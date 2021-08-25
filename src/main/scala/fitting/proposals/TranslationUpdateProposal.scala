package fitting.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import breeze.stats.distributions.Gaussian
import fitting.contour.Sample
import fitting.proposals.TranslationUpdateProposal.{Axes, XAxis, YAxis, ZAxis}
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.MultivariateNormalDistribution

case class TranslationUpdateProposal(stddev: Double, axes: Axes)(implicit rng: scalismo.utils.Random)
    extends ProposalGenerator[Sample]
    with TransitionProbability[Sample] {

  val perturbationDistr = new Gaussian(0, stddev * stddev)

  def propose(sample: Sample): Sample = {
    val translationParameters = sample.fittingParameters.poseParameters.translationParameters

    val newTranslationParameters = axes match {
      case XAxis => translationParameters.copy(x = translationParameters.x + perturbationDistr.sample())
      case YAxis => translationParameters.copy(y = translationParameters.y + perturbationDistr.sample())
      case ZAxis => translationParameters.copy(z = translationParameters.z + perturbationDistr.sample())
    }

    val newPoseParameters =
      sample.fittingParameters.poseParameters.copy(translationParameters = newTranslationParameters)
    val newParameters = sample.fittingParameters.copy(poseParameters = newPoseParameters)
    sample.copy(generatedBy = s"TranslationUpdateProposal ($stddev)", fittingParameters = newParameters)
  }

  override def logTransitionProbability(from: Sample, to: Sample) = {
    val (fromPara, toParam) = axes match {
      case XAxis =>
        (to.fittingParameters.poseParameters.translationParameters.x,
         from.fittingParameters.poseParameters.translationParameters.x)
      case YAxis =>
        (to.fittingParameters.poseParameters.translationParameters.y,
         from.fittingParameters.poseParameters.translationParameters.y)
      case ZAxis =>
        (to.fittingParameters.poseParameters.translationParameters.z,
         from.fittingParameters.poseParameters.translationParameters.z)
    }
    val residual = toParam - fromPara

    perturbationDistr.logPdf(residual)
  }
}

object TranslationUpdateProposal {
  trait Axes { def selector: Int }
  case object XAxis extends Axes { override val selector: Int = 0 }
  case object YAxis extends Axes { override val selector: Int = 1 }
  case object ZAxis extends Axes { override val selector: Int = 2 }
}
