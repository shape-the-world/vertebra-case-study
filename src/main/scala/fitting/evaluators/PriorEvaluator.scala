package fitting.evaluators

import fitting.contour.Sample
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.PointDistributionModel

case class PriorEvaluator(model: PointDistributionModel[_3D, TriangleMesh]) extends DistributionEvaluator[Sample] {

  val translationPrior = breeze.stats.distributions.Uniform(-10000, 10000) //(0.0, 20.0)
  val rotationPrior = breeze.stats.distributions.Uniform(-2 * Math.PI, 2 * Math.PI)

  override def logValue(sample: Sample): Double = {
    model.gp.logpdf(sample.fittingParameters.shapeCoefficients) +
      translationPrior.logPdf(sample.fittingParameters.poseParameters.translationParameters.x) +
      translationPrior.logPdf(sample.fittingParameters.poseParameters.translationParameters.y) +
      translationPrior.logPdf(sample.fittingParameters.poseParameters.translationParameters.z) +
      rotationPrior.logPdf(sample.fittingParameters.poseParameters.rotationAngles._1) +
      rotationPrior.logPdf(sample.fittingParameters.poseParameters.rotationAngles._2) +
      rotationPrior.logPdf(sample.fittingParameters.poseParameters.rotationAngles._3)
  }
}
