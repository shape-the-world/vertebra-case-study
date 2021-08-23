package rendering

import scalismo.common.interpolation.BarycentricInterpolator
import scalismo.common._
import scalismo.geometry.{Point, _3D}
import scalismo.mesh.boundingSpheres.{ClosestPointInTetrahedron, ClosestPointInTriangleOfTetrahedron, ClosestPointIsVertex, ClosestPointOnLine}
import scalismo.mesh.{TetrahedralMesh, TetrahedralMesh3DOperations, TriangleMesh}
import scalismo.numerics.ValueInterpolator

class BarycentricInterpolatorRestrictedToVolume3D[A: ValueInterpolator]() extends BarycentricInterpolator[_3D, A] {

    def signedDistanceImage(mesh : TriangleMesh[_3D]): Field[_3D, Float] = {
        val vnormals = mesh.vertexNormals

        def dist(pt: Point[_3D]): Float = {

            val PointWithId(closestPt, id) = mesh.pointSet.findClosestPoint(pt)

            val v = pt - closestPt
            val dist = v.norm
            val sign = Math.signum((v.normalize).dot(vnormals(id)))
            (dist * sign).toFloat
        }

        Field(EuclideanSpace3D, dist)
    }



    override protected val valueInterpolator: ValueInterpolator[A] = ValueInterpolator[A]

    override def interpolate(df: DiscreteField[_3D, TetrahedralMesh, A]): Field[_3D, A] = {

        val mesh = df.domain

        mesh.operations

        val outerSurface = mesh.operations.getOuterSurface
        val insideOutsideMap = signedDistanceImage(outerSurface)//outerSurface.operations.toBinaryImage
        val outerSurfaceBoundingBox = outerSurface.boundingBox

        val domain = Domain.fromPredicate((p: Point[_3D]) => outerSurfaceBoundingBox.isDefinedAt(p) && insideOutsideMap(p) <= 0)

        val meshOps: TetrahedralMesh3DOperations = mesh.operations

        def interpolateBarycentric(p: Point[_3D]): A = {

            meshOps.closestPointToVolume(p) match {
                case ClosestPointIsVertex(_, _, pId) => df(pId)
                case ClosestPointOnLine(_, _, pIds, bc) => ValueInterpolator[A].blend(df(pIds._1), df(pIds._2), bc)
                case ClosestPointInTriangleOfTetrahedron(_, _, tetId, triId, bc) =>
                    val triangle = mesh.tetrahedralization.tetrahedron(tetId).triangles(triId.id)
                    bc.interpolateProperty(df(triangle.ptId1), df(triangle.ptId2), df(triangle.ptId3))
                case ClosestPointInTetrahedron(_, _, tId, bc) =>
                    val tetrahedron = mesh.tetrahedralization.tetrahedron(tId)
                    bc.interpolateProperty(df(tetrahedron.ptId1),
                        df(tetrahedron.ptId2),
                        df(tetrahedron.ptId3),
                        df(tetrahedron.ptId4))
            }
//            df(mesh.pointSet.findClosestPoint(p).id)
        }

        Field(domain, interpolateBarycentric)
    }
}
