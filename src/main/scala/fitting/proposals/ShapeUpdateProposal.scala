package fitting.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import fitting.contour.Sample
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{MultivariateNormalDistribution, PointDistributionModel}

case class ShapeUpdateProposal(model: PointDistributionModel[_3D, TriangleMesh], stddev: Double, numCoefficientsToChange : Int)(implicit rng: scalismo.utils.Random)
    extends ProposalGenerator[Sample]
    with TransitionProbability[Sample] {

  private val effectiveNumCoefficientsToChange = Math.min(model.rank, numCoefficientsToChange)

  val perturbationDistr = new MultivariateNormalDistribution(
    DenseVector.zeros(effectiveNumCoefficientsToChange),
    DenseMatrix.eye[Double](effectiveNumCoefficientsToChange) * stddev * stddev
  )

  override def propose(sample: Sample): Sample = {
    val shapeCoefficients = sample.fittingParameters.shapeCoefficients
    val newCoefficients = shapeCoefficients.copy
    newCoefficients(0 until effectiveNumCoefficientsToChange) := shapeCoefficients(0 until effectiveNumCoefficientsToChange) + perturbationDistr.sample
    val newParameters = sample.fittingParameters.copy(shapeCoefficients = newCoefficients)
    sample.copy(generatedBy = s"ShapeUpdateProposal ($stddev)", fittingParameters = newParameters)
  }

  override def logTransitionProbability(from: Sample, to: Sample) = {
    val residual = to.fittingParameters.shapeCoefficients(0 until effectiveNumCoefficientsToChange) - from.fittingParameters.shapeCoefficients(0 until effectiveNumCoefficientsToChange)
    perturbationDistr.logpdf(residual)
  }
}
