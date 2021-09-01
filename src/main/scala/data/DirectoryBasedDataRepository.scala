package data

import data.DataRepository.Stage.{Aligned, Initial, Registered}
import data.DataRepository.Vertebra.VertebraL1
import data.DataRepository.{CaseId, ResolutionLevel, Stage, Vertebra}
import data.DirectoryBasedDataRepository.readZippedImage
import scalismo.common.Scalar
import scalismo.geometry.{_3D, Landmark}
import scalismo.image.DiscreteImage
import scalismo.io.{ImageIO, LandmarkIO, MeshIO, StatisticalModelIO}
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.statisticalmodel.{DiscreteLowRankGaussianProcess, PointDistributionModel}

import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream
import scala.util.Try

class DirectoryBasedDataRepository(val vertebra: Vertebra) extends DataRepository {

  override def caseIds: Seq[CaseId] =
    Seq(
      CaseId("verse005"),
      CaseId("verse008"),
      CaseId("verse033"),
      CaseId("verse088"),
      CaseId("verse096"),
      CaseId("verse097"),
      CaseId("verse104_CT-iso"),
      CaseId("verse127"),
      CaseId("verse254"),
      CaseId("verse405_verse259_CT-sag"),
      CaseId("verse406_verse261_CT-sag"),
      CaseId("verse407_verse262_CT-sag"),
      CaseId("verse415_verse275_CT-sag"),
      //CaseId("verse506_CT-iso"),
      CaseId("verse521"),
      //CaseId("verse532"),
      CaseId("verse537"),
      //CaseId("verse541"),
      //CaseId("verse557"),
      //CaseId("verse561_CT-sag"),
      CaseId("verse564_CT-iso"),
      CaseId("verse565_CT-iso")
      //CaseId("verse584"),
      //CaseId("verse586_CT-iso")
    )

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

  def referenceTetrahedralMeshFile(level: ResolutionLevel): File = vertebra match {
    case VertebraL1 =>
      level match {
        case ResolutionLevel.Coarse => new java.io.File(referenceDir, s"verse005-coarse-nodes.vtu")
        case ResolutionLevel.Medium => new java.io.File(referenceDir, s"verse005-medium-nodes.vtu")
        case ResolutionLevel.Fine   => new java.io.File(referenceDir, s"verse005-fine-nodes.vtu")
      }
  }

  /** The reference tetrahedral mesh is the mesh on which we base all the modelling. */
  override def referenceTetrahedralMesh(level: ResolutionLevel): Try[TetrahedralMesh[_3D]] =
    MeshIO.readTetrahedralMesh(referenceTetrahedralMeshFile(level))

  def referenceLandmarksFile: File = new java.io.File(referenceDir, s"031.json")

  override def referenceLandmarks: Try[Seq[Landmark[_3D]]] = LandmarkIO.readLandmarksJson3D(referenceLandmarksFile)

  def referenceVolumeFile: File = new java.io.File(referenceDir, s"verse005.nii.gz")
  override def referenceVolume: Try[DiscreteImage[_3D, Short]] =
    readZippedImage(referenceVolumeFile, ImageIO.read3DScalarImage[Short])

  def referenceLabelmapFile: File = new java.io.File(referenceDir, s"031_seg.nii.gz")
  override def referenceLabelMap: Try[DiscreteImage[_3D, Short]] =
    readZippedImage(referenceVolumeFile, ImageIO.read3DScalarImage[Short])

  def referenceTrabecularAreaFile: File = new java.io.File(referenceDir, s"verse005TrabecularArea.vtu")
  override def referenceTrabecularVolume: Try[TetrahedralMesh[_3D]] = {
    MeshIO.readTetrahedralMesh(referenceTrabecularAreaFile)
  }

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

  override def saveLandmarks(stage: Stage, id: CaseId, landmarks: Seq[Landmark[_3D]]): Try[Unit] = {
    LandmarkIO.writeLandmarksJson[_3D](landmarks, landmarkFile(stage, id))
  }

  def volumesDir(stage: Stage): File = new java.io.File(stageDir(stage), "volumes")

  override def saveVolume(stage: Stage, id: CaseId, volume: DiscreteImage[_3D, Short]): Try[Unit] = {
    ImageIO.writeNifti(volume, volumeFile(stage, id))
  }

