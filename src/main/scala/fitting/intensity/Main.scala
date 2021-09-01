package fitting.intensity

import bmd.BMDMeasurement
import breeze.linalg.DenseVector
import com.typesafe.scalalogging.StrictLogging
import data.DataRepository.ResolutionLevel
import data.DataRepository.Vertebra.VertebraL1
import data.DirectoryBasedDataRepository
import rendering.{BarycentricInterpolatorRestrictedToVolume3D, DRRRenderer, XRay}
import scalismo.common.interpolation.{BarycentricInterpolator3D, TriangleMeshInterpolator3D}
import scalismo.geometry.{_3D, EuclideanVector3D, IntVector, Point3D}
import scalismo.io.StatisticalModelIO
import scalismo.mesh.{ScalarVolumeMeshField, TetrahedralMesh}
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess
import scalismo.transformations.Transformation3D
import scalismo.ui.api.{ObjectView, ScalismoUI}

import java.awt.Color
import java.io.File

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {
    scalismo.initialize()
    implicit val rng = scalismo.utils.Random(42)

//    val ui = ScalismoUI()

    val repo = DirectoryBasedDataRepository.of(VertebraL1)

    val intensityModelOrigPos = repo.intensityModel(ResolutionLevel.Coarse).get

    // We need to transform the model into the scanner
    // TODO Later, this will need to be done using the provided fit from the contour fitting
    val outerSurface = intensityModelOrigPos.domain.operations.getOuterSurface

    val centerOfMass = (outerSurface.pointSet.points.foldLeft(EuclideanVector3D(0, 0, 0))((acc, point) =>
      acc + point.toVector
    ) * (1.0 / outerSurface.pointSet.numberOfPoints)).toPoint

    val scannerTransform = Transformation3D(pt => pt + (Point3D(0, 0, 0) - centerOfMass))
    val domainInScanner = intensityModelOrigPos.domain.transform(scannerTransform)
    val intensityModel = DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float](domainInScanner,
                                                                                     intensityModelOrigPos.meanVector,
                                                                                     intensityModelOrigPos.variance,
                                                                                     intensityModelOrigPos.basisMatrix)

    val drrRenderer = new DRRRenderer(150, 200, 200, IntVector(32, 32), 50)

    // Generate ground truth - used for first synthetic experiments
    val groundTruthCoefficients = DenseVector.zeros[Double](intensityModel.rank)
    groundTruthCoefficients(0) = 2
    groundTruthCoefficients(1) = 2
    groundTruthCoefficients(2) = 2
    val groundTruthVolumeMesh = intensityModel
      .instance(groundTruthCoefficients)
      .interpolate(new BarycentricInterpolatorRestrictedToVolume3D[Float]())

    val targetImageYZ = drrRenderer.projectYZ(groundTruthVolumeMesh, 0)
    val targetImageXZ = drrRenderer.projectXZ(groundTruthVolumeMesh, 0)

    val initialFittingParameters =
      IntensityFittingParameters(DenseVector.zeros[Double](intensityModel.rank))

    def fittingStatusCallback(sample: IntensitySample, iterationNumber: Int): Unit = {

      if (iterationNumber % 1 == 0) {
        logger.info(s"in iteration $iterationNumber")
        logger.info(s"coefficients: ${sample.fittingParameters.intensityModelCoefficients}")
      }
    }

    // ground truth bmd

    val bestFitSample = IntensityFitting.fit(intensityModel,
                                             initialFittingParameters,
                                             targetImageXZ,
                                             targetImageYZ,
                                             numberOfSamples = 50,
                                             drrRenderer,
                                             fittingStatusCallback)

    // ground truth bmd
    val trabecularVolumeOrigPos = repo.referenceTrabecularVolume.get
    val trabecularVolume = trabecularVolumeOrigPos.transform(scannerTransform)

    val estimateGT = BMDMeasurement.estimateBMD(groundTruthVolumeMesh, trabecularVolume)
    println("ground truth estimate " + estimateGT)

    // bmd before fit
    val initialVolumeMesh = intensityModel
      .instance(initialFittingParameters.intensityModelCoefficients)
      .interpolate(new BarycentricInterpolatorRestrictedToVolume3D[Float]())

    val estimateInitial = BMDMeasurement.estimateBMD(initialVolumeMesh, trabecularVolume)
    println("initial estimate " + estimateInitial)

    // mbd after fit
    val bestFitVolumeMesh = intensityModel
      .instance(bestFitSample.fittingParameters.intensityModelCoefficients)
      .interpolate(new BarycentricInterpolatorRestrictedToVolume3D[Float]())

    val estimateBestFit = BMDMeasurement.estimateBMD(bestFitVolumeMesh, trabecularVolume)
    println("fit estimate " + estimateBestFit)
  }

}
