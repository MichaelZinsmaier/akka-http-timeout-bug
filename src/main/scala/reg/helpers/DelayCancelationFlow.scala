package reg.helpers

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import akka.actor.Cancellable
import akka.stream.Attributes
import akka.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import akka.stream.stage.StageLogging

class DelayCancellationFlow[T](cancelAfter: Duration) extends SimpleLinearGraphStage[T] {
  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with ScheduleSupport with InHandler with OutHandler with StageLogging {
    setHandlers(in, out, this)

    def onPush(): Unit = push(out, grab(in)) // using `passAlong` was considered but it seems to need some boilerplate to make it work
    def onPull(): Unit = pull(in)

    var timeout: Option[Cancellable] = None

    override def onDownstreamFinish(): Unit = {
      cancelAfter match {
        case finite: FiniteDuration ⇒
          timeout = Some {
            scheduleOnce(finite) {
              log.debug(s"Stage was canceled after delay of $cancelAfter")
              completeStage()
            }
          }
        case _ ⇒ // do nothing
      }

      // don't pass cancellation to upstream but keep pulling until we get completion or failure
      setHandler(
        in,
        new InHandler {
          if (!hasBeenPulled(in)) pull(in)

          def onPush(): Unit = {
            grab(in) // ignore further elements
            pull(in)
          }
        }
      )
    }



    override def postStop(): Unit = timeout match {
      case Some(x) ⇒ x.cancel()
      case None    ⇒ // do nothing
    }
  }
}


trait ScheduleSupport { self: GraphStageLogic ⇒
  /**
    * Schedule a block to be run once after the given duration in the context of this graph stage.
    */
  def scheduleOnce(delay: FiniteDuration)(block: ⇒ Unit): Cancellable =
    materializer.scheduleOnce(delay, new Runnable { def run() = runInContext(block) })

  def runInContext(block: ⇒ Unit): Unit = getAsyncCallback[AnyRef](_ ⇒ block).invoke(null)
}
