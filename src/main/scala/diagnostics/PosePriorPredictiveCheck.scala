package diagnostics

import breeze.linalg.DenseVector
import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import fitting.contour.ContourFitting
import fitting.evaluators.PriorEvaluator
import fitting.model.Prior
import rendering.XRay
import scalismo.geometry.{_3D, EuclideanVector3D, Point, Point3D}
import scalismo.mesh.TriangleMesh
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.transformations.{Translation3D, TranslationAfterRotation}
import scalismo.ui.api.ScalismoUI

object PosePriorPredictiveCheck {

  implicit val rng = scalismo.utils.Random(42L)

  def centerOfMass(mesh: TriangleMesh[_3D]): Point[_3D] = {
    val points = mesh.pointSet.points.toIndexedSeq
    points.foldLeft(EuclideanVector3D(0, 0, 0))((acc, pt) => acc + pt.toVector * (1.0 / points.size)).toPoint
  }

  def visualizeContours(pdm: PointDistributionModel[_3D, TriangleMesh]): Unit = {
    val xRay = new XRay(sensorDistance = 400, sensorWidth = 15000, sensorHeight = 15000)

    val ui = ScalismoUI()
    val contourGroup = ui.createGroup("contours")

    val meanShape = pdm.mean

    val rotationCenter = centerOfMass(meanShape)

    val nSamples = 10
    for (i <- 0 until nSamples) yield {
      val sampledRot = Prior.sampleRotation(rotationCenter)
      val sampledTranslation = Prior.sampleTranslation()

      val rigidTransform = TranslationAfterRotation(sampledTranslation, sampledRot)
      val translatedShape = meanShape.transform(rigidTransform)
      val view = ui.show(contourGroup, xRay.projectContoursXY(translatedShape), s"contour-$i")
      view.color = java.awt.Color.getHSBColor(i.asInstanceOf[Float] / nSamples.asInstanceOf[Float], 0.85f, 1.0f)
    }
  }

  def main(args: Array[String]): Unit = {

    scalismo.initialize()

    val repo = DirectoryBasedDataRepository.of(VertebraL1)
    val pdmOrigPos = repo.ssm(ResolutionLevel.Fine).get

    val translationToOrigin = Translation3D(Point3D(0, 0, 0) - centerOfMass(pdmOrigPos.mean))
    val pdm = pdmOrigPos.transform(translationToOrigin)
    visualizeContours(pdm)

  }

}
