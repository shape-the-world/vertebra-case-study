package fitting.intensity

import breeze.linalg.{DenseMatrix, DenseVector}
import breeze.stats.distributions.CauchyDistribution
import com.typesafe.scalalogging.StrictLogging
import fitting.evaluators
import fitting.evaluators.{CachedEvaluator, IntensityEvaluator, IntensityPriorEvaluator}
import fitting.logger.IntensitySampleLogger
import fitting.proposals.IntensityGaussianUpdateProposal
import rendering.{DRRRenderer, XRay}
import scalismo.geometry.{_2D, _3D}
import scalismo.image.DiscreteImage
import scalismo.mesh.{LineMesh, TetrahedralMesh, TriangleMesh}
import scalismo.sampling.algorithms.MetropolisHastings
import scalismo.sampling.evaluators.ProductEvaluator
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{
  DiscreteGaussianProcess,
  DiscreteLowRankGaussianProcess,
  MultivariateNormalDistribution,
  PointDistributionModel
}

object IntensityFitting extends StrictLogging {

  case class SampleWithProbability(sample: IntensitySample, logProb: Double)

  type IntensityModel = DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]
  def runFittingChain(
    intensityModel: IntensityModel,
    initialParameters: IntensityFittingParameters,
    targetImageXZ: DiscreteImage[_3D, Float],
    targetImageYZ: DiscreteImage[_3D, Float],
    drrRenderer: DRRRenderer,
    proposalGenerator: ProposalGenerator[IntensitySample] with TransitionProbability[IntensitySample],
    numberOfSamples: Int,
    statusCallback: (IntensitySample, Int) => Unit
  )(implicit rng: scalismo.utils.Random): Seq[SampleWithProbability] = {

    val intensityEvaluator = CachedEvaluator(
      IntensityEvaluator(intensityModel,
                         targetImageXZ,
                         targetImageYZ,
                         new CauchyDistribution(0.0, 100.0),
                         drrRenderer,
                         numberOfSamplingPoints = 200)
    )

    val priorEvaluator = evaluators.IntensityPriorEvaluator(intensityModel)
    val posteriorEvaluator = ProductEvaluator(priorEvaluator, intensityEvaluator)

    // TODO generalize logger
    val sampleLogger = IntensitySampleLogger()
    val mh = MetropolisHastings(proposalGenerator, posteriorEvaluator)

    val initialSample = IntensitySample("initialSample", initialParameters)

    val samplingIterator = mh.iterator(initialSample, sampleLogger).zipWithIndex.map {
      case (sample, itNumber) =>
        statusCallback(sample, itNumber)
        SampleWithProbability(sample, posteriorEvaluator.logValue(sample))
    }

    // we generate some samples
    val samples = samplingIterator.take(numberOfSamples).toIndexedSeq
    logger.info(sampleLogger.acceptanceRatios().toString)

    samples
  }

  def fit(model: IntensityModel,
          initialParameters: IntensityFittingParameters,
          targetImageXZ: DiscreteImage[_3D, Float],
          targetImageYZ: DiscreteImage[_3D, Float],
          drrRenderer: DRRRenderer,
          statusCallback: (IntensitySample, Int) => Unit)(implicit rng: scalismo.utils.Random): IntensitySample = {

    val intensityUpdateGenerator = MixtureProposal.fromProposalsWithTransition(
      (1.0, IntensityGaussianUpdateProposal(model, 0.1, numCoefficientsToChange = 1))
    )
    val samples =
      runFittingChain(model,
                      initialParameters,
                      targetImageXZ,
                      targetImageYZ,
                      drrRenderer,
                      intensityUpdateGenerator,
                      500,
                      statusCallback)
    val bestSample = samples.maxBy(_.logProb).sample
    bestSample
  }

}
