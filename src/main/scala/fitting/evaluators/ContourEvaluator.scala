package fitting.evaluators

import fitting.contour.{FittingParameters, Sample}
import rendering.XRay
import scalismo.geometry._3D
import scalismo.mesh.{LineMesh, TriangleMesh}
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.{MultivariateNormalDistribution, PointDistributionModel}

case class ContourEvaluator(model: PointDistributionModel[_3D, TriangleMesh],
                            targetContourXY: LineMesh[_3D],
                            targetContourXZ: LineMesh[_3D],
                            errorModel: MultivariateNormalDistribution,
                            xRay: XRay)
    extends DistributionEvaluator[Sample] {

  override def logValue(sample: Sample): Double = {

    val currModelInstance = model.reference.transform(
      FittingParameters
        .fullTransformation(model, sample.fittingParameters.poseParameters, sample.fittingParameters.shapeCoefficients)
    )
    val contourXY = xRay.projectContoursXY(currModelInstance)
    val likelihoodsXY = for (modelContourPoint <- targetContourXY.pointSet.points) yield {
      val closestPointOnTarget = contourXY.pointSet.findClosestPoint(modelContourPoint).point
      val residual = modelContourPoint - closestPointOnTarget
      errorModel.logpdf(residual.toBreezeVector)
    }
    val contourXZ = xRay.projectContoursXZ(currModelInstance)
    val likelihoodsXZ = for (modelContourPoint <- targetContourXZ.pointSet.points) yield {
      val closestPointOnTarget = contourXZ.pointSet.findClosestPoint(modelContourPoint).point
      val residual = modelContourPoint - closestPointOnTarget
      errorModel.logpdf(residual.toBreezeVector)
    }

    val loglikelihood = likelihoodsXY.sum + likelihoodsXZ.sum
    loglikelihood
  }

}

object ContourEvaluator {}
