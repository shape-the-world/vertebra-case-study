package pipeline

import com.typesafe.scalalogging.StrictLogging
import data.DataProvider
import data.DataProvider.Stage
import data.DataProvider.Vertebra.VertebraL1
import scalismo.common.interpolation.{BSplineImageInterpolator3D, NearestNeighborInterpolator3D}
import scalismo.geometry.{Landmark, Point3D, _3D}
import scalismo.image.DiscreteImageDomain
import scalismo.io.{ImageIO, LandmarkIO, MeshIO}
import scalismo.registration.LandmarkRegistration

import scala.util.{Failure, Success, Try}

object AlignData extends StrictLogging {

  def processCase(referenceLandmarks: Seq[Landmark[_3D]],
                  referenceDomain: DiscreteImageDomain[_3D],
                  dataProvider: DataProvider,
                  caseId: DataProvider.CaseId)
                 (implicit rng : scalismo.utils.Random)
  : Try[Unit] = {
    Try {
      val landmarks = dataProvider.landmarks(Stage.Initial, caseId).get
      val mesh = dataProvider.triangleMesh(Stage.Initial, caseId).get
      val labelMap = dataProvider.labelMap(Stage.Initial, caseId).get
      val volume = dataProvider.volume(Stage.Initial, caseId).get

      val transform =
        LandmarkRegistration.rigid3DLandmarkRegistration(landmarks, referenceLandmarks, center = Point3D(0, 0, 0))

      val alignedMeshFile = dataProvider.triangleMeshFile(Stage.Aligned, caseId)
      MeshIO.writeMesh(mesh.transform(transform), alignedMeshFile).get

      val alignedLandmarks = landmarks.map(_.transform(transform))
      val alignedLandmarksFile = dataProvider.landmarkFile(Stage.Aligned, caseId)
      LandmarkIO.writeLandmarksJson(alignedLandmarks, alignedLandmarksFile).get

      val alignedLabelmapFile = dataProvider.labelMapFile(Stage.Aligned, caseId)
      val alignedLabelMap = labelMap
        .interpolate(NearestNeighborInterpolator3D())
        .compose(transform.inverse)
        .discretize(referenceDomain, outsideValue = 0.toShort)

      ImageIO.writeNifti(alignedLabelMap, alignedLabelmapFile).get

      val alignedVolumeFile = dataProvider.volumeFile(Stage.Aligned, caseId)
      val alignedVolume = volume
        .interpolate(BSplineImageInterpolator3D(degree = 3))
        .compose(transform.inverse)
        .discretize(referenceDomain, outsideValue = 0.toShort)
      ImageIO.writeNifti(alignedVolume, alignedVolumeFile)
    }
  }

  def main(args: Array[String]): Unit = {

    scalismo.initialize()
    implicit val rng = scalismo.utils.Random(42)

    val dataProvider = DataProvider.of(VertebraL1)
    dataProvider.volumesDir(Stage.Aligned).mkdirs()
    dataProvider.triangleMeshDir(Stage.Aligned).mkdirs()
    dataProvider.landmarkDir(Stage.Aligned).mkdirs()

    val referenceLandmarks = dataProvider.referenceLandmarks.get
    val referenceDomain = dataProvider.referenceVolume.get.domain

    for (caseId <- dataProvider.caseIds) {
      logger.info(s"working on case $caseId")
      processCase(referenceLandmarks, referenceDomain, dataProvider, caseId) match {
        case Success(_)         => logger.info(s"successfully processed $caseId")
        case Failure(exception) => {
          logger.error(s"an error occurred while processing $caseId: " + exception.getMessage)
        }
      }

    }

  }
}
