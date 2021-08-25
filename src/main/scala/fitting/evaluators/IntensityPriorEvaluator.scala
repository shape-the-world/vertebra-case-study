package fitting.evaluators

import fitting.contour.Sample
import fitting.intensity.IntensityFitting.IntensityModel
import fitting.intensity.IntensitySample
import scalismo.sampling.DistributionEvaluator

case class IntensityPriorEvaluator(model: IntensityModel) extends DistributionEvaluator[IntensitySample] {

  override def logValue(sample: IntensitySample): Double = {
    model.logpdf(sample.fittingParameters.intensityModelCoefficients)
  }
}
