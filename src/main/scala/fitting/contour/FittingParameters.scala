package fitting.contour

import breeze.linalg.DenseVector
import fitting.contour.FittingParameters.PoseParameters
import scalismo.common.interpolation.TriangleMeshInterpolator3D
import scalismo.geometry.EuclideanVector
import scalismo.geometry.Point
import scalismo.geometry._3D
import scalismo.mesh.TriangleMesh
import scalismo.registration.GaussianProcessTransformation3D
import scalismo.statisticalmodel.PointDistributionModel
import scalismo.transformations.{Rotation3D, Transformation, Transformation3D, Translation3D, TranslationAfterRotation, TranslationAfterRotation3D};

case class FittingParameters(poseParameters: PoseParameters, shapeCoefficients: DenseVector[Double])

object FittingParameters {
  case class PoseParameters(translationParameters: EuclideanVector[_3D],
                            rotationAngles: (Double, Double, Double),
            centerOfRotation: Point[_3D])

    def poseTransformation(poseParameters: PoseParameters): TranslationAfterRotation[_3D] = {
        val (phi, theta, psi) = poseParameters.rotationAngles
        val rotation = Rotation3D(phi, theta, psi, center = poseParameters.centerOfRotation)
        val translation = Translation3D(poseParameters.translationParameters)
        TranslationAfterRotation3D(translation, rotation)
    }

    def fullTransformation(pdm: PointDistributionModel[_3D, TriangleMesh],
                           poseParameters: PoseParameters,
                           shapeCoefficients: DenseVector[Double]): Transformation[_3D] = {
        val gp = pdm.gp.interpolate(TriangleMeshInterpolator3D())

        val gpTransformation = GaussianProcessTransformation3D(gp, shapeCoefficients)
        Transformation3D(gpTransformation.andThen(poseTransformation(poseParameters)))
    }
}
