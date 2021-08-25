package modelling

import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import scalismo.common.interpolation.BarycentricInterpolator3D
import scalismo.geometry.{_3D, EuclideanVector}
import scalismo.kernels.{DiagonalKernel, GaussianKernel}
import scalismo.statisticalmodel.{GaussianProcess, LowRankGaussianProcess, PointDistributionModel}

import scala.util.{Failure, Success}

/**
 * Builds a gp model using an analytically defined kernel.
 * It builds both a tetrahedral and a triangle mesh model.
 */
object BuildGPModel extends StrictLogging {

  // Warning, this might take quite some time to compute (e.g. > 15 minutes)
  def main(args: Array[String]): Unit = {
    scalismo.initialize()

    val resolutionLevel = ResolutionLevel.Coarse
    val dataRepository = DirectoryBasedDataRepository.of(VertebraL1)

    val referenceTetrahedralMesh = dataRepository.referenceTetrahedralMesh(resolutionLevel).get
    val referenceTriangleMesh = dataRepository.referenceTriangleMesh(resolutionLevel).get

    val k = DiagonalKernel(GaussianKernel[_3D](40) * 10, 3) +
      DiagonalKernel(GaussianKernel[_3D](20) * 5, 3) +
      DiagonalKernel(GaussianKernel[_3D](10) * 1, 3)

    val gp = GaussianProcess[_3D, EuclideanVector[_3D]](k)

    val lowRankGP = LowRankGaussianProcess.approximateGPCholesky(referenceTetrahedralMesh,
                                                                 gp,
                                                                 1e-2,
                                                                 BarycentricInterpolator3D[EuclideanVector[_3D]]())

    val gpModelTetra = PointDistributionModel(referenceTetrahedralMesh, lowRankGP)
    dataRepository.saveGpModelTetrahedralMesh(gpModelTetra, resolutionLevel) match {
      case Success(_) => logger.info("Successfully saved tetrahedral mesh")
      case Failure(exception) =>
        logger.error("An error occurred while saving tetrahedral mesh: " + exception.getMessage)
    }

    val gpModelTriangle = PointDistributionModel(referenceTriangleMesh, lowRankGP)
    dataRepository.saveGpModelTriangleMesh(gpModelTriangle, resolutionLevel) match {
      case Success(_)         => logger.info("Successfully saved triangle mesh")
      case Failure(exception) => logger.error("An error occurred while saving triangle mesh: " + exception.getMessage)
    }
//    val ui = ScalismoUI()
//    val tetraGroup = ui.createGroup("tetrahedralMeshModel")
//    ui.show(tetraGroup, gpModelTetra, " model" )
//
//    val triangleGroup = ui.createGroup("triangleMeshModel")
//    ui.show(triangleGroup, gpModelTriangle, " model" )
  }
}
