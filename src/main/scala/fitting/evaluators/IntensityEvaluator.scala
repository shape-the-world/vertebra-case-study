package fitting.evaluators

import breeze.stats.distributions.CauchyDistribution
import fitting.intensity.IntensityFitting.IntensityModel
import fitting.intensity.IntensitySample
import rendering.{BarycentricInterpolatorRestrictedToVolume3D, DRRRenderer}
import scalismo.common.interpolation.{BSplineImageInterpolator3D, LinearImageInterpolator3D}
import scalismo.geometry._3D
import scalismo.image.DiscreteImage
import scalismo.numerics.UniformSampler
import scalismo.sampling.DistributionEvaluator

case class IntensityEvaluator(model: IntensityModel,
                              targetImageXZ: DiscreteImage[_3D, Float],
                              targetImageYZ: DiscreteImage[_3D, Float],
                              errorModel: CauchyDistribution,
                              drrRenderer: DRRRenderer,
                              numberOfSamplingPoints: Int)(implicit val rng: scalismo.utils.Random)
    extends DistributionEvaluator[IntensitySample] {

  override def logValue(sample: IntensitySample): Double = {

    val volumeMeshForParameters = model
      .instance(sample.fittingParameters.intensityModelCoefficients)
    val volumeMeshInterpolated =
      volumeMeshForParameters.interpolate(new BarycentricInterpolatorRestrictedToVolume3D[Float])

    val drrImageXZ = drrRenderer
      .projectXZ(volumeMeshInterpolated, outsideValue = 0f)

    val drrImageXZInterpolated = drrImageXZ
      .interpolate(LinearImageInterpolator3D())

    //val imageYZ = drrRenderer.projectYZ(volumeMeshForParameters, outsideValue =  0f)
    val targetImageXZInterpolated = targetImageXZ.interpolate(LinearImageInterpolator3D())

    val sampler = UniformSampler(targetImageXZ.domain.boundingBox, numberOfSamplingPoints)
    val samplingPoints = sampler.sample().map(_._1)

    val likelihoodsXY = for (samplingPoint <- samplingPoints) yield {
      val residual = drrImageXZInterpolated(samplingPoint) - targetImageXZInterpolated(samplingPoint)
      errorModel.logPdf(residual)
    }

    val loglikelihood = likelihoodsXY.sum
    loglikelihood
  }

}
