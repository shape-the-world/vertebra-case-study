package pipeline

import com.typesafe.scalalogging.StrictLogging
import data.DataProvider.{CaseId, Stage, Vertebra}
import data.DataProvider
import data.DataProvider.Vertebra.VertebraL1
import scalismo.geometry._3D
import scalismo.image.DiscreteImage
import scalismo.io.MeshIO
import scalismo.mesh.TriangleMesh
import scalismo.ui.api.ScalismoUIHeadless
import scalismo.utils.{ImageConversion, MeshConversion}
import vtk.{vtkMarchingCubes, vtkQuadricDecimation, vtkWindowedSincPolyDataFilter}

import scala.util.{Failure, Success, Try}

object ExtractMeshFromLabelmap extends StrictLogging {

  def extractTriangleMesh(labelMap: DiscreteImage[_3D, Short], vertebra: Vertebra): Try[TriangleMesh[_3D]] = {
    val labelMapWithTargetStructureOnly = labelMap.map(v => if (v == vertebra.label) 1 else 0)
    val vtkImage = ImageConversion.imageToVtkStructuredPoints(labelMapWithTargetStructureOnly,
                                                              ImageConversion.VtkNearestNeighborInterpolation)
    val extractor = new vtkMarchingCubes()
    extractor.SetValue(0, 0.5)

    extractor.SetInputData(vtkImage)
    extractor.Update()

    val smoother = new vtkWindowedSincPolyDataFilter()
    smoother.SetInputConnection(extractor.GetOutputPort())
    smoother.SetPassBand(0.1)
    smoother.SetNumberOfIterations(20)

    val dec = new vtkQuadricDecimation()
    dec.SetTargetReduction(0.5)
    dec.SetInputConnection(smoother.GetOutputPort())
    dec.Update()

    MeshConversion.vtkPolyDataToTriangleMesh(dec.GetOutput())
  }

  def processCase(dataProvider: DataProvider, caseId: CaseId, vertebra: Vertebra): Try[Unit] = {
    Try {
      val labelmap = dataProvider.labelMap(Stage.Initial, caseId).get
      val mesh = ExtractMeshFromLabelmap.extractTriangleMesh(labelmap, vertebra).get

      val outputFile = dataProvider.triangleMeshFile(Stage.Initial, caseId)
      outputFile.getParentFile.mkdirs()
      MeshIO.writeMesh(mesh, outputFile).get
    }
  }

  def main(args: Array[String]): Unit = {

    logger.info("Starting ExtractFomImage")

    scalismo.initialize()

    val vertebra = VertebraL1
    val dataProvider = DataProvider.of(vertebra)

    for (caseId <- dataProvider.caseIds) {
      logger.info(s"processing case $caseId")

      processCase(dataProvider, caseId, vertebra) match {
        case Success(_)         => logger.info(s"successfully processed $caseId")
        case Failure(exception) => logger.error(s"an error occurred while processing $caseId: " + exception.getMessage)
      }
    }
  }
}
