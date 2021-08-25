//package fitting
//
//import breeze.linalg.DenseVector
//import com.typesafe.scalalogging.StrictLogging
//import fitting.FittingParameters.PoseParameters
//import rendering.XRay
//import scalismo.common.interpolation.TriangleMeshInterpolator3D
//import scalismo.geometry.{EuclideanVector3D, Point3D}
//import scalismo.io.{MeshIO, StatisticalModelIO}
//import scalismo.transformations.Translation3D
//import scalismo.ui.api.{ObjectView, ScalismoUI}
//
//import java.awt.Color
//import java.io.File
//
//object Main extends StrictLogging {
//
//  def main(args: Array[String]): Unit = {
//    scalismo.initialize()
//    implicit val rng = scalismo.utils.Random(42)
//
//    val modelFullRes = StatisticalModelIO
//      .readStatisticalTriangleMeshModel3D(
//        new File("data/scapula_modelbest.h5")
//      )
//      .get
//    val model = modelFullRes
//      .newReference(modelFullRes.reference.operations.decimate(1000), TriangleMeshInterpolator3D())
//
//    //val sphere = MeshIO.readMesh(new File("data/sphere-hires.ply")).get.transform(Translation3D(EuclideanVector3D(0, 0, 2)))
//
//    val centerOfMass = (model.reference.pointSet.points.foldLeft(EuclideanVector3D(0, 0, 0))((acc, point) =>
//      acc + point.toVector
//    ) * (1.0 / model.reference.pointSet.numberOfPoints)).toPoint
//
//    val translationToScanner = Point3D(0, 0, 0) - centerOfMass
//    val xRay = new XRay(sensorDistance = 400, sensorWidth = 15000, sensorHeight = 15000)
//    val initialShapeCoeffs = DenseVector.ones[Double](model.rank)
//    val groundTruthPose = PoseParameters(
//      translationParameters = EuclideanVector3D(translationToScanner.x, translationToScanner.y, translationToScanner.z),
//      rotationAngles = (0, 3.14 / 2, 0),
//      centerOfMass
//    )
//    val targetMesh =
//      model.reference
//        .transform(FittingParameters.fullTransformation(model, groundTruthPose, initialShapeCoeffs))
//
//    //val targetMesh = MeshIO.readMesh(new java.io.File("./data/sphere.ply")).get
//    val ui = ScalismoUI()
//    val xRayScannerGroup = ui.createGroup("x-ray-scanner")
//
////    ui.show(xRayScannerGroup, IndexedSeq(Point3D(0, 0, xrayParameters.sensorDistance)), "sensorPlane")
//    val modelGroup = ui.createGroup("modelgroup")
//    val modelView = ui.show(modelGroup, model, "ssm")
//
//    val modelBoundaryGroup = ui.createGroup("modelBoundary")
//
//    val targetGroup = ui.createGroup("targetgroup")
//    val targetMeshView = ui.show(targetGroup, targetMesh, "target mesh")
//    targetMeshView.color = Color.RED
//
//    val boundaryXY = xRay.projectContoursXY(targetMesh)
//    val boundaryXYView = ui.show(targetGroup, boundaryXY, "boundary-xy")
//    boundaryXYView.color = Color.RED
//
//    val boundaryXZ = xRay.projectContoursXZ(targetMesh)
//    val boundaryXZView = ui.show(targetGroup, boundaryXZ, "boundary-xz")
//    boundaryXZView.color = Color.BLUE
//
//    ui.show(xRayScannerGroup, IndexedSeq(Point3D(0, 0, 0)), "source")
//
//    val initialPoseParameters = PoseParameters(
//      translationParameters = EuclideanVector3D(translationToScanner.x, translationToScanner.y, translationToScanner.z),
//      rotationAngles = (3.14 / 2, 3.14 / 2, 3.4 / 2),
//      centerOfRotation = centerOfMass
//    )
//
//    val initialFittingParameters =
//      FittingParameters(initialPoseParameters, DenseVector.zeros[Double](model.rank))
//
//    def fittingStatusCallback(sample: Sample, iterationNumber: Int): Unit = {
//
//      if (iterationNumber % 100 == 0) {
//        logger.info(s"in iteration $iterationNumber")
//        modelView.shapeModelTransformationView.shapeTransformationView.coefficients =
//          sample.fittingParameters.shapeCoefficients
//        modelView.shapeModelTransformationView.poseTransformationView.transformation =
//          FittingParameters.poseTransformation(sample.fittingParameters.poseParameters)
//
//        ui.filter(modelBoundaryGroup, (v: ObjectView) => true).foreach(_.remove())
//        val fittedBoundaryXY = xRay.projectContoursXY(modelView.referenceView.transformedTriangleMesh)
//
//        val boundaryViewXY = ui.show(modelBoundaryGroup, fittedBoundaryXY, "fitted boundary")
//        boundaryViewXY.color = Color.WHITE
//
//        val fittedBoundaryXZ = xRay.projectContoursXZ(modelView.referenceView.transformedTriangleMesh)
//
//        val boundaryViewXZ = ui.show(modelBoundaryGroup, fittedBoundaryXZ, "fitted boundary")
//        boundaryViewXZ.color = Color.WHITE
//      }
//    }
//
//    ContourFitting.fit(model, boundaryXY, boundaryXZ, initialFittingParameters, xRay, fittingStatusCallback _)
//
//  }
//
//}
