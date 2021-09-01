package modelling

import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.{ResolutionLevel, Stage}
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import scalismo.common.interpolation.BSplineImageInterpolator3D
import scalismo.mesh.ScalarVolumeMeshField
import scalismo.numerics.PivotedCholesky
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess
import scalismo.statisticalmodel.dataset.DataCollection

import scala.util.{Failure, Success}

object BuildIntensityModel extends StrictLogging {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    val resolutionLevel = ResolutionLevel.Coarse
    implicit val rng = scalismo.utils.Random(42)

    val dataRepository = DirectoryBasedDataRepository.of(VertebraL1)

    val referenceMesh = dataRepository.referenceTetrahedralMesh(resolutionLevel).get

    val (successes, failures) = dataRepository.caseIds
      .map(caseId =>
        dataRepository
          .tetrahedralMesh(Stage.Registered(resolutionLevel), caseId)
          .map(mesh => (mesh, caseId))
      )
      .partition(_.isSuccess)

    failures.map(failure => failure.fold(fa => logger.error(fa.getMessage), _ => ()))

    val meshesWithId = successes.map(_.get)

    val scalarVolumeMeshFields = for ((tetraMesh, caseId) <- meshesWithId) yield {
      logger.info(s"processing mesh $caseId")
      val volumeCT =
        dataRepository.volume(Stage.Aligned, caseId).get.interpolate(BSplineImageInterpolator3D(degree = 3))
      val scalars = tetraMesh.pointSet.points.map(volumeCT).map(_.toFloat)
      ScalarVolumeMeshField(referenceMesh, scalars.toIndexedSeq)
    }
    val dataCollection = DataCollection.fromScalarVolumeMesh3DSequence(scalarVolumeMeshFields)
    val intensityModel =
      DiscreteLowRankGaussianProcess.createUsingPCA(dataCollection, PivotedCholesky.RelativeTolerance(1e-10))
    logger.info("successfully built intensity model")

    dataRepository.saveIntensityModel(intensityModel, resolutionLevel) match {
      case Success(_)         => logger.info("successfully saved the intensity model")
      case Failure(exception) => logger.error("could not save intensity model " + exception.getMessage)
    }

  }
}
