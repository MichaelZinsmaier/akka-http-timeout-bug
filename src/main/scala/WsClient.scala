import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.ActorMaterializer
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

object WsClient {

  def main(args: Array[String]): Unit = {
    new WsClient()
  }

  /** derive server settings from the default settings */
  private def deriveClientSettings(idleTimeout: FiniteDuration)(implicit system: ActorSystem): ClientConnectionSettings = {
    val default = ClientConnectionSettings(system)
    default.withIdleTimeout(idleTimeout)
  }
}

class WsClient {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher


  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case message: TextMessage.Strict => println(message.text)
    }

  // send this as a message over the WebSocket
  val outgoing = Source
    .repeat(TextMessage("marvin"))
    .throttle(1, FiniteDuration(10, TimeUnit.SECONDS), 1, ThrottleMode.shaping)

  // flow to use (note: not re-usable!)
  val clientSettings = WsClient.deriveClientSettings(FiniteDuration(30, TimeUnit.SECONDS))
  val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9000/greeter"), settings = clientSettings)

  val (upgradeResponse, closed) =
    outgoing
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()

  val connected = upgradeResponse.flatMap { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Future.successful(Done)
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  // in a real application you would not side effect here
  connected.onComplete(println)
  closed.foreach(_ => println("closed"))
}
