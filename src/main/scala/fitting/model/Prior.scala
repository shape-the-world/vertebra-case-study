package fitting.model

import scalismo.geometry.{_3D, EuclideanVector3D, Point}
import scalismo.transformations.{Rotation, Rotation3D, Translation, Translation3D}

object Prior {

  def translationPriorX(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Uniform(-50, 50)(rng.breezeRandBasis) //(0.0, 20.0)
  def translationPriorY(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Uniform(-50, 50)(rng.breezeRandBasis) //(0.0, 20.0)
  def translationPriorZ(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Uniform(-50, 50)(rng.breezeRandBasis) //(0.0, 20.0)

  def rotationPriorPhi(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Gaussian(0, 0.1)(rng.breezeRandBasis)
  def rotationPriorTheta(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Gaussian(0, 0.1)(rng.breezeRandBasis)

  def rotationPriorPsi(implicit rng: scalismo.utils.Random) =
    breeze.stats.distributions.Gaussian(0, 0.1)(rng.breezeRandBasis)

  def sampleTranslation()(implicit rng: scalismo.utils.Random): Translation[_3D] = {
    Translation3D(EuclideanVector3D(translationPriorX.sample(), translationPriorY.sample(), translationPriorZ.sample()))
  }

  def sampleRotation(rotationCenter: Point[_3D])(implicit rng: scalismo.utils.Random): Rotation[_3D] = {
    Rotation3D(rotationPriorPhi.sample(), rotationPriorTheta.sample(), rotationPriorPsi.sample(), rotationCenter)
  }

}
