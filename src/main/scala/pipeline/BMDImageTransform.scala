package pipeline

import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.{CaseId, Stage}
import data.DataRepository.Vertebra.VertebraL1
import data.{DataRepository, DirectoryBasedDataRepository}
import scalismo.common.BoxDomain3D
import scalismo.common.interpolation.BSplineImageInterpolator3D
import scalismo.geometry.{_3D, EuclideanVector3D, IntVector3D, Point}
import scalismo.image.{DiscreteImage, DiscreteImage3D, DiscreteImageDomain3D, StructuredPoints, StructuredPoints3D}
import scalismo.io.ImageIO
import scalismo.numerics.GridSampler
import scalismo.ui.api.ScalismoUI
import tools.Debug.image

import scala.util.{Failure, Success, Try}

object BMDImageTransform extends StrictLogging {

  def bmdTransform(image: DiscreteImage[_3D, Short]): DiscreteImage[_3D, Short] = {

    val interpolatedImage = image.interpolate(BSplineImageInterpolator3D(degree = 3))
    val bmdValue = (point: Point[_3D]) => {
      val cubeResolution = IntVector3D(5, 5, 5)
      val cube = BoxDomain3D(
        origin = point - EuclideanVector3D(0.5, 0.5, 0.5),
        oppositeCorner = point + EuclideanVector3D(0.5, 0.5, 0.5)
      )

      val cubeGrid = DiscreteImageDomain3D(cube, size = cubeResolution) // grid on cubic cm
      val intensitiesInCube = for (pt <- cubeGrid.pointSet.points) yield {
        if (interpolatedImage.isDefinedAt(pt)) interpolatedImage(pt).toLong else 0L
      }
      (intensitiesInCube.sum / cubeGrid.pointSet.numberOfPoints).toShort
    }

    DiscreteImage3D(image.domain, bmdValue)
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val ui = ScalismoUI()

    val dataRepository = DirectoryBasedDataRepository.of(VertebraL1)
    val volume = dataRepository.volume(Stage.Aligned, CaseId("005")).get
    val mesh = dataRepository.triangleMesh(Stage.Aligned, CaseId("005")).get
    val croppedVolume = volume
      .interpolate(BSplineImageInterpolator3D(degree = 3))
      .discretize(DiscreteImageDomain3D(mesh.boundingBox, size = IntVector3D(64, 64, 64)), outsideValue = 0)

    val bmdVolume = bmdTransform(croppedVolume)
    ui.show(bmdVolume, "bmd")
    ui.show(croppedVolume, "cropped")
    //
//    def processCase(dataRepository: DataRepository, caseId: CaseId): Try[Unit] = {
//      dataRepository.volume(Stage.Aligned, caseId) match {
//        case Success(image) => {
//          val transformedImage = bmdTransform(image)
//          dataRepository.saveBmdVolume(Stage.Aligned, caseId, transformedImage)
//        }
//        case Failure(e) => Failure(e)
//      }
//    }
//
//    for (caseId <- dataRepository.caseIds) {
//      logger.info(s"working on case $caseId")
//      processCase(dataRepository, caseId) match {
//        case Success(_) => logger.info(s"successfully processed $caseId")
//        case Failure(exception) => {
//          logger.error(s"an error occurred while processing $caseId: " + exception.getMessage)
//        }
//      }
//    }

  }

}
