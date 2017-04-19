import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.TextMessage.Strict
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink

import reg.helpers.DelayCancellationFlow
import reg.helpers.MyMonitorFlow

object WsServer {

  def main(args: Array[String]): Unit = {
    new WsServer()
  }

  /** derive server settings from the default settings */
  private def deriveServerSettings(idleTimeout: FiniteDuration)(implicit system: ActorSystem): ServerSettings = {
    val default = ServerSettings(system)
    val fastIdle = default.withIdleTimeout(idleTimeout)
    default.withTimeouts(fastIdle)
  }
}

class WsServer {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val requestHandler: HttpRequest => HttpResponse = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/greeter"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => {
          upgrade.handleMessages(greeterWebSocketService)
        }
        case None          => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val greeterWebSocketService =
    Flow[Message]
      .via(new MyMonitorFlow("in"))
      .via(new DelayCancellationFlow(FiniteDuration(60, TimeUnit.SECONDS)))
      .mapConcat {
        case tm: TextMessage.Strict => {
          val text = tm.text
          println(s"request from $text")
          TextMessage.Strict(s"Hello $text") :: Nil
        }
        case bm: BinaryMessage =>
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
      .via(new DelayCancellationFlow(FiniteDuration(60, TimeUnit.SECONDS)))
      .via(new MyMonitorFlow("out"))

  val serverSettings = WsServer.deriveServerSettings(FiniteDuration(5, TimeUnit.SECONDS))
  val bind = Http().bindAndHandleSync(requestHandler, "localhost", 9000, settings = serverSettings)

  bind.onComplete {
    case Success(_) => println("server bound and running")
    case _ => println("problems in server")
  }
}
