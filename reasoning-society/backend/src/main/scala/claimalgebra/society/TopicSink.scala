package claimalgebra.society

import cats.effect.{IO, Ref}
import fs2.concurrent.Topic

/** An [[EventSink]] backed by an fs2 [[Topic]] (for live fan-out) PLUS a `Ref`-held replay log (for
  * catch-up), so the SSE transport can serve any number of browser subscribers — including ones
  * that connect after the game has already started.
  *
  * This is the fix for the adversarial-verify residue #3 — a fallible `EventSink.emit` must not
  * desync the transport from the committed log or wedge the single-writer LogActor. Backing the
  * sink with a Topic decouples the two ends:
  *   - the LogActor's `emit` stays a clean, non-blocking publish (`publish1` to a Topic whose
  *     subscribers use unbounded queues never backpressures the writer, and the Topic is never
  *     closed, so the publish cannot fail);
  *   - a slow or failed subscriber drains (or terminates) its OWN queue only — its failure never
  *     reaches the publisher, and per-subscriber SSE errors are contained in the route's own stream
  *     boundary. The log the LogActor commits and the log the Topic republishes cannot diverge.
  *
  * The replay log is why a late-joining browser is not blind: a `Topic` only delivers events
  * published AFTER a subscription, so without a catch-up snapshot a browser connecting mid-game
  * would miss the history (including the current question) and show an empty board. The sink
  * appends each event to the log before publishing; the `/events` route emits the snapshot, then
  * follows the Topic, deduped by `seq` (see [[SocietyRoutes]]).
  */
object TopicSink:

  /** Build a Topic, a replay log, and an [[EventSink]] that appends each event to the log and then
    * publishes it to the Topic. The SSE endpoint reads the log for catch-up and follows the Topic.
    */
  def make: IO[(EventSink, Topic[IO, Event], Ref[IO, Vector[Event]])] =
    for
      topic <- Topic[IO, Event]
      logRef <- Ref.of[IO, Vector[Event]](Vector.empty)
    yield
      val sink: EventSink = event => logRef.update(_ :+ event) *> topic.publish1(event).void
      (sink, topic, logRef)
