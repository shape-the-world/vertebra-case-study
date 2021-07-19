package modelling

import data.DataProvider
import data.DataProvider.Vertebra.VertebraL1
import scalismo.common.interpolation.BarycentricInterpolator3D
import scalismo.geometry.{EuclideanVector, _3D}
import scalismo.io.StatisticalModelIO
import scalismo.kernels.{DiagonalKernel, GaussianKernel}
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, PointDistributionModel}

/**
 * Builds a gp model using an analytically defined kernel.
 * It builds both a tetrahedral and a triangle mesh model.
 */
object BuildGPModel {

  // Warning, this might take quite some time to compute (e.g. > 15 minutes)
  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val dataProvider = DataProvider.of(VertebraL1)

    dataProvider.gpModelDir.mkdirs()

    val referenceTetrahedralMesh = dataProvider.referenceTetrahedralMesh.get
    val referenceTriangleMesh = dataProvider.referenceTriangleMesh.get

    val k = DiagonalKernel(GaussianKernel[_3D](40) * 10, 3) +
      DiagonalKernel(GaussianKernel[_3D](20) * 5, 3) +
      DiagonalKernel(GaussianKernel[_3D](10) * 1, 3)

    val gp = GaussianProcess[_3D, EuclideanVector[_3D]](k)

    val lowRankGP = LowRankGaussianProcess.approximateGPCholesky(referenceTetrahedralMesh,
                                                                 gp,
                                                                 1e-2,
                                                                 BarycentricInterpolator3D[EuclideanVector[_3D]]())

    val gpModelTetra = PointDistributionModel(referenceTetrahedralMesh, lowRankGP)
    StatisticalModelIO.writeStatisticalTetrahedralMeshModel3D(gpModelTetra, dataProvider.gpModelTetrahedralMeshFile).get

    val gpModelTriangle = PointDistributionModel(referenceTriangleMesh, lowRankGP)
    StatisticalModelIO.writeStatisticalTriangleMeshModel3D(gpModelTriangle, dataProvider.gpModelTriangleMeshFile).get
//    val ui = ScalismoUI()
//    val tetraGroup = ui.createGroup("tetrahedralMeshModel")
//    ui.show(tetraGroup, gpModelTetra, " model" )
//
//    val triangleGroup = ui.createGroup("triangleMeshModel")
//    ui.show(triangleGroup, gpModelTriangle, " model" )
  }
}
