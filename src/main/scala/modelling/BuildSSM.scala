package modelling

import com.typesafe.scalalogging.StrictLogging
import data.DataProvider
import data.DataProvider.Stage
import data.DataProvider.Vertebra.VertebraL1
import scalismo.io.StatisticalModelIO
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.statisticalmodel.dataset.DataCollection
import scalismo.ui.api.ScalismoUI

import scala.util.{Failure, Success}

object BuildSSM extends StrictLogging {

  def main(args : Array[String]) : Unit = {

    scalismo.initialize()

    implicit val rng = scalismo.utils.Random(42)

    val dataProvider = DataProvider.of(VertebraL1)

    dataProvider.ssmDir.mkdirs()

    val refMesh = dataProvider.referenceMesh.get
    val (successes, failures) = dataProvider.caseIds
      .map(caseId => dataProvider.triangleMesh(Stage.Registered, caseId))
      .partition(_.isSuccess)

    failures.map(failure =>
      failure.fold(fa => logger.error(fa.getMessage), _ => ())
    )

    val meshes = successes.map(_.get)
    val dataCollection = DataCollection.fromTriangleMesh3DSequence(refMesh, meshes)
    val gpaAlignedDataCollection = DataCollection.gpa(dataCollection)

    val ssm = PointDistributionModel.createUsingPCA(gpaAlignedDataCollection)

    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(ssm, dataProvider.ssmFile).get

    val ui = ScalismoUI()
    ui.show(ssm, "ssm")
  }
}
