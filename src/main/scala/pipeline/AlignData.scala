package pipeline

import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.Stage
import data.DataRepository.Vertebra.VertebraL1
import data.{DataRepository, DirectoryBasedDataRepository}
import scalismo.common.interpolation.{BSplineImageInterpolator3D, NearestNeighborInterpolator3D}
import scalismo.geometry.{Landmark, Point3D, _3D}
import scalismo.image.DiscreteImageDomain
import scalismo.registration.LandmarkRegistration

import scala.util.{Failure, Success, Try}

/**
 * Pipeline step to align the data using landmarks.
 * All the individual data representations (labelmap, volume, triangle meshes, landmarks) are aligned.
 */
object AlignData extends StrictLogging {

  def processCase(referenceLandmarks: Seq[Landmark[_3D]],
                  referenceDomain: DiscreteImageDomain[_3D],
                  dataRepository: DataRepository,
                  caseId: DataRepository.CaseId)(implicit rng: scalismo.utils.Random): Try[Unit] = {
    Try {
      val landmarks = dataRepository.landmarks(Stage.Initial, caseId).get
      val mesh = dataRepository.triangleMesh(Stage.Initial, caseId).get
      val labelMap = dataRepository.labelMap(Stage.Initial, caseId).get
      val volume = dataRepository.volume(Stage.Initial, caseId).get

      val transform =
        LandmarkRegistration.rigid3DLandmarkRegistration(landmarks, referenceLandmarks, center = Point3D(0, 0, 0))

      val alignedMeshFile = dataRepository.saveTriangleMesh(Stage.Aligned, caseId, mesh.transform(transform)).get

      val alignedLandmarks = landmarks.map(_.transform(transform))
      dataRepository.saveLandmarks(Stage.Aligned, caseId, alignedLandmarks)

      val alignedLabelMap = labelMap
        .interpolate(NearestNeighborInterpolator3D())
        .compose(transform.inverse)
        .discretize(referenceDomain, outsideValue = 0.toShort)

      dataRepository.saveLabelMap(Stage.Aligned, caseId, alignedLabelMap).get

      val alignedVolume = volume
        .interpolate(BSplineImageInterpolator3D(degree = 3))
        .compose(transform.inverse)
        .discretize(referenceDomain, outsideValue = 0.toShort)
      dataRepository.saveVolume(Stage.Aligned, caseId, alignedVolume)
    }
  }

  def main(args: Array[String]): Unit = {

    scalismo.initialize()
    implicit val rng = scalismo.utils.Random(42)

    val dataRepository = DirectoryBasedDataRepository.of(VertebraL1)

    val referenceLandmarks = dataRepository.referenceLandmarks.get
    val referenceDomain = dataRepository.referenceVolume.get.domain

    for (caseId <- dataRepository.caseIds) {
      logger.info(s"working on case $caseId")
      processCase(referenceLandmarks, referenceDomain, dataRepository, caseId) match {
        case Success(_) => logger.info(s"successfully processed $caseId")
        case Failure(exception) => {
          logger.error(s"an error occurred while processing $caseId: " + exception.getMessage)
        }
      }
    }

  }
}
