package reg.helpers

import akka.stream.Attributes
import akka.stream.FlowShape
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler

class MyMonitorFlow[T](name: String) extends GraphStage[FlowShape[T, T]] {
  val in = Inlet[T]("FlowMonitor.in")
  val out = Outlet[T]("FlowMonitor.out")
  val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): (GraphStageLogic) = {

    val logic: GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {

      def onPush(): Unit = {
        val msg = grab(in)
        push(out, msg)

        println("## M ## " + name + " " + msg)
      }

      override def onUpstreamFinish(): Unit = {
        super.onUpstreamFinish()
        println("## M ## " + name + " " + "upstream finished")
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        super.onUpstreamFailure(ex)
        println("## M ## " + name + " " + "upstream failure " + ex)
      }

      def onPull(): Unit = pull(in)

      override def onDownstreamFinish(): Unit = {
        super.onDownstreamFinish()
        println("## M ## " + name + " " + "downstream finished")
      }

      setHandler(in, this)
      setHandler(out, this)

      override def toString = "MonitorFlowLogic"
    }

    logic
  }

  override def toString = "MonitorFlow"
}
