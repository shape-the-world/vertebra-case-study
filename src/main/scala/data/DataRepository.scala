package data

import data.DataRepository.{CaseId, Stage, Vertebra}
import scalismo.geometry.{Landmark, _3D}
import scalismo.image.DiscreteImage
import scalismo.mesh.{TetrahedralMesh, TriangleMesh}
import scalismo.statisticalmodel.{DiscreteLowRankGaussianProcess, PointDistributionModel}

import scala.util.Try

/**
 * This trait provides all method needed to interact with the data. The basic principle is that
 * no code should ever construct a file path or read a file directly, but always go via the
 * DataRepository. This makes the pipeline code easy to change and at the same contains all the
 * information about data organization in one place.
 *
 */
trait DataRepository {

  /**
   *  The verteba, whose data the data provider manages.
   */
  def vertebra: Vertebra

  /** Each data item is identified by a caseId. This sequence holds all CaseIds that are
   * managed by this Data provider.
   */
  def caseIds: Seq[CaseId]

  /** The reference tetrahedral mesh is the mesh on which we base all the modelling. */
  def referenceTetrahedralMesh: Try[TetrahedralMesh[_3D]]

  // We obtain the reference triangle mesh from the reference tetrahedral mesh.
  def referenceTriangleMesh: Try[TriangleMesh[_3D]] = referenceTetrahedralMesh.map(_.operations.getOuterSurface)

  def referenceLandmarks: Try[Seq[Landmark[_3D]]]

  def referenceVolume: Try[DiscreteImage[_3D, Short]]
  def referenceLabelMap: Try[DiscreteImage[_3D, Short]]

  def triangleMesh(stage: Stage, id: CaseId): Try[TriangleMesh[_3D]]

  def saveTriangleMesh(stage : Stage, id: CaseId, mesh : TriangleMesh[_3D]) : Try[Unit]

  def tetrahedralMesh(stage: Stage, id: CaseId): Try[TetrahedralMesh[_3D]]

  def saveTetrahedralMesh(stage : Stage, id: CaseId, mesh : TetrahedralMesh[_3D]) : Try[Unit]

  def landmarks(stage: Stage, id: CaseId): Try[Seq[Landmark[_3D]]]

  def saveLandmarks(stage: Stage, id: CaseId, landmarks : Seq[Landmark[_3D]]): Try[Unit]

  def labelMap(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]]

  def saveLabelMap(stage : Stage, id : CaseId, labelMap : DiscreteImage[_3D, Short]) : Try[Unit]

  def volume(stage: Stage, id: CaseId): Try[DiscreteImage[_3D, Short]]

  def saveVolume(stage : Stage, id : CaseId, volume : DiscreteImage[_3D, Short]) : Try[Unit]

  def gpModelTriangleMesh: Try[PointDistributionModel[_3D, TriangleMesh]]

  def saveGpModelTriangleMesh(gpModel : PointDistributionModel[_3D, TriangleMesh]) : Try[Unit]

  def gpModelTetrahedralMesh: Try[PointDistributionModel[_3D, TetrahedralMesh]]

  def saveGpModelTetrahedralMesh(gpModel : PointDistributionModel[_3D, TetrahedralMesh]) : Try[Unit]

  def ssm: Try[PointDistributionModel[_3D, TriangleMesh]]

  def saveSSM(ssm : PointDistributionModel[_3D, TriangleMesh]) : Try[Unit]

  def intensityModel : Try[DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]]

  def saveIntensityModel(intensityModel : DiscreteLowRankGaussianProcess[_3D, TetrahedralMesh, Float]) : Try[Unit]

}

object DataRepository {

  /**
   * This trait is used to specify the individual vertebrae
   */
  sealed trait Vertebra {
    def desc: String
    def label: Int
  }

  object Vertebra {
    case object VertebraL1 extends Vertebra {
      override val desc = "L1"
      override val label: Int = 23
    }
  }

  /**
   * An object is usually represented in different stages during the
   * processing. It might start in its initial stage, is then aligned and finally registered.
   * This trait, together with the accompanying case objects are used to represent this Stage.
   */
  sealed trait Stage {
    def dirname: String
  }
  object Stage {
    case object Initial extends Stage {
      override val dirname = "initial"
    }

    case object Aligned extends Stage {
      override val dirname = "aligned"
    }

    case object Registered extends Stage {
      override val dirname = "registered"
    }
  }

  /** Specifies an individual case   */
  case class CaseId(value: String)
}
