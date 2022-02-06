package rendering

import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import scalismo.common.Field
import scalismo.common.interpolation.BSplineImageInterpolator3D
import scalismo.geometry._
import scalismo.image.{DiscreteImage, DiscreteImage3D, DiscreteImageDomain3D}
import scalismo.mesh.ScalarVolumeMeshField
import scalismo.transformations.Translation3D
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Benchmark

import scala.collection.parallel.immutable.ParVector

class DRRRenderer(val sensorDistance: Int,
                  val sensorWidth: Double,
                  val sensorHeight: Double,
                  resolution: IntVector[_2D],
                  samplingRate: Int) {

  val sourcePointOnX = Point3D(-sensorDistance / 2, 0, 0)
  val sourcePointOnY = Point3D(0, -sensorDistance / 2, 0)

  val sensorImageDomainYZ = DiscreteImageDomain3D(
    origin = Point3D(sensorDistance / 2, -sensorWidth / 2, -sensorHeight / 2),
    spacing = EuclideanVector3D(1, sensorWidth / resolution.i, sensorHeight / resolution.j),
    size = IntVector3D(2, resolution.i, resolution.j)
  )

  val sensorImageDomainXZ = DiscreteImageDomain3D(
    origin = Point3D(-sensorWidth / 2, sensorDistance / 2, -sensorHeight / 2),
    spacing = EuclideanVector3D(sensorWidth / resolution.i, 1, sensorHeight / resolution.j),
    size = IntVector3D(resolution.i, 2, resolution.j)
  )

  def computeValue(meshField: Field[_3D, Float],
                   sensorImagePoint: Point[_3D],
                   sourcePoint: Point[_3D],
                   outsideValue: Float): (Float, Seq[Point[_3D]]) = {

    val rayFromSource = sensorImagePoint - sourcePoint
    val v = rayFromSource * (1.0 / samplingRate)
    val probePosition = for (i <- 0 until samplingRate) yield sourcePoint + v * i

    val probedValues = for (probePoint <- probePosition) yield {
      if (meshField.isDefinedAt(probePoint)) meshField(probePoint) else outsideValue
    }
    (probedValues.sum, probePosition.toIndexedSeq)
  }

  def projectXZ(field: Field[_3D, Float], outsideValue: Float): DiscreteImage[_3D, Float] = {
    //val field = mesh.interpolate(new BarycentricInterpolatorRestrictedToVolume3D())
    val imagePointsChunks =
      sensorImageDomainXZ.pointSet.points.grouped(sensorImageDomainXZ.pointSet.numberOfPoints / 16).map(_.toIndexedSeq)
    val parChunks = ParVector.fromSpecific(imagePointsChunks)
    val valueInchunks = for (chunk <- parChunks) yield {
      for (point <- chunk) yield {
        computeValue(field, point, sourcePointOnY, outsideValue)
      }
    }
    DiscreteImage3D(sensorImageDomainXZ, valueInchunks.flatten.toIndexedSeq.map(_._1))
  }

  def projectYZ(field: Field[_3D, Float], outsideValue: Float): DiscreteImage[_3D, Float] = {
    //val field = mesh.interpolate(new BarycentricInterpolatorRestrictedToVolume3D())
    val imagePointsChunks =
      sensorImageDomainYZ.pointSet.points.grouped(sensorImageDomainYZ.pointSet.numberOfPoints / 16).map(_.toIndexedSeq)
    val parChunks = ParVector.fromSpecific(imagePointsChunks)
    val valueInchunks = for (chunk <- parChunks) yield {
      for (point <- chunk) yield {
        computeValue(field, point, sourcePointOnX, outsideValue)
      }
    }
    DiscreteImage3D(sensorImageDomainYZ, valueInchunks.flatten.toIndexedSeq.map(_._1))
  }

}

object DRRRenderer {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    val resolutionLevel = ResolutionLevel.Coarse
    val repo = DirectoryBasedDataRepository.of(VertebraL1) //.asInstanceOf[DirectoryBasedDataRepository]
    val meshOrigPos = repo.referenceTetrahedralMesh(resolutionLevel).get

    val outerSurfaceOrigPos = repo.referenceTriangleMesh(resolutionLevel).get //meshOrigPos.operations.getOuterSurface
    val centerOfMassOrigPos = (outerSurfaceOrigPos.pointSet.points.foldLeft(EuclideanVector3D(0, 0, 0))((acc, point) =>
      acc + point.toVector
    ) * (1.0 / outerSurfaceOrigPos.pointSet.numberOfPoints)).toPoint

    val ctOrigPos = repo.referenceVolume.get.map(_.toFloat)
    val ctInterpolated = ctOrigPos.interpolate(BSplineImageInterpolator3D(3))
    val ctValues = meshOrigPos.pointSet.points.map(point => ctInterpolated(point).toFloat)

    val translationToScanner = Translation3D(Point3D(0, 0, 0) - centerOfMassOrigPos)

    val mesh = meshOrigPos.transform(translationToScanner)
    val centerOfMass = translationToScanner(centerOfMassOrigPos)

    val volumeMesh = ScalarVolumeMeshField(mesh, ctValues.toIndexedSeq)
    val volumeMeshField = volumeMesh.interpolate(new BarycentricInterpolatorRestrictedToVolume3D())

    val drr = new DRRRenderer(150, 200, 200, IntVector(128, 128), 200)
    val sensorImageXY = Benchmark.benchmark(drr.projectXZ(volumeMeshField, 0), "projectionxz")
    val sensorImageYZ = Benchmark.benchmark(drr.projectYZ(volumeMeshField, 0), "projectionyz")

    val ui = ScalismoUI()
    ui.show(IndexedSeq(drr.sourcePointOnX), "sourceX")
    ui.show(IndexedSeq(drr.sourcePointOnY), "sourceY")
    ui.show(volumeMesh, "mesh")
    ui.show(sensorImageXY, "drr-xy")
    ui.show(sensorImageYZ, "drr-yz")


  }
}
