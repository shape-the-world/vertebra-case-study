package data

import data.DataProvider.Vertebra.VertebraL1
import data.DataProvider.{readZippedImage, CaseId, Stage, Vertebra}
import scalismo.geometry.{_3D, Landmark}
import scalismo.image.DiscreteImage
import scalismo.io.{ImageIO, LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.statisticalmodel.PointDistributionModel

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream
import scala.util.Try

/**
 * This class is the primary interface to interact with the data. The basic principle is that
 * no code should ever construct a file path or read a file directly, but always go via the
 * DataProvider. This makes the pipeline code easy to change and at the same contains all the
 * information about data organization in one place.
 *
 * @param vertebra  Specify the verteba, whose data the data provider manages.
 */
class DataProvider(vertebra: Vertebra) {

  /** Each data item is identified by a caseId. This sequence holds all CaseIds that are
   * managed by this Data provider.
   */
  def caseIds: Seq[CaseId] =
    Seq(CaseId("004"),
        CaseId("005"),
        CaseId("008"),
        CaseId("031"),
        CaseId("033"),
        CaseId("034"),
        CaseId("056"),
        CaseId("075"))

  /**
   * The base directory, under which all the data is stored
   */
  def baseDir: File = new java.io.File(s"D:\\data\\vertebrae\\${vertebra.desc}")

  /**
   * Specifies the directory for a given stage.
   */
  def stageDir(stage: Stage): File = new java.io.File(baseDir, stage.dirname)

  /**
   * Directory, where all the reference data is stored
   */
  def referenceDir: File = new java.io.File(baseDir, "reference")

  def referenceTetrahedralMeshFile: File = vertebra match {
    case VertebraL1 => new java.io.File(referenceDir, s"031.vtu")
  }

  /** The reference tetrahedral mesh is the mesh on which we base all the modelling. */
  def referenceTetrahedralMesh: Try[TetrahedralMesh[_3D]] = MeshIO.readTetrahedralMesh(referenceTetrahedralMeshFile)

  // We obtain the reference triangle mesh from the reference tetrahedral mesh.
  def referenceTriangleMesh: Try[TriangleMesh[_3D]] = referenceTetrahedralMesh.map(_.operations.getOuterSurface)

  def referenceLandmarksFile: File = new java.io.File(referenceDir, s"031.json")

  def referenceLandmarks: Try[Seq[Landmark[_3D]]] = LandmarkIO.readLandmarksJson3D(referenceLandmarksFile)

  def referenceVolumeFile: File = new java.io.File(referenceDir, s"031.nii.gz")
  def referenceVolume: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)
  def referenceLabelmapFile: File = new java.io.File(referenceDir, s"031_seg.nii.gz")
  def referenceLabelMap: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)

  def triangleMeshDir(stage: Stage): File = new java.io.File(stageDir(stage), "triangle-meshes")

  def triangleMeshFile(stage: Stage, caseId: CaseId): File = new File(triangleMeshDir(stage), s"${caseId.value}.ply")

  def triangleMesh(stage: Stage, id: CaseId): Try[TriangleMesh[_3D]] = {
    val file = triangleMeshFile(stage, id)
    MeshIO.readMesh(file)
  }

  def tetrahedralMeshDir(stage: Stage): File = new java.io.File(stageDir(stage), "tetrahedral-meshes")

  def tetrahedralMeshFile(stage: Stage, caseId: CaseId): File =
    new File(tetrahedralMeshDir(stage), s"${caseId.value}.vtu")

  def tetrahedralMesh(stage: Stage, id: CaseId): Try[TetrahedralMesh[_3D]] = {
    val file = tetrahedralMeshFile(stage, id)
    MeshIO.readTetrahedralMesh(file)
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
  def gpModelTriangleMeshFile: File = new java.io.File(gpModelDir, "gpmodel-trianglemesh.h5")
  def gpModelTriangleMesh: Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(gpModelTriangleMeshFile)
  }

  def gpModelTetrahedralMeshFile: File = new java.io.File(gpModelDir, "gpmodel-tetrahedralmesh.h5")
  def gpModelTetrahedralMesh: Try[PointDistributionModel[_3D, TetrahedralMesh]] = {
    StatisticalModelIO.readStatisticalTetrahedralMeshModel3D(gpModelTetrahedralMeshFile)
  }

  def ssmDir: File = new java.io.File(stageDir(Stage.Registered), "model")
  def ssmFile: File = new java.io.File(ssmDir, "ssm.h5")
  def ssm: Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(ssmFile)
  }

}

object DataProvider {

  /**
   * This trait is used to specify the individual vertebrae
   */
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

  /**
   * An object is usually represented in different stages during the
   * processing. It might start in its initial stage, is then aligned and finally registered.
   * This trait, together with the accompanying case objects are used to represent this Stage.
   */
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

  /** Specifies an individual case   */
  case class CaseId(value: String)

  /** Factory method used to create a new data provider */
  def of(vertebra: Vertebra): DataProvider = new DataProvider(vertebra)

  /**
   * Helper method used to read a gzipped nii image.
   */
  private def readZippedImage(gzippedImageFile: java.io.File): Try[DiscreteImage[_3D, Short]] = {
    val gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(gzippedImageFile)))
    val tmpFile = java.io.File.createTempFile("lbl", ".nii")
    tmpFile.deleteOnExit()
    val os = new FileOutputStream(tmpFile)
    os.write(gis.readAllBytes())
    os.close()
    ImageIO.read3DScalarImageAsType[Short](tmpFile)
  }

}
