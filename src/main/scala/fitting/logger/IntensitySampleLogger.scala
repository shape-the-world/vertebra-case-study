package fitting.logger

import fitting.contour.Sample
import fitting.intensity.IntensitySample
import scalismo.sampling.loggers.AcceptRejectLogger
import scalismo.sampling.{DistributionEvaluator, ProposalGenerator}

//
// Logger
//
case class IntensitySampleLogger() extends AcceptRejectLogger[IntensitySample] {
  private val numAccepted = collection.mutable.Map[String, Int]()
  private val numRejected = collection.mutable.Map[String, Int]()

  override def accept(current: IntensitySample,
                      sample: IntensitySample,
                      generator: ProposalGenerator[IntensitySample],
                      evaluator: DistributionEvaluator[IntensitySample]): Unit = {
    val numAcceptedSoFar = numAccepted.getOrElseUpdate(sample.generatedBy, 0)
    numAccepted.update(sample.generatedBy, numAcceptedSoFar + 1)
  }

  override def reject(current: IntensitySample,
                      sample: IntensitySample,
                      generator: ProposalGenerator[IntensitySample],
                      evaluator: DistributionEvaluator[IntensitySample]): Unit = {
    val numRejectedSoFar = numRejected.getOrElseUpdate(sample.generatedBy, 0)
    numRejected.update(sample.generatedBy, numRejectedSoFar + 1)
  }

  def acceptanceRatios(): Map[String, Double] = {
    val generatorNames = numRejected.keys.toSet.union(numAccepted.keys.toSet)
    val acceptanceRatios = for (generatorName <- generatorNames) yield {
      val total = (numAccepted.getOrElse(generatorName, 0)
        + numRejected.getOrElse(generatorName, 0)).toDouble
      (generatorName, numAccepted.getOrElse(generatorName, 0) / total)
    }
    acceptanceRatios.toMap
  }
}
