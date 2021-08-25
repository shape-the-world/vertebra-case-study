package fitting.intensity

import breeze.linalg.DenseVector
import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import rendering.{BarycentricInterpolatorRestrictedToVolume3D, DRRRenderer, XRay}
import scalismo.common.interpolation.{BarycentricInterpolator3D, TriangleMeshInterpolator3D}
import scalismo.geometry.{_3D, EuclideanVector3D, IntVector, Point3D}
import scalismo.io.StatisticalModelIO
import scalismo.mesh.{ScalarVolumeMeshField, TetrahedralMesh}
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess
import scalismo.ui.api.{ObjectView, ScalismoUI}

import java.awt.Color
import java.io.File

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    implicit val rng = scalismo.utils.Random(42)
//    val ui = ScalismoUI()

    val repo = DirectoryBasedDataRepository.of(VertebraL1)
    val intensityModelOrigPos = repo.intensityModel(ResolutionLevel.Coarse).get

    // We need to transform the model into the scanner
    // TODO Later, this will need to be done using the provided fit from the contour fitting
    val outerSurface = intensityModelOrigPos.domain.operations.getOuterSurface

    val centerOfMass = (outerSurface.pointSet.points.foldLeft(EuclideanVector3D(0, 0, 0))((acc, point) =>
      acc + point.toVector
    ) * (1.0 / outerSurface.pointSet.numberOfPoints)).toPoint

    val domainInScanner = intensityModelOrigPos.domain.transform(pt => pt + (Point3D(0, 0, 0) - centerOfMass))
    val intensityModel = DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float](domainInScanner,
                                                                                     intensityModelOrigPos.meanVector,
                                                                                     intensityModelOrigPos.variance,
                                                                                     intensityModelOrigPos.basisMatrix)

    val drrRenderer = new DRRRenderer(150, 200, 200, IntVector(32, 32), 50)

    // Generate ground truth - used for first synthetic experiments
    val groundTruthCoefficients = DenseVector.zeros[Double](intensityModel.rank)
    groundTruthCoefficients(0) = 3
    val groundTruthVolumeMesh = intensityModel
      .instance(groundTruthCoefficients)
      .interpolate(new BarycentricInterpolatorRestrictedToVolume3D[Float]())

    val targetImageYZ = drrRenderer.projectYZ(groundTruthVolumeMesh, 0)
    val targetImageXZ = drrRenderer.projectXZ(groundTruthVolumeMesh, 0)

    val initialFittingParameters =
      IntensityFittingParameters(DenseVector.zeros[Double](intensityModel.rank))
    initialFittingParameters.intensityModelCoefficients(0) = 2.5
    def fittingStatusCallback(sample: IntensitySample, iterationNumber: Int): Unit = {

      if (iterationNumber % 1 == 0) {
        logger.info(s"in iteration $iterationNumber")
        logger.info(s"coefficients: ${sample.fittingParameters.intensityModelCoefficients}")
      }
    }

    val bestFit = IntensityFitting.fit(intensityModel,
                                       initialFittingParameters,
                                       targetImageXZ,
                                       targetImageYZ,
                                       drrRenderer,
                                       fittingStatusCallback)

  }

}
