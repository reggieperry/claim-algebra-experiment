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
  *   - `POST /start` — New Game (the injected `newGame`: the [[GameSupervisor]] cancels the running
  *     game, HARVESTS its definitions into persistent memory, clears the working log/oracle, and
  *     forks one fresh game seeded with the recalled definitions — serialized, so two
  *     `POST /start`s cannot stack games). Definitions carry over.
  *   - `POST /reset` — Full Reset (the injected `fullReset`): New Game PLUS clearing the persistent
  *     definitions — a truly blank session.
  *   - `GET  /` — a plain-text landing describing the endpoints (the React viewer is slice 3b).
  */
object SocietyRoutes:

  // The boundary decoders: JSON -> command, re-validated to the domain types (a bad token, a blank
  // id, or a blank term is a DecodingFailure, surfaced as a 400 below).
  private given answerEntityDecoder: EntityDecoder[IO, AnswerCommand] = jsonOf[IO, AnswerCommand]
  private given challengeEntityDecoder: EntityDecoder[IO, ChallengeCommand] =
    jsonOf[IO, ChallengeCommand]
  private given rewindEntityDecoder: EntityDecoder[IO, RewindCommand] = jsonOf[IO, RewindCommand]

  private val landing: String =
    """reasoning-society transport (Build 3 slice 3a)
      |  GET  /events    text/event-stream — the live event log as JSON frames
      |  POST /answer    {"questionId": "...", "answer": "yes|no|unknown"}
      |  POST /challenge {"questionId": "...", "term": "..."}   (define a term before answering)
      |  POST /start     New Game — fresh working log, one game; learned definitions kept
      |  POST /reset     Full Reset — New Game plus clearing the learned definitions
      |  POST /rewind    {"toSeq": <AnswerGiven seq>}  — flip one poisoned early answer (B2)
      |""".stripMargin

  /** Wire the routes.
    *
    * @param topic
    *   the event log Topic every SSE subscriber reads.
    * @param oracle
    *   the human/oracle seam `POST /answer` completes.
    * @param newGame
    *   New Game (`POST /start`) — the [[GameSupervisor]]'s serialized cancel → harvest → clear →
    *   fork; keeps the learned definitions. Returns once the fresh game has been forked.
    * @param fullReset
    *   Full Reset (`POST /reset`) — as New Game, but also clears the persistent definitions.
    */
  def apply(
      topic: Topic[IO, Event],
      logRef: Ref[IO, Vector[Event]],
      oracle: RemoteOracle,
      newGame: IO[Unit],
      fullReset: IO[Unit],
      rewindTo: Int => IO[Unit]
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
              .resolveAnswer(command.questionId, command.answer)
              .flatMap(resolved => Ok(Json.obj("resolved" -> resolved.asJson)))
        }

      case request @ POST -> Root / "challenge" =>
        // The human CHALLENGES a term before answering (clarification-feature §1). Untrusted input: a
        // malformed body (or blank id/term) is a 400, never a 500. A valid body completes the pending
        // question with a Challenge, which PAUSES grounding and re-asks after the agent defines the
        // term. Fail-closed on an unknown/blank id: `resolved:false`, a no-op (never a second reply).
        request.attemptAs[ChallengeCommand].value.flatMap {
          case Left(_) =>
            BadRequest(
              Json.obj("error" -> "malformed body; expected {questionId, term}".asJson)
            )
          case Right(command) =>
            oracle
              .resolveChallenge(command.questionId, command.term)
              .flatMap(resolved => Ok(Json.obj("resolved" -> resolved.asJson)))
        }

      case POST -> Root / "start" =>
        // New Game (serialized by the GameSupervisor's mutex): the response returns once the old
        // game is torn down, its definitions harvested, the working scope reset, and one fresh game
        // forked seeded with the learned definitions.
        newGame *> Ok(Json.obj("started" -> true.asJson))

      case POST -> Root / "reset" =>
        // Full Reset (serialized by the same mutex): as New Game, but the persistent definitions are
        // cleared too — a truly blank session.
        fullReset *> Ok(Json.obj("reset" -> true.asJson))

      case request @ POST -> Root / "rewind" =>
        // Rewind (B2): the human flips ONE poisoned early answer. Untrusted input: a malformed body or
        // a non-positive toSeq is a 400. A valid toSeq is snapped SERVER-SIDE to the round boundary
        // (never the client), and the SAME game re-folds the truncated prefix — fail-closed to a no-op
        // prefix if the seq names no answer. Serialized by the GameSupervisor's mutex; on 2xx the
        // browser reconnects and refills from the truncated snapshot.
        request.attemptAs[RewindCommand].value.flatMap {
          case Left(_) =>
            BadRequest(
              Json.obj("error" -> "malformed body; expected {toSeq: positive int}".asJson)
            )
          case Right(command) =>
            rewindTo(command.toSeq) *> Ok(Json.obj("rewound" -> true.asJson))
        }

      case GET -> Root =>
        Ok(landing)
    }
