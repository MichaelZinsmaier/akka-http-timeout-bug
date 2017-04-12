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
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

object WsServer {

  def main(args: Array[String]): Unit = {
    new WsServer()
  }
}

class WsServer {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val requestHandler: HttpRequest => HttpResponse = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path("/greeter"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
        case None          => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val greeterWebSocketService =
    Flow[Message]
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

  val bind = Http().bindAndHandleSync(requestHandler, "localhost", 9000)

  bind.onComplete {
    case Success(_) => println("server bound and running")
    case _ => println("problems in server")
  }
}
