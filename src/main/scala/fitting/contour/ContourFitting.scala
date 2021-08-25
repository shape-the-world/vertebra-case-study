package fitting.contour

import breeze.linalg.{DenseMatrix, DenseVector}
import com.typesafe.scalalogging.StrictLogging
import fitting.contour.{FittingParameters, Sample}
import fitting.evaluators.{CachedEvaluator, ContourEvaluator, PriorEvaluator}
import fitting.logger.Logger
import fitting.proposals.TranslationUpdateProposal.{XAxis, YAxis, ZAxis}
import fitting.proposals.{RotationUpdateProposal, ShapeUpdateProposal, TranslationUpdateProposal}
import rendering.XRay
import scalismo.geometry._3D
import scalismo.mesh.{LineMesh, TriangleMesh}
import scalismo.sampling.algorithms.MetropolisHastings
import scalismo.sampling.evaluators.ProductEvaluator
import scalismo.sampling.proposals.MixtureProposal
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{MultivariateNormalDistribution, PointDistributionModel}

object ContourFitting extends StrictLogging {

  case class SampleWithProbability(sample: Sample, logProb: Double)

  def runFittingChain(
    shapeModel: PointDistributionModel[_3D, TriangleMesh],
    contourXY: LineMesh[_3D],
    contourXZ: LineMesh[_3D],
    initialParameters: FittingParameters,
    xRay: XRay,
    proposalGenerator: ProposalGenerator[Sample] with TransitionProbability[Sample],
    numberOfSamples: Int,
    statusCallback: (Sample, Int) => Unit
  )(implicit rng: scalismo.utils.Random): Seq[SampleWithProbability] = {

    val errorModel = MultivariateNormalDistribution(DenseVector.zeros[Double](3), DenseMatrix.eye[Double](3) * 5.0)
    val contourEvaluator = CachedEvaluator(ContourEvaluator(shapeModel, contourXY, contourXZ, errorModel, xRay))
    val priorEvaluator = PriorEvaluator(shapeModel)

    val posteriorEvaluator = ProductEvaluator(priorEvaluator, contourEvaluator)

    val sampleLogger = Logger()
    val mh = MetropolisHastings(proposalGenerator, posteriorEvaluator)

    val initialSample = Sample("initialSample", initialParameters)

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

  def fit(shapeModel: PointDistributionModel[_3D, TriangleMesh],
          contourXY: LineMesh[_3D],
          contourXZ: LineMesh[_3D],
          initialParameters: FittingParameters,
          xRay: XRay,
          statusCallback: (Sample, Int) => Unit)(implicit rng: scalismo.utils.Random): Sample = {

    val poseOnlyGenerator = MixtureProposal.fromProposalsWithTransition(
      (0.3, RotationUpdateProposal(0.005)),
      (0.1, TranslationUpdateProposal(5.0, XAxis)),
      (0.1, TranslationUpdateProposal(5.0, YAxis)),
      (0.1, TranslationUpdateProposal(5.0, ZAxis)),
      (0.3, ShapeUpdateProposal(shapeModel, 0.1, numCoefficientsToChange = 2))
    )
    val samples1stChain =
      runFittingChain(shapeModel, contourXY, contourXZ, initialParameters, xRay, poseOnlyGenerator, 500, statusCallback)
    val bestSample1stChain = samples1stChain.maxBy(_.logProb).sample

    val poseAndShapeGenerator = MixtureProposal.fromProposalsWithTransition(
      (0.1, RotationUpdateProposal(0.0001)),
      (0.2, TranslationUpdateProposal(1, XAxis)),
      (0.2, TranslationUpdateProposal(1, YAxis)),
      (0.2, TranslationUpdateProposal(1, ZAxis)),
      (0.6, ShapeUpdateProposal(shapeModel, 0.1, numCoefficientsToChange = 10))
    )

    val sample2ndChain =
      runFittingChain(shapeModel,
                      contourXY,
                      contourXZ,
                      bestSample1stChain.fittingParameters,
                      xRay,
                      poseAndShapeGenerator,
                      1000,
                      statusCallback)
    val bestSample2ndChain = sample2ndChain.maxBy(_.logProb).sample

    val finalPoseAndShapeGenerator = MixtureProposal.fromProposalsWithTransition(
      (0.1, RotationUpdateProposal(0.0001)),
      (0.1, TranslationUpdateProposal(0.5, XAxis)),
      (0.1, TranslationUpdateProposal(0.5, YAxis)),
      (0.1, TranslationUpdateProposal(0.5, ZAxis)),
      (0.6, ShapeUpdateProposal(shapeModel, 0.05, numCoefficientsToChange = 100))
    )

    val samplesFinalChain =
      runFittingChain(shapeModel,
                      contourXY,
                      contourXZ,
                      bestSample2ndChain.fittingParameters,
                      xRay,
                      finalPoseAndShapeGenerator,
                      1000,
                      statusCallback)

//    DiagnosticPlots.pairs(samplesFinalChain.map(_.sample), new java.io.File("plots/pairs.png"))
//    DiagnosticPlots.tracePlot(samplesFinalChain, new java.io.File("plots/trace.png"))

    samplesFinalChain.maxBy(_.logProb).sample

  }

}
