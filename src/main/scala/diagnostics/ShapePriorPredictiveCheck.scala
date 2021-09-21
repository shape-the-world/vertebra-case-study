package diagnostics

import breeze.linalg.DenseVector
import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.{DataRepository, DirectoryBasedDataRepository}
import rendering.XRay
import scalismo.geometry.{_3D, EuclideanVector3D, Point3D}
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.transformations.Translation3D
import scalismo.ui.api.ScalismoUI

object ShapePriorPredictiveCheck {

  implicit val rng = scalismo.utils.Random(42L)

  def visualizeShapes(pdm: PointDistributionModel[_3D, TriangleMesh]): Unit = {
    val ui = ScalismoUI()
    val shapes3dGroup = ui.createGroup("pdms")
    ui.show(shapes3dGroup, pdm, "pdf")

  }

  def visualizeContours(pdm: PointDistributionModel[_3D, TriangleMesh]): Unit = {
    val xRay = new XRay(sensorDistance = 400, sensorWidth = 15000, sensorHeight = 15000)

    val ui = ScalismoUI()
    val contourGroup = ui.createGroup("contours")
    val samples = Seq(
      DenseVector.zeros[Double](pdm.rank),
      DenseVector.tabulate(pdm.rank)(i => if (i == 0) 3.0 else 0.0),
      DenseVector.tabulate(pdm.rank)(i => if (i == 0) -3.0 else 0.0)
    )
    for ((sample, i) <- samples.zipWithIndex) yield {
      val view = ui.show(contourGroup, xRay.projectContoursXY(pdm.instance(sample)), s"contour-$i")
      view.color = java.awt.Color.getHSBColor(i.asInstanceOf[Float] / samples.size.asInstanceOf[Float], 0.85f, 1.0f)
    }
  }

  def main(args: Array[String]): Unit = {

    scalismo.initialize()

    val repo = DirectoryBasedDataRepository.of(VertebraL1)
    val pdmOrigPos = repo.ssm(ResolutionLevel.Fine).get

    val meanPoints = pdmOrigPos.mean.pointSet.points.toIndexedSeq
    val centerOfMass =
      meanPoints.foldLeft(EuclideanVector3D(0, 0, 0))((acc, pt) => acc + pt.toVector * (1.0 / meanPoints.size)).toPoint
    val translationToOrigin = Translation3D(Point3D(0, 0, 0) - centerOfMass)
    val pdm = pdmOrigPos.transform(translationToOrigin)
    visualizeContours(pdm)

  }

}
