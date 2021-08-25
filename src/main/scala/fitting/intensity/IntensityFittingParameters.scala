package fitting.intensity

import breeze.linalg.DenseVector;

case class IntensityFittingParameters(intensityModelCoefficients: DenseVector[Double])

object IntensityFittingParameters {}

case class IntensitySample(generatedBy: String, fittingParameters: IntensityFittingParameters)
