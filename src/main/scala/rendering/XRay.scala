package rendering

import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.common.{PointId, UnstructuredPoints3D}
import scalismo.geometry.{Point, Point3D, _3D}
import scalismo.mesh._
import scalismo.transformations.{Transformation, Transformation3D}

class XRay(val sensorDistance: Int, val sensorWidth: Int, val sensorHeight: Int) {

  val sourcePosition = -sensorDistance / 2
  val sensorPlanePosition = sensorDistance / 2

  def perspectiveProjectionXYPlane: Transformation[_3D] = {

    val transformationMatrix = DenseMatrix.zeros[Double](4, 4)
    transformationMatrix(0, 0) = sensorDistance
    transformationMatrix(1, 1) = sensorDistance
    transformationMatrix(2, 2) = sensorDistance
    transformationMatrix(3, 2) = 1

    def project(p: Point[_3D]): Point[_3D] = {
      val pointInHomogeneousCoords = DenseVector.zeros[Double](4)
      pointInHomogeneousCoords(3) = 1
      pointInHomogeneousCoords(0 until 3) := DenseVector(p.x, p.y, p.z - sourcePosition)

      val projectedPointHomogeneousCoords = transformationMatrix * pointInHomogeneousCoords
      val z = projectedPointHomogeneousCoords(3)

      Point3D(
        projectedPointHomogeneousCoords(0) / z,
        projectedPointHomogeneousCoords(1) / z,
        projectedPointHomogeneousCoords(2) / z + sourcePosition
      )
    }

    Transformation3D(project)
  }

  def perspectiveProjectionXZPlane: Transformation[_3D] = {
    val transformationMatrix = DenseMatrix.zeros[Double](4, 4)
    transformationMatrix(0, 0) = sensorDistance
    transformationMatrix(1, 1) = sensorDistance
    transformationMatrix(2, 2) = sensorDistance
    transformationMatrix(3, 1) = 1

    def project(p: Point[_3D]): Point[_3D] = {
      val pointInHomogeneousCoords = DenseVector.zeros[Double](4)

      // as the perspective projection matrix assumes the source in the origin, we translate the object
      pointInHomogeneousCoords(3) = 1
      pointInHomogeneousCoords(0 until 3) := DenseVector(p.x, p.y - sourcePosition, p.z)

      val projectedPointHomogeneousCoords = transformationMatrix * pointInHomogeneousCoords
      val y = projectedPointHomogeneousCoords(3)
      Point3D(
        projectedPointHomogeneousCoords(0) / y,
        projectedPointHomogeneousCoords(1) / y + sourcePosition,
        projectedPointHomogeneousCoords(2) / y
      )

    }

    Transformation3D(project)
  }

  def projectMeshXY(mesh: TriangleMesh[_3D]): TriangleMesh[_3D] = {
    mesh.transform(perspectiveProjectionXYPlane)
  }

  def projectMeshXZ(mesh: TriangleMesh[_3D]): TriangleMesh[_3D] = {
    mesh.transform(perspectiveProjectionXZPlane)
  }

  def projectContoursXY(mesh: TriangleMesh[_3D]): LineMesh[_3D] = {
    val projectedMesh = projectMeshXY(mesh)
    findOuterContour(projectedMesh)
  }

  def projectContoursXZ(mesh: TriangleMesh[_3D]): LineMesh[_3D] = {
    val projectedMesh = projectMeshXZ(mesh)
    findOuterContour(projectedMesh)
  }

  /**
   *
   * @param projectedMesh : A mesh, which was already projected on a 2D plane
   * @return Outer contour of the mesh
   */
  private def findOuterContour(projectedMesh: TriangleMesh[_3D]): LineMesh[_3D] = {

    // stores for each point id the orientation, with respect to to the sensor center,
    // which is assumed to be the origin (0, 0, 0)
    val orientationMap = projectedMesh.pointSet.pointsWithId.map {
      case (point, pointId) =>
        val e = point.toVector.normalize
        val n = projectedMesh.vertexNormals(pointId).normalize
        (pointId, Math.signum(n.dot(e)).toInt) // store -1 or 1 dependent on orientation
    }.toMap

    val triangulation = projectedMesh.triangulation

    def isContourPoint(pointId: PointId): Boolean = {
      val orientation = orientationMap(pointId)
      val point = projectedMesh.pointSet.point(pointId)
      val orientationOfAdjecentPoints = triangulation.adjacentPointsForPoint(pointId).map(id => orientationMap(id))

      lazy val flippedOrientation = orientationOfAdjecentPoints.exists(_ != orientation)

      lazy val inImage = point.x > -sensorWidth / 2 && point.x < (sensorWidth / 2) &&
        point.y > -sensorHeight / 2 && point.y < sensorHeight / 2

      flippedOrientation && inImage
    }

    val contourPids = orientationMap.keys.filter(pointId => isContourPoint(pointId)).toIndexedSeq

    // We will create a new mesh with new point ids. For this we need to have a mapping between the old and the new
    // pointIds
    val oldNewPointIdMap = contourPids.zipWithIndex.map { case (oldPtId, newPtId) => (oldPtId, PointId(newPtId)) }.toMap

    // We create all possible lines connecting the contours. We assume that two lines are connected on the contour,
    // if they were connected in the original mesh topology.
    val lines = (for (contourPointPid <- contourPids) yield {

      val adjecentPtIds = triangulation
        .adjacentPointsForPoint(contourPointPid)
        .filter(adjecentId => contourPids.contains(adjecentId) && adjecentId.id > contourPointPid.id)
      adjecentPtIds.map(adjecentPtId => LineCell(oldNewPointIdMap(contourPointPid), oldNewPointIdMap(adjecentPtId)))
    }).flatten
    val contourPoints = contourPids.map(id => projectedMesh.pointSet.point(id))
    LineMesh3D(UnstructuredPoints3D(contourPoints), LineList(lines))
  }

}
