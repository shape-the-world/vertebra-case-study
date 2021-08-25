package fitting.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import fitting.contour.Sample
import fitting.intensity.IntensityFitting.IntensityModel
import fitting.intensity.IntensitySample
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{MultivariateNormalDistribution, PointDistributionModel}

case class IntensityGaussianUpdateProposal(model: IntensityModel, stddev: Double, numCoefficientsToChange : Int)(implicit rng: scalismo.utils.Random)
    extends ProposalGenerator[IntensitySample]
    with TransitionProbability[IntensitySample] {

  private val effectiveNumCoefficientsToChange = Math.min(model.rank, numCoefficientsToChange)

  val perturbationDistr = new MultivariateNormalDistribution(
    DenseVector.zeros(effectiveNumCoefficientsToChange),
    DenseMatrix.eye[Double](effectiveNumCoefficientsToChange) * stddev * stddev
  )

  override def propose(sample: IntensitySample): IntensitySample = {
    val shapeCoefficients = sample.fittingParameters.intensityModelCoefficients
    val newCoefficients = shapeCoefficients.copy
    newCoefficients(0 until effectiveNumCoefficientsToChange) := shapeCoefficients(0 until effectiveNumCoefficientsToChange) + perturbationDistr.sample
    val newParameters = sample.fittingParameters.copy(intensityModelCoefficients = newCoefficients)
    sample.copy(generatedBy = s"ShapeUpdateProposal ($stddev)", fittingParameters = newParameters)
  }

  override def logTransitionProbability(from: IntensitySample, to: IntensitySample) = {
    val residual = to.fittingParameters.intensityModelCoefficients(0 until effectiveNumCoefficientsToChange) - from.fittingParameters.intensityModelCoefficients(0 until effectiveNumCoefficientsToChange)
    perturbationDistr.logpdf(residual)
  }
}
