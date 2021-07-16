package modelling

import data.DataProvider
import data.DataProvider.Vertebra.VertebraL1
import scalismo.common.interpolation.{NearestNeighborInterpolator, TriangleMeshInterpolator3D}
import scalismo.geometry.{EuclideanVector, _3D}
import scalismo.io.StatisticalModelIO
import scalismo.kernels.{DiagonalKernel, GaussianKernel}
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, PointDistributionModel}
import scalismo.ui.api.ScalismoUI

object BuildGPModel {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val dataProvider = DataProvider.of(VertebraL1)

    dataProvider.gpModelDir.mkdirs()

    val referenceMesh = dataProvider.referenceMesh.get

    val k = DiagonalKernel(GaussianKernel[_3D](40) * 10, 3) +
      DiagonalKernel(GaussianKernel[_3D](20) * 5, 3) +
      DiagonalKernel(GaussianKernel[_3D](10) * 1, 3)

    val gp = GaussianProcess[_3D, EuclideanVector[_3D]](k)

    val lowRankGP = LowRankGaussianProcess.approximateGPCholesky(referenceMesh,
                                                                 gp,
                                                                 1e-2,
                                                                 TriangleMeshInterpolator3D[EuclideanVector[_3D]]())

    val gpModel = PointDistributionModel(referenceMesh, lowRankGP)
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(gpModel, dataProvider.gpModelFile).get
    val ui = ScalismoUI()
    ui.show(gpModel, " model" )
  }
}
