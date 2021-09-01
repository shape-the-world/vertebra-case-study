package tools

import data.DataRepository.{ResolutionLevel, Stage}
import data.DataRepository.Vertebra.VertebraL1
import data.{DataRepository, DirectoryBasedDataRepository}
import scalismo.ui.api.ScalismoUI

import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

object UIStarter extends App {

  val ui = ScalismoUI()

  val resolutionLevel = ResolutionLevel.Coarse

  val repo = DirectoryBasedDataRepository.of(VertebraL1)

//  for (id <- repo.caseIds.par) {
//    val volume = repo.volume(Stage.Aligned, id).get
//    val values = volume.values.toIndexedSeq
//    println(s"$id: minmax : " + (values.min, values.max))
//  }

  val intensityModel = repo.intensityModel(resolutionLevel).get
  implicit val rng = scalismo.utils.Random(42)
  val sampleGroup = ui.createGroup("samples")

  val meanIntensity = intensityModel.sample()
  ui.show(sampleGroup, meanIntensity, "mean")
  for (i <- 0 until 10) {
    val sample = intensityModel.sample()

    ui.show(sampleGroup, sample, s"sample-$i")
  }
}
