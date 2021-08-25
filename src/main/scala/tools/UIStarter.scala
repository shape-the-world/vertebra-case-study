package tools

import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.{DataRepository, DirectoryBasedDataRepository}
import scalismo.ui.api.ScalismoUI

object UIStarter extends App {

  val ui = ScalismoUI()

  val resolutionLevel = ResolutionLevel.Coarse

  val intensityModel = DirectoryBasedDataRepository.of(VertebraL1).intensityModel(resolutionLevel).get
  implicit val rng = scalismo.utils.Random(42)
  val sampleGroup = ui.createGroup("samples")

  val meanIntensity = intensityModel.sample()
  ui.show(sampleGroup, meanIntensity, "mean")
  for (i <- 0 until 10) {
    val sample = intensityModel.sample()

    ui.show(sampleGroup, sample, s"sample-$i")
  }
}
