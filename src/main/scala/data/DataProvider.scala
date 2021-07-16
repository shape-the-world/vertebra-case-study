package data

import data.DataProvider.Vertebra.VertebraL1
import data.DataProvider.{CaseId, Stage, Vertebra, readZippedImage}
import scalismo.geometry.{Landmark, _3D}
import scalismo.image.DiscreteImage
import scalismo.io.{ImageIO, LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.PointDistributionModel

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream
import scala.util.Try


class DataProvider(vertebra: Vertebra) {

  def caseIds: Seq[CaseId] =
    Seq(CaseId("004"),
        CaseId("005"),
        CaseId("008"),
        CaseId("031"),
        CaseId("033"),
        CaseId("034"),
        CaseId("056"),
        CaseId("075"))

  def baseDir: File = new java.io.File(s"D:\\data\\vertebrae\\${vertebra.desc}")

  def referenceDir: File = new java.io.File(baseDir, "reference")
  def referenceMeshFile: File = vertebra match {
    case VertebraL1 => new java.io.File(referenceDir, s"031.ply")
  }
  def referenceMesh: Try[TriangleMesh[_3D]] = MeshIO.readMesh(referenceMeshFile)
  def referenceLandmarksFile: File = new java.io.File(referenceDir, s"031.json")
  def referenceLandmarks: Try[Seq[Landmark[_3D]]] = LandmarkIO.readLandmarksJson3D(referenceLandmarksFile)

  def referenceVolumeFile: File = new java.io.File(referenceDir, s"031.nii.gz")
  def referenceVolume: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)
  def referenceLabelmapFile: File = new java.io.File(referenceDir, s"031_seg.nii.gz")
  def referenceLabelMap: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)

  def stageDir(stage: Stage): File = new java.io.File(baseDir, stage.dirname)

  def triangleMeshDir(stage: Stage): File = new java.io.File(stageDir(stage), "triangle-meshes")

  def triangleMeshFile(stage: Stage, caseId: CaseId): File = new File(triangleMeshDir(stage), s"${caseId.value}.ply")

  def triangleMesh(stage: Stage, id: CaseId): Try[TriangleMesh[_3D]] = {
    val file = triangleMeshFile(stage, id)
    MeshIO.readMesh(file)
  }

  def landmarkDir(stage: Stage): File = new File(stageDir(stage), "landmarks")

  def landmarkFile(stage: Stage, caseId: CaseId): File = new File(landmarkDir(stage), s"${caseId.value}.json")

  def landmarks(stage: Stage, id: CaseId): Try[Seq[Landmark[_3D]]] = {
    val file = landmarkFile(stage, id)
    LandmarkIO.readLandmarksJson3D(file)
  }

  def volumesDir(stage: Stage): File = new java.io.File(stageDir(stage), "volumes")

  def labelMapFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}_seg.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii")
  }

  def labelMap(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (labelMapFile(stage, id).getPath.endsWith(".gz")) {
      readZippedImage(labelMapFile(stage, id))
    } else {
      ImageIO.read3DScalarImage[Short](labelMapFile(stage, id))
    }

  }

  def volumeFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}.nii")
  }

  def volume(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (volumeFile(stage, id).getPath.endsWith(".gz"))
      readZippedImage(volumeFile(stage, id))
    else
      ImageIO.read3DScalarImage[Short](volumeFile(stage, id))
  }


  def gpModelDir: File = new java.io.File(referenceDir, "model")
  def gpModelFile: File = new java.io.File(gpModelDir, "gpmodel.h5")
  def gpModel: Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(gpModelFile)
  }

  def ssmDir : File = new java.io.File(stageDir(Stage.Registered), "model")
  def ssmFile : File = new java.io.File(ssmDir, "ssm.h5")
  def ssm : Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(ssmFile)
  }

}

object DataProvider {

  sealed trait Vertebra {
    def desc: String
    def label: Int
  }

  object Vertebra {
    case object VertebraL1 extends Vertebra {
      override val desc = "L1"
      override val label: Int = 23
    }
  }

  sealed trait Stage {
    def dirname: String
  }
  object Stage {
    case object Initial extends Stage {
      override val dirname = "initial"
    }

    case object Aligned extends Stage {
      override val dirname = "aligned"
    }

    case object Registered extends Stage {
      override val dirname = "registered"
    }
  }


  case class CaseId(value: String)
  case class LabelMapWithId(labelMap: DiscreteImage[_3D, Short], id: CaseId)
  case class VolumeWithId(volume: DiscreteImage[_3D, Short], id: CaseId)

  def of(vertebra: Vertebra): DataProvider = new DataProvider(vertebra)

  def readZippedImage(gzippedImageFile: java.io.File): Try[DiscreteImage[_3D, Short]] = {
    val gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(gzippedImageFile)))
    val tmpFile = java.io.File.createTempFile("lbl", ".nii")
    tmpFile.deleteOnExit()
    val os = new FileOutputStream(tmpFile)
    os.write(gis.readAllBytes())
    os.close()
    ImageIO.read3DScalarImageAsType[Short](tmpFile)
  }

  def main(args: Array[String]): Unit = {}
}
