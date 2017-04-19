package bidi

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source



object BidiPipe {

  /** Creates a new bidi pipe.
    * @param materializer the actor materializer
    */
  def apply[T1, T2](materializer: ActorMaterializer): BidiPipe[T1, T2] = new BidiPipe[T1, T2](materializer)

  /** Creates a binary unidirectional pipe with one source and one sink at each end.
    * Data will not start to flow until both the source and the sink are
    * plugged into a sink and a source and materialized.
    * @param materializer the actor materializer
    * @return (source, sink) of a unidirectional stream pipe
    */
  private def createPipe[T](materializer: ActorMaterializer): (Source[T, NotUsed], Sink[T, NotUsed]) = {
    val (sub, pub) = Source.asSubscriber[T].toMat(Sink.asPublisher[T](fanout = false))(Keep.both).run()(materializer)
    (Source.fromPublisher(pub), Sink.fromSubscriber(sub))
  }

}


/** A generic bidirectional stream pipe with two endpoints with one source a one sink each.
  * Data will not start to flow until both the source and the sink of that direction are
  * plugged into a sink and a source and materialized.
  * @param materializer the actor materializer
  */
class BidiPipe[T1, T2](materializer: ActorMaterializer) {

  /*
   * <- leftSource <-- +----------+ <--- rightSink <-
   *                   | BidiPipe |
   * -> leftSink ----> +----------+ --> rightSource ->
   *
   */

  /** Source and sink of the pipe in the direction: left source <- right sink  */
  private val rightToLeft: (Source[T1, NotUsed], Sink[T1, NotUsed]) = BidiPipe.createPipe(materializer)

  /** Source and sink of the pipe in the direction: left sink -> right source */
  private val leftToRight: (Source[T2, NotUsed], Sink[T2, NotUsed]) = BidiPipe.createPipe(materializer)

  /** The left source of the bidi pipe. */
  val leftSource: Source[T1, NotUsed] = rightToLeft._1

  /** The left sink of the bidi pipe. */
  val leftSink: Sink[T2, NotUsed] = leftToRight._2

  /** The right source of the bidi pipe. */
  val rightSource: Source[T2, NotUsed] = leftToRight._1

  /** The right sink of the bidi pipe. */
  val rightSink: Sink[T1, NotUsed] = rightToLeft._2

}
