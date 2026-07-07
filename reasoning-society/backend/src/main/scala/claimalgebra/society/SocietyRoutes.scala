package claimalgebra.society

import cats.effect.{IO, Ref}
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.{EntityDecoder, HttpRoutes, ServerSentEvent}

/** The HTTP transport for one reasoning-society game (Build 3 slice 3a):
  *   - `GET  /events` — a `text/event-stream` (SSE) that streams the ordered event log as JSON, one
  *     [[Wire]] object per `data:` frame. Each browser is an independent Topic subscriber with its
  *     own error boundary, so a dropped or failing browser terminates only its own stream and never
  *     touches the game or another subscriber (residue #3, realized with [[TopicSink]]).
  *   - `POST /answer` — the human oracle's reply `{questionId, answer}` (`answer` in
  *     `yes|no|unknown`). Untrusted input: a malformed body is a 400 (never a 500), and a valid
  *     body completes the pending [[RemoteOracle]] question so the game proceeds.
  *   - `POST /start` — restart the game (the injected `startGame`: the [[GameSupervisor]] cancels
  *     the running game, resets the shared log/oracle, and forks one fresh game — serialized, so
  *     two `POST /start`s cannot stack games).
  *   - `GET  /` — a plain-text landing describing the endpoints (the React viewer is slice 3b).
  */
object SocietyRoutes:

  // The boundary decoder: JSON -> AnswerCommand, re-validated to the domain types (a bad token or a
  // blank id is a DecodingFailure, surfaced as a 400 below).
  private given answerEntityDecoder: EntityDecoder[IO, AnswerCommand] = jsonOf[IO, AnswerCommand]

  private val landing: String =
    """reasoning-society transport (Build 3 slice 3a)
      |  GET  /events   text/event-stream — the live event log as JSON frames
      |  POST /answer   {"questionId": "...", "answer": "yes|no|unknown"}
      |  POST /start    restart the game (fresh log, one game)
      |""".stripMargin

  /** Wire the routes.
    *
    * @param topic
    *   the event log Topic every SSE subscriber reads.
    * @param oracle
    *   the human/oracle seam `POST /answer` completes.
    * @param startGame
    *   restart the single game (the [[GameSupervisor]]'s serialized cancel → reset → fork); returns
    *   once the fresh game has been forked.
    */
  def apply(
      topic: Topic[IO, Event],
      logRef: Ref[IO, Vector[Event]],
      oracle: RemoteOracle,
      startGame: IO[Unit]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "events" =>
        // Catch-up then follow: subscribe FIRST (so nothing is missed between the snapshot and the
        // subscription), read the committed log for the game so far, then emit snapshot ++ live,
        // deduped by seq — a browser joining mid-game gets the full history, current question and all.
        // Unbounded subscription (residue #3): the writer never backpressures on a slow browser, and
        // the per-stream boundary contains a rendering/transport failure to THIS subscriber only.
        val frames: Stream[IO, ServerSentEvent] =
          Stream.resource(topic.subscribeAwaitUnbounded).flatMap { live =>
            Stream.eval(logRef.get).flatMap { snapshot =>
              val lastSeq = snapshot.lastOption.fold(0)(_.seq)
              (Stream.emits(snapshot) ++ live.filter(_.seq > lastSeq))
                .map(event => ServerSentEvent(data = Some(Wire.encode(event).noSpaces)))
                .handleErrorWith(_ => Stream.exec(IO.unit))
            }
          }
        Ok(frames)

      case request @ POST -> Root / "answer" =>
        request.attemptAs[AnswerCommand].value.flatMap {
          case Left(_) =>
            BadRequest(
              Json.obj(
                "error" -> "malformed body; expected {questionId, answer in yes|no|unknown}".asJson
              )
            )
          case Right(command) =>
            oracle
              .resolve(command.questionId, command.answer)
              .flatMap(resolved => Ok(Json.obj("resolved" -> resolved.asJson)))
        }

      case POST -> Root / "start" =>
        // Restart the single game (serialized by the GameSupervisor's mutex): the response returns
        // once the old game is torn down, the shared state reset, and one fresh game forked.
        startGame *> Ok(Json.obj("started" -> true.asJson))

      case GET -> Root =>
        Ok(landing)
    }
