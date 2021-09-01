package bmd

import data.DataRepository.{CaseId, Stage}
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import scalismo.common.Field
import scalismo.common.interpolation.BSplineImageInterpolator3D
import scalismo.geometry.{_3D, Point}
import scalismo.image.DiscreteImage
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.numerics.UniformTetrahedralMeshSampler3D

object BMDMeasurement {

  def estimateBMD(volume: Field[_3D, Float],
                  trabecularVolume: TetrahedralMesh[_3D])(implicit rng: scalismo.utils.Random): Double = {

    val sampler = UniformTetrahedralMeshSampler3D(trabecularVolume, numberOfPoints = 1000)
    val samplePoints = sampler.sample().map(_._1)

    val intensityValues = for (point <- samplePoints) yield {
      volume(point).toInt
    }

    val integratedHUOnTrabecularVolume = intensityValues.sum * (trabecularVolume.volume / samplePoints.size)
    val averageHU = integratedHUOnTrabecularVolume / trabecularVolume.volume
    averageHU
  }

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    implicit val rng = scalismo.utils.Random(42)

    val datarepo = DirectoryBasedDataRepository.of(VertebraL1)
    val volume = datarepo.volume(Stage.Aligned, CaseId("verse-005")).get
    val trabecularVolume = datarepo.referenceTrabecularVolume.get
    val interpolatedImage = volume.interpolate(BSplineImageInterpolator3D(degree = 3))
    println("estimated bmd: " + estimateBMD(interpolatedImage.andThen(_.toShort), trabecularVolume))

  }

}
