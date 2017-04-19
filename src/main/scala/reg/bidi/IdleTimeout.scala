package bidi

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.scaladsl.TestSource
import akka.testkit.TestKit
import org.testng.annotations.Test

class IdleTimeout extends TestKit(ActorSystem("test-system")) {

  implicit val materializer = ActorMaterializer()



  @Test(groups = Array("regression"))
  def idleTimeout(): Unit = {
    val idle = 2.seconds
    val settings = deriveServerSettings(idle)
    val pipe = BidiPipe[Message, Message](materializer)

    runServer(settings, pipe) {() =>

      // client
      new BidiClient()

      val pub = pipe.rightSink.runWith(TestSource.probe[Message])
      val sub = pipe.rightSource.runWith(TestSink.probe[Message])
      sub.request(1)

      // timeout -> complete
      sub.expectError()
      pub.expectCancellation()
    }
  }


  private def runServer(serverConfig: ServerSettings = ServerSettings(system), pipe: BidiPipe[Message, Message])(action: () => Unit): Unit = {

    // create a server and run it
    val bindingFuture = Http().bind("localhost", 9000, settings = serverConfig).to(Sink.foreach { connection =>

      // handle connection
      val handler = serverFlow(pipe.leftSink, pipe.leftSource, "/test")
      connection.handleWithSyncHandler(handler)

    }).run()
    val binding = Await.result(bindingFuture, 10.seconds)

    // run the test
    action()

    // shutdown
    Await.ready(binding.unbind(), 10.seconds)
  }

  /** handler flow for simple binary WebSocket messages upgrades any handshake */
  private def serverFlow(inSink: Sink[Message, Any], outSource: Source[Message, Any], path: String): HttpRequest => HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path(`path`), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessagesWithSinkSource(inSink, outSource)
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case _: HttpRequest => HttpResponse(404, entity = "Unknown resource!")
  }


  /** derive server settings from the default settings */
  private def deriveServerSettings(idleTimeout: FiniteDuration): ServerSettings = {
    val default = ServerSettings(system)
    val fastIdle = default.timeouts.withIdleTimeout(idleTimeout)
    default.withTimeouts(fastIdle)
  }

}