  def bmdVolumeFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}_bmd.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}_bmd.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}_bmd.nii")
  }

  override def bmdVolume(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (bmdVolumeFile(stage, id).getPath.endsWith(".gz")) {
      readZippedImage(labelMapFile(stage, id), ImageIO.read3DScalarImage[Short])
    } else {
      ImageIO.read3DScalarImage[Short](labelMapFile(stage, id))
    }

  }

  override def saveBmdVolume(stage: Stage, id: CaseId, volume: DiscreteImage[_3D, Short]): Try[Unit] = {
    ImageIO.writeNifti(volume, bmdVolumeFile(stage, id))
  }

  def labelMapFile(stage: Stage, id: CaseId): File = {
    if (volumesDir(stage).listFiles().find(f => f.getPath.endsWith(s"${id.value}_seg.nii.gz")).isDefined)
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii.gz")
    else
      new java.io.File(volumesDir(stage), s"${id.value}_seg.nii")
  }

  override def labelMap(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]] = {
    if (labelMapFile(stage, id).getPath.endsWith(".gz")) {
      readZippedImage(labelMapFile(stage, id), ImageIO.read3DScalarImage[Short])
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
    // we read the image as float, as some file seem to have intensity transformations. As we expect
    // them to all be normal CT images, we convert them back to short
    val reader = (file: java.io.File) => ImageIO.read3DScalarImageAsType[Float](file).map(image => image.map(_.toShort))
    if (volumeFile(stage, id).getPath.endsWith(".gz"))
      readZippedImage(volumeFile(stage, id), reader)
    else {
      reader(volumeFile(stage, id))
    }
  }

  def gpModelDir: File = new java.io.File(referenceDir, "model")
  def gpModelTriangleMeshFile(level: ResolutionLevel): File = {
    level match {
      case ResolutionLevel.Fine   => new java.io.File(gpModelDir, "gpmodel-trianglemesh-coarse.h5")
      case ResolutionLevel.Medium => new java.io.File(gpModelDir, "gpmodel-trianglemesh-medium.h5")
      case ResolutionLevel.Coarse => new java.io.File(gpModelDir, "gpmodel-trianglemesh-fine.h5")
    }
  }
  override def gpModelTriangleMesh(level: ResolutionLevel): Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(gpModelTriangleMeshFile(level))
  }

  def gpModelTetrahedralMeshFile(level: ResolutionLevel): File = {
    level match {
      case ResolutionLevel.Fine   => new java.io.File(gpModelDir, "gpmodel-tetrahedralmesh-coarse.h5")
      case ResolutionLevel.Medium => new java.io.File(gpModelDir, "gpmodel-tetrahedralmesh-medium.h5")
      case ResolutionLevel.Coarse => new java.io.File(gpModelDir, "gpmodel-tetrahedralmesh-fine.h5")
    }
  }
  override def gpModelTetrahedralMesh(level: ResolutionLevel): Try[PointDistributionModel[_3D, TetrahedralMesh]] = {
    StatisticalModelIO.readStatisticalTetrahedralMeshModel3D(gpModelTetrahedralMeshFile(level))
  }

  def ssmDir(level: ResolutionLevel): File = new java.io.File(stageDir(Stage.Registered(level)), "model")

  def ssmFile(level: ResolutionLevel): File = {
    level match {
      case ResolutionLevel.Fine   => new java.io.File(ssmDir(level), "ssm-coarse.h5")
      case ResolutionLevel.Medium => new java.io.File(ssmDir(level), "ssm-medium.h5")
      case ResolutionLevel.Coarse => new java.io.File(ssmDir(level), "ssm-fine.h5")
    }
  }
  override def ssm(level: ResolutionLevel): Try[PointDistributionModel[_3D, TriangleMesh]] = {
    StatisticalModelIO.readStatisticalTriangleMeshModel3D(ssmFile(level))
  }

  override def saveGpModelTriangleMesh(gpModel: PointDistributionModel[_3D, TriangleMesh],
                                       level: ResolutionLevel): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(gpModel, gpModelTriangleMeshFile(level))
  }

  override def saveGpModelTetrahedralMesh(gpModel: PointDistributionModel[_3D, TetrahedralMesh],
                                          level: ResolutionLevel): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTetrahedralMeshModel3D(gpModel, gpModelTetrahedralMeshFile(level))
  }

  override def saveSSM(ssm: PointDistributionModel[_3D, TriangleMesh], level: ResolutionLevel): Try[Unit] = {
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(ssm, ssmFile(level))
  }

  def intensityModelDir(level: ResolutionLevel): File = new java.io.File(stageDir(Stage.Registered(level)), "model")
  def intensityModelFile(level: ResolutionLevel) = {
    level match {
      case ResolutionLevel.Coarse => new java.io.File(intensityModelDir(level), "intensity-model-coarse.h5")
      case ResolutionLevel.Medium => new java.io.File(intensityModelDir(level), "intensity-model-medium.h5")
      case ResolutionLevel.Fine   => new java.io.File(intensityModelDir(level), "intensity-model-fine.h5")
    }
  }
  def intensityModel(level: ResolutionLevel): Try[DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]] = {
    StatisticalModelIO.readVolumeMeshIntensityModel3D(intensityModelFile(level))
  }

  override def saveIntensityModel(
    intensityModel: DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float],
    level: ResolutionLevel
  ): Try[Unit] = {
    StatisticalModelIO.writeVolumeMeshIntensityModel3D(intensityModel, intensityModelFile(level))
  }

}

object DirectoryBasedDataRepository {

  /** Factory method used to create a new data provider */
  def of(vertebra: Vertebra): DataRepository = new DirectoryBasedDataRepository(vertebra)

  def mkdirs(vertebra: Vertebra): Unit = {
    val dataRepository = new DirectoryBasedDataRepository(vertebra)
    dataRepository.referenceDir.mkdirs()
    dataRepository.gpModelDir.mkdirs()

    for (level <- Seq(ResolutionLevel.Coarse, ResolutionLevel.Medium, ResolutionLevel.Fine)) {
      dataRepository.ssmDir(level).mkdirs()
      dataRepository.intensityModelDir(level).mkdirs()

      for (stage <- Seq(Initial, Aligned, Registered(level))) {
        dataRepository.triangleMeshDir(stage).mkdirs()
        dataRepository.tetrahedralMeshDir(stage).mkdirs()
        dataRepository.landmarkDir(stage).mkdirs()
        dataRepository.volumesDir(stage).mkdirs()
      }
    }
  }

  /**
   * Helper method used to read a gzipped nii image.
   */
  private def readZippedImage[S: Scalar](
    gzippedImageFile: java.io.File,
    imageReader: File => Try[DiscreteImage[_3D, S]]
  ): Try[DiscreteImage[_3D, S]] = {
    val gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(gzippedImageFile)))
    val tmpFile = java.io.File.createTempFile("lbl", ".nii")
    val os = new FileOutputStream(tmpFile)
    os.write(gis.readAllBytes())
    os.close()
    val image = imageReader(tmpFile)

    tmpFile.delete()
    image
  }

}
