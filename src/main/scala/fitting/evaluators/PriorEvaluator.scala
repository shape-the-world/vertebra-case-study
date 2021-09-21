package fitting.evaluators

import fitting.contour.Sample
import fitting.model.Prior
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.PointDistributionModel

case class PriorEvaluator(model: PointDistributionModel[_3D, TriangleMesh])(implicit rng: scalismo.utils.Random)
    extends DistributionEvaluator[Sample] {

  override def logValue(sample: Sample): Double = {
    model.gp.logpdf(sample.fittingParameters.shapeCoefficients) +
      Prior.translationPriorX.logPdf(sample.fittingParameters.poseParameters.translationParameters.x) +
      Prior.translationPriorX.logPdf(sample.fittingParameters.poseParameters.translationParameters.y) +
      Prior.translationPriorX.logPdf(sample.fittingParameters.poseParameters.translationParameters.z) +
      Prior.rotationPriorPhi.logPdf(sample.fittingParameters.poseParameters.rotationAngles._1) +
      Prior.rotationPriorTheta.logPdf(sample.fittingParameters.poseParameters.rotationAngles._2) +
      Prior.rotationPriorPsi.logPdf(sample.fittingParameters.poseParameters.rotationAngles._3)
  }
}
