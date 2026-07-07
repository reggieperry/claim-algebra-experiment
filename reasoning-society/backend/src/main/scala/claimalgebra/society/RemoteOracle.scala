package claimalgebra.society

import cats.effect.{Deferred, IO, Ref}

/** The human/oracle seam realized over the wire (the counterpart to [[Oracle.console]] and
  * [[Oracle.scripted]]): the LogActor asks a question through [[respond]], which PARKS on a
  * one-shot [[Deferred]] keyed by the question id, and the transport completes it — `POST /answer`
  * through [[resolveAnswer]] (an [[HumanMove.Answer]]), `POST /challenge` through
  * [[resolveChallenge]] (an [[HumanMove.Challenge]]). The awaiting `respond` fiber wakes with the
  * human's move and the LogActor proceeds — the seam the SSE transport needs, with the reply
  * arriving asynchronously from the browser rather than stdin.
  *
  * Fail-closed and idempotent by construction: a [[resolveAnswer]]/[[resolveChallenge]] of an
  * unknown question id (or of one already completed) is a no-op returning `false` — it never errors
  * and never manufactures a second reply, so a duplicate or stray POST cannot corrupt the game.
  * Registration is atomic (a single `Ref.modify`), so a re-asked or concurrently-asked question
  * shares the one Deferred rather than racing two.
  *
  * RE-ASK support (clarification-feature): a challenge is CONSUMED — the Deferred is completed and
  * REMOVED — so when the LogActor re-asks the same question, `respond` registers a FRESH Deferred
  * for the same id. The spent registration never lingers to swallow the re-ask's reply.
  */
final class RemoteOracle private (
    pending: Ref[IO, Map[QuestionId, Deferred[IO, HumanMove]]]
) extends Oracle:

  /** Register (or join) the pending question and await the human's move. If the same id is already
    * pending (a concurrent ask), await the SAME Deferred rather than overwrite it; on a re-ask the
    * prior Deferred was already consumed and removed, so this registers a fresh one.
    */
  def respond(question: Question): IO[HumanMove] =
    Deferred[IO, HumanMove].flatMap { fresh =>
      pending.modify { waiting =>
        waiting.get(question.id) match
          case Some(existing) => (waiting, existing.get)
          case None => (waiting.updated(question.id, fresh), fresh.get)
      }.flatten
    }

  /** Complete the pending question with the human's ANSWER (`POST /answer`). */
  def resolveAnswer(id: QuestionId, answer: OracleAnswer): IO[Boolean] =
    complete(id, HumanMove.Answer(answer))

  /** Complete the pending question with the human's CHALLENGE (`POST /challenge`) — the term is
    * already validated (non-blank, normalized) at the [[Term]] boundary before it reaches here.
    */
  def resolveChallenge(id: QuestionId, term: Term): IO[Boolean] =
    complete(id, HumanMove.Challenge(term))

  /** The shared completion path. Returns whether a pending question was actually resolved: `false`
    * for an unknown or already-completed id (fail-closed no-op). The id is removed on completion,
    * so a second resolve of the same id is a `false` no-op — never a throw, never a second wake —
    * and the removal is what frees the id for a re-ask's fresh registration.
    */
  private def complete(id: QuestionId, move: HumanMove): IO[Boolean] =
    pending.modify { waiting =>
      waiting.get(id) match
        case None => (waiting, IO.pure(false))
        case Some(deferred) => (waiting - id, deferred.complete(move))
    }.flatten

  /** Drop every pending registration — a game restart clears the map so a stale question id from
    * the cancelled game can never resolve into the new game (a resolve of it is a fail-closed
    * `false` no-op). A parked `respond` fiber whose `Deferred` is dropped here simply never wakes;
    * in the running system that fiber is a supervised child the game teardown already cancels, so
    * `reset` only clears the registry the routes look questions up in.
    */
  def reset: IO[Unit] = pending.set(Map.empty)

  /** The count of currently-registered pending questions — a read-only query the restart tests use
    * to observe registration and to confirm a [[reset]] cleared the map.
    */
  private[society] def pendingCount: IO[Int] = pending.get.map(_.size)

object RemoteOracle:
  def make: IO[RemoteOracle] =
    Ref[IO]
      .of(Map.empty[QuestionId, Deferred[IO, HumanMove]])
      .map(new RemoteOracle(_))
