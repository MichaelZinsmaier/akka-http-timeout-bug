package bidi

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

import reg.helpers.DelayCancellationFlow
import reg.helpers.MyMonitorFlow

class BidiClient {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val incoming = Sink.ignore
  val outgoing = Source.maybe[Message]

  val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9000/test"))

  val (upgradeResponse, closed) =
    outgoing
      .via(new MyMonitorFlow("out"))
      .via(new DelayCancellationFlow[Message](60.seconds))
      .viaMat(webSocketFlow)(Keep.right)
      .viaMat(new DelayCancellationFlow[Message](60.seconds))(Keep.left)
      .viaMat(new MyMonitorFlow("in"))(Keep.left)
      .toMat(incoming)(Keep.both)
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
