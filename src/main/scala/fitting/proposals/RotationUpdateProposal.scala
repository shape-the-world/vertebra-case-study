package fitting.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import fitting.contour.Sample
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.MultivariateNormalDistribution

case class RotationUpdateProposal(stddev: Double)(implicit rng: scalismo.utils.Random)
    extends ProposalGenerator[Sample]
    with TransitionProbability[Sample] {

  val perturbationDistr =
    new MultivariateNormalDistribution(DenseVector.zeros[Double](3), DenseMatrix.eye[Double](3) * stddev * stddev)

  def propose(sample: Sample): Sample = {
    val perturbation = perturbationDistr.sample

    val (phi, theta, psi) = sample.fittingParameters.poseParameters.rotationAngles
    val newPoseParameters = sample.fittingParameters.poseParameters.copy(
      rotationAngles = (phi + perturbation(0), theta + perturbation(1), psi + perturbation(1))
    )
    val newParameters = sample.fittingParameters.copy(
      poseParameters = newPoseParameters
    )
    sample.copy(generatedBy = s"RotationUpdateProposal ($stddev)", fittingParameters = newParameters)
  }

  override def logTransitionProbability(from: Sample, to: Sample) = {
    val toRotationParameters = to.fittingParameters.poseParameters.rotationAngles
    val fromRotationParameters = from.fittingParameters.poseParameters.rotationAngles
    val residual = DenseVector(
      toRotationParameters._1 - fromRotationParameters._1,
      toRotationParameters._2 - fromRotationParameters._2,
      toRotationParameters._3 - fromRotationParameters._3
    )
    perturbationDistr.logpdf(residual)
  }
}
