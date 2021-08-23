package data

import data.DataRepository.Stage.{Aligned, Initial, Registered}
import data.DataRepository.Vertebra.VertebraL1
import data.DataRepository.{CaseId, Stage, Vertebra}
import data.DirectoryBasedDataRepository.readZippedImage
import scalismo.geometry.{Landmark, _3D}
import scalismo.image.DiscreteImage
import scalismo.io.{ImageIO, LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.statisticalmodel.{DiscreteLowRankGaussianProcess, PointDistributionModel}

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream
import scala.util.Try

class DirectoryBasedDataRepository(val vertebra : Vertebra) extends DataRepository {

  override def caseIds: Seq[CaseId] =
    Seq(CaseId("verse004"),
      CaseId("verse005"),
      CaseId("verse008"),
      CaseId("verse031"),
      CaseId("verse033"),
      CaseId("verse034"),
      CaseId("verse056"))

  /**
   * The base directory, under which all the data is stored
   */
  def baseDir: File = new java.io.File(s"C:\\Users\\luetma00\\data\\vertebrae\\${vertebra.desc}")

  /**
   * Specifies the directory for a given stage.
   */
  def stageDir(stage: Stage): File = new java.io.File(baseDir, stage.dirname)

  /**
   * Directory, where all the reference data is stored
   */
  def referenceDir: File = new java.io.File(baseDir, "reference")

  def referenceTetrahedralMeshFile: File = vertebra match {
    case VertebraL1 => new java.io.File(referenceDir, s"verse005-1600-nodes.vtu")
  }

  /** The reference tetrahedral mesh is the mesh on which we base all the modelling. */
  override def referenceTetrahedralMesh: Try[TetrahedralMesh[_3D]] = MeshIO.readTetrahedralMesh(referenceTetrahedralMeshFile)

  def referenceLandmarksFile: File = new java.io.File(referenceDir, s"031.json")

  override def referenceLandmarks: Try[Seq[Landmark[_3D]]] = LandmarkIO.readLandmarksJson3D(referenceLandmarksFile)

  def referenceVolumeFile: File = new java.io.File(referenceDir, s"verse005.nii.gz")
  override def referenceVolume: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)

  def referenceLabelmapFile: File = new java.io.File(referenceDir, s"031_seg.nii.gz")
  override def referenceLabelMap: Try[DiscreteImage[_3D, Short]] = readZippedImage(referenceVolumeFile)

  def triangleMeshDir(stage: Stage): File = new java.io.File(stageDir(stage), "triangle-meshes")

  def triangleMeshFile(stage: Stage, caseId: CaseId): File = new File(triangleMeshDir(stage), s"${caseId.value}.ply")

  override def triangleMesh(stage: Stage, id: CaseId): Try[TriangleMesh[_3D]] = {
    val file = triangleMeshFile(stage, id)
    MeshIO.readMesh(file)
  }

  override def saveTriangleMesh(stage: Stage, id: CaseId, mesh: TriangleMesh[_3D]): Try[Unit] = {
    MeshIO.writeMesh(mesh, triangleMeshFile(stage, id))
  }

  def tetrahedralMeshDir(stage: Stage): File = new java.io.File(stageDir(stage), "tetrahedral-meshes")

  def tetrahedralMeshFile(stage: Stage, caseId: CaseId): File =
    new File(tetrahedralMeshDir(stage), s"${caseId.value}.vtu")

  override def tetrahedralMesh(stage: Stage, id: CaseId): Try[TetrahedralMesh[_3D]] = {
    val file = tetrahedralMeshFile(stage, id)
    MeshIO.readTetrahedralMesh(file)
  }

  override def saveTetrahedralMesh(stage: Stage, id: CaseId, mesh: TetrahedralMesh[_3D]): Try[Unit] = {
    MeshIO.writeTetrahedralMesh(mesh, tetrahedralMeshFile(stage, id))
  }

  def landmarkDir(stage: Stage): File = new File(stageDir(stage), "landmarks")

  def landmarkFile(stage: Stage, caseId: CaseId): File = new File(landmarkDir(stage), s"${caseId.value}.json")

  override def landmarks(stage: Stage, id: CaseId): Try[Seq[Landmark[_3D]]] = {
    val file = landmarkFile(stage, id)
    LandmarkIO.readLandmarksJson3D(file)
  }

  override def saveLandmarks(stage: Stage, id: CaseId, landmarks : Seq[Landmark[_3D]]): Try[Unit] = {
    LandmarkIO.writeLandmarksJson[_3D](landmarks, landmarkFile(stage, id))
  }

  def volumesDir(stage: Stage): File = new java.io.File(stageDir(stage), "volumes")

  override def saveVolume(stage: Stage, id: CaseId, volume: DiscreteImage[_3D, Short]): Try[Unit] = {
    ImageIO.writeNifti(volume, volumeFile(stage, id))
  }

  def labelMapFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}_seg.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii")
  }

  override def labelMap(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (labelMapFile(stage, id).getPath.endsWith(".gz")) {
      readZippedImage(labelMapFile(stage, id))
    } else {
      ImageIO.read3DScalarImage[Short](labelMapFile(stage, id))
    }

  }

  override def saveLabelMap(stage: Stage, id: CaseId, labelMap: DiscreteImage[_3D, Short]): Try[Unit] = {
    ImageIO.writeNifti(labelMap, labelMapFile(stage, id))
  }

  def volumeFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}.nii")
  }

  override def volume(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (volumeFile(stage, id).getPath.endsWith(".gz"))
      readZippedImage(volumeFile(stage, id))
    else
      ImageIO.read3DScalarImage[Short](volumeFile(stage, id))
  }

  def gpModelDir: File = new java.io.File(referenceDir, "model")
  def gpModelTriangleMeshFile: File = new java.io.File(gpModelDir, "gpmodel-trianglemesh.h5")
  override def gpModelTriangleMesh: Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(gpModelTriangleMeshFile)
  }

  def gpModelTetrahedralMeshFile: File = new java.io.File(gpModelDir, "gpmodel-tetrahedralmesh.h5")
  override def gpModelTetrahedralMesh: Try[PointDistributionModel[_3D, TetrahedralMesh]] = {
    StatisticalModelIO.readStatisticalTetrahedralMeshModel3D(gpModelTetrahedralMeshFile)
  }

  def ssmDir: File = new java.io.File(stageDir(Stage.Registered), "model")
  def ssmFile: File = new java.io.File(ssmDir, "ssm.h5")
  override def ssm: Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(ssmFile)
  }

  override def saveGpModelTriangleMesh(gpModel: PointDistributionModel[_3D, TriangleMesh]): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(gpModel, gpModelTriangleMeshFile)
  }

  override def saveGpModelTetrahedralMesh(gpModel: PointDistributionModel[_3D, TetrahedralMesh]): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTetrahedralMeshModel3D(gpModel, gpModelTetrahedralMeshFile)
  }

  override def saveSSM(ssm: PointDistributionModel[_3D, TriangleMesh]): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(ssm, ssmFile)
  }

  def intensityModelDir : File = new java.io.File(stageDir(Stage.Registered), "model")
  def intensityModelFile = new java.io.File(intensityModelDir, "intensity-model.h5")
  def intensityModel : Try[DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]] = {
    StatisticalModelIO.readVolumeMeshIntensityModel3D(intensityModelFile)
  }

  override def saveIntensityModel(intensityModel: DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]): Try[Unit] = {
    StatisticalModelIO.writeVolumeMeshIntensityModel3D(intensityModel, intensityModelFile)
  }

}

object DirectoryBasedDataRepository {
  /** Factory method used to create a new data provider */
  def of(vertebra: Vertebra): DataRepository = new DirectoryBasedDataRepository(vertebra)

  def mkdirs(vertebra : Vertebra) : Unit = {
    val dataRepository = new DirectoryBasedDataRepository(vertebra)
    dataRepository.referenceDir.mkdirs()
    dataRepository.gpModelDir.mkdirs()
    dataRepository.ssmDir.mkdirs()
    dataRepository.intensityModelDir.mkdirs()

    for (stage <- Seq(Initial, Aligned, Registered)) {
      dataRepository.triangleMeshDir(stage).mkdirs()
      dataRepository.tetrahedralMeshDir(stage).mkdirs()
      dataRepository.landmarkDir(stage).mkdirs()
      dataRepository.volumesDir(stage).mkdirs()
    }
  }

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