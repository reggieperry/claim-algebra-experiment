package claimalgebra.society

import cats.effect.{Deferred, IO, Ref}

/** The human/oracle seam realized over the wire (the counterpart to [[Oracle.console]] and
  * [[Oracle.scripted]]): the LogActor asks a question through [[answer]], which PARKS on a one-shot
  * [[Deferred]] keyed by the question id, and `POST /answer` completes it through [[resolve]]. The
  * awaiting `answer` fiber wakes with the human's reply and the LogActor proceeds — exactly the
  * seam the SSE transport needs, with the reply arriving asynchronously from the browser rather
  * than stdin.
  *
  * Fail-closed and idempotent by construction: [[resolve]] of an unknown question id (or of one
  * already answered) is a no-op returning `false` — it never errors and never manufactures a second
  * answer, so a duplicate or stray POST cannot corrupt the game. Registration is atomic (a single
  * `Ref.modify`), so a re-asked or concurrently-asked question shares the one Deferred rather than
  * racing two.
  */
final class RemoteOracle private (
    pending: Ref[IO, Map[QuestionId, Deferred[IO, OracleAnswer]]]
) extends Oracle:

  /** Register (or join) the pending question and await its answer. If the same id is already
    * pending, await the SAME Deferred rather than overwrite it.
    */
  def answer(question: Question): IO[OracleAnswer] =
    Deferred[IO, OracleAnswer].flatMap { fresh =>
      pending.modify { waiting =>
        waiting.get(question.id) match
          case Some(existing) => (waiting, existing.get)
          case None => (waiting.updated(question.id, fresh), fresh.get)
      }.flatten
    }

  /** Complete the pending question with the human's reply. Returns whether a pending question was
    * actually resolved: `false` for an unknown or already-answered id (fail-closed no-op). The id
    * is removed on resolution, so a second resolve of the same id is a `false` no-op — never a
    * throw, never a second wake.
    */
  def resolve(id: QuestionId, answer: OracleAnswer): IO[Boolean] =
    pending.modify { waiting =>
      waiting.get(id) match
        case None => (waiting, IO.pure(false))
        case Some(deferred) => (waiting - id, deferred.complete(answer))
    }.flatten

  /** Drop every pending registration — a game restart clears the map so a stale question id from
    * the cancelled game can never resolve into the new game (a [[resolve]] of it is a fail-closed
    * `false` no-op). A parked `answer` fiber whose `Deferred` is dropped here simply never wakes;
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
      .of(Map.empty[QuestionId, Deferred[IO, OracleAnswer]])
      .map(new RemoteOracle(_))
