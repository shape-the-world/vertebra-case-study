package pipeline

import breeze.linalg.DenseVector
import com.typesafe.scalalogging.StrictLogging
import data.DataProvider
import data.DataProvider.Stage
import data.DataProvider.Vertebra.VertebraL1
import scalismo.common.interpolation.{BarycentricInterpolator3D, TriangleMeshInterpolator3D}
import scalismo.geometry.{EuclideanVector, Landmark, _3D}
import scalismo.io.MeshIO
import scalismo.mesh.{MeshOperations, TetrahedralMesh, TriangleMesh, TriangleMesh3D}
import scalismo.numerics.{FixedPointsUniformMeshSampler3D, LBFGSOptimizer}
import scalismo.registration.{GaussianProcessTransformationSpace, L2Regularizer, MeanSquaresMetric, Registration}
import scalismo.statisticalmodel.{LowRankGaussianProcess, PointDistributionModel}
import scalismo.ui.api.{Group, ObjectView, ScalismoUI, ShapeModelTransformationView}
import scalismo.utils.Random

import scala.util.{Failure, Success, Try}

/**
 * Pipeline step, which establishes correspondence between the reference and
 * the aligned meshes. It requires that a Gaussian process model, defined on a tetrahedral
 * reference is available.
 * While the model that is used for performing the registration is a tetrahedral model,
 * the registration is only performed using its outer surface.
 */
object NonrigidRegistration extends StrictLogging {

  case class RegistrationParameters(regularizationWeight: Double, numberOfIterations: Int, numberOfSampledPoints: Int)

  val ui: ScalismoUI = ScalismoUI()
  private val modelGroup = ui.createGroup("modelGroup")
  val targetGroup: Group = ui.createGroup("target")

  def doRegistration(lowRankGP: LowRankGaussianProcess[_3D, EuclideanVector[_3D]],
                     referenceMesh: TriangleMesh[_3D],
                     targetmesh: TriangleMesh[_3D],
                     registrationParameters: RegistrationParameters,
                     initialCoefficients: DenseVector[Double],
                     gpView: ShapeModelTransformationView)(implicit rng: scalismo.utils.Random): DenseVector[Double] = {

    val transformationSpace = GaussianProcessTransformationSpace(lowRankGP)
    val fixedImage = MeshOperations(TriangleMesh3D(referenceMesh.pointSet, referenceMesh.triangulation)).toDistanceImage
    val movingImage = MeshOperations(TriangleMesh3D(targetmesh.pointSet, targetmesh.triangulation)).toDistanceImage

    val sampler = FixedPointsUniformMeshSampler3D(
      referenceMesh,
      registrationParameters.numberOfSampledPoints
    )

    val metric = MeanSquaresMetric(
      fixedImage,
      movingImage,
      transformationSpace,
      sampler
    )

    val optimizer = LBFGSOptimizer(registrationParameters.numberOfIterations)
    val regularizer = L2Regularizer(transformationSpace)
    val registration = Registration(
      metric,
      regularizer,
      registrationParameters.regularizationWeight,
      optimizer
    )
    val registrationIterator = registration.iterator(initialCoefficients)
    val visualizingRegistrationIterator = for ((it, itnum) <- registrationIterator.zipWithIndex) yield {
      logger.info(s"object value in iteration $itnum is ${it.value}")
      gpView.shapeTransformationView.coefficients = it.parameters
      it
    }

    val registrationResult = visualizingRegistrationIterator.toSeq.last
    registrationResult.parameters

  }

  def registerCase(pdmGp: PointDistributionModel[_3D, TetrahedralMesh],
                   referenceLandmarks: Seq[Landmark[_3D]],
                   dataProvider: DataProvider,
                   caseId: DataProvider.CaseId)(implicit rng: scalismo.utils.Random): Try[Unit] = {

    val successOfFailure = Try {

      val targetMesh = dataProvider.triangleMesh(Stage.Aligned, caseId).get
      val targetView = ui.show(targetGroup, targetMesh, s"$caseId")
      val targetLandmarks = dataProvider.landmarks(Stage.Aligned, caseId).get

      val refIds = referenceLandmarks.map(lm => pdmGp.reference.pointSet.findClosestPoint(lm.point).id)
      val posteriorPDM = pdmGp.posterior(refIds.zip(targetLandmarks.map(_.point)).toIndexedSeq, sigma2 = 25.0)

      // for the registration we restrict the pdm to the outer surface
      val pdmGPOuterSurface = posteriorPDM.newReference(pdmGp.reference.operations.getOuterSurface, BarycentricInterpolator3D())

      val pdmView = ui.show(modelGroup, pdmGPOuterSurface, "gp")
      val gpView: ShapeModelTransformationView = pdmView.shapeModelTransformationView


      targetView.color = java.awt.Color.RED
      val registrationParameters = Seq(
        RegistrationParameters(regularizationWeight = 1e-1, numberOfIterations = 20, numberOfSampledPoints = 300),
        RegistrationParameters(regularizationWeight = 5e-2, numberOfIterations = 20, numberOfSampledPoints = 500),
        RegistrationParameters(regularizationWeight = 1e-2, numberOfIterations = 20, numberOfSampledPoints = 1000),
        RegistrationParameters(regularizationWeight = 1e-3, numberOfIterations = 20, numberOfSampledPoints = 2000)
      )

      val initialCoefficients = DenseVector.zeros[Double](pdmGp.rank)

      val finalCoefficients = registrationParameters.foldLeft(initialCoefficients)((modelCoefficients, regParameters) =>
        doRegistration(pdmGPOuterSurface.gp.interpolate(TriangleMeshInterpolator3D()),
          pdmGPOuterSurface.reference,
                       targetMesh,
                       regParameters,
                       modelCoefficients,
                       gpView)
      )

      val registeredTriangleMesh = pdmGPOuterSurface.instance(finalCoefficients)
      val registeredTetrahedralMesh = posteriorPDM.instance(finalCoefficients)

      val outputFileTriangleMesh = dataProvider.triangleMeshFile(Stage.Registered, caseId)
      MeshIO.writeMesh(registeredTriangleMesh, outputFileTriangleMesh).get

      val outputFileTetrahedralMesh = dataProvider.tetrahedralMeshFile(Stage.Registered, caseId)
      MeshIO.writeTetrahedralMesh(registeredTetrahedralMesh, outputFileTetrahedralMesh).get

    }


    // clean up the views
    ui.filter(targetGroup, (_ : ObjectView) => true).foreach(view => Try{view.remove()})
    ui.filter(modelGroup, (_ : ObjectView) => true).foreach(view => Try{view.remove()})


    successOfFailure
  }

  def main(args: Array[String]): Unit = {

    val dataProvider = DataProvider.of(VertebraL1)

    scalismo.initialize()
    implicit val rng: Random = scalismo.utils.Random(42)

    val pdmGp = dataProvider.gpModelTetrahedralMesh.get
    val referenceLandmarks = dataProvider.referenceLandmarks.get

    dataProvider.triangleMeshDir(Stage.Registered).mkdirs()

    for (caseId <- dataProvider.caseIds) {
      logger.info(s"registration for case $caseId")
      registerCase(pdmGp, referenceLandmarks,dataProvider, caseId)
    } match {
      case Success(value) =>
        logger.info(s"successfully registered case $caseId")
      case Failure(exception) =>
        logger.error(s"error registereing $caseId " + exception.getMessage)
    }
  }
}
