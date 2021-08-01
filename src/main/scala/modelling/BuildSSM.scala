package modelling

import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.Stage
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.ui.api.ScalismoUI

import scala.util.{Failure, Success}

/**
 * Builds a statistical shape model.
 */
object BuildSSM extends StrictLogging {

  def main(args: Array[String]): Unit = {

    scalismo.initialize()

    implicit val rng = scalismo.utils.Random(42)

    val dataRepository = DirectoryBasedDataRepository.of(VertebraL1)

    val refMesh = dataRepository.referenceTriangleMesh.get
    val (successes, failures) = dataRepository.caseIds
      .map(caseId => dataRepository.triangleMesh(Stage.Registered, caseId))
      .partition(_.isSuccess)

    failures.map(failure => failure.fold(fa => logger.error(fa.getMessage), _ => ()))

    val meshes = successes.map(_.get)
    val dataCollection = DataCollection.fromTriangleMesh3DSequence(refMesh, meshes)
    val gpaAlignedDataCollection = DataCollection.gpa(dataCollection)

    val ssm = PointDistributionModel.createUsingPCA(gpaAlignedDataCollection)

    dataRepository.saveSSM(ssm) match {
      case Success(_) => logger.info("successfully saved ssm")
      case Failure(exception) => logger.info("failed to save flie " + exception.getMessage)
    }

    val ui = ScalismoUI()
    ui.show(ssm, "ssm")
  }
}
