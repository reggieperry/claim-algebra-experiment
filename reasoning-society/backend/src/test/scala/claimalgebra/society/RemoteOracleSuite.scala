package claimalgebra.society

import cats.effect.IO
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The wire oracle seam (deterministic via Deferred/Ref). A parked `respond` wakes with the move a
  * `resolveAnswer`/`resolveChallenge` supplies; an unknown or already-completed id is a fail-closed
  * no-op, never a throw and never a second wake. A CHALLENGE is consumed (removed) so a re-ask
  * re-registers a fresh Deferred for the same id.
  */
class RemoteOracleSuite extends CatsEffectSuite:

  private def mkQuestion(raw: String): QuestionId = QuestionId.from(raw).fold(fail(_), identity)
  private def mkTerm(raw: String): Term = Term.from(raw).fold(fail(_), identity)

  // Registration of `respond` is an IO effect that may run slightly after a concurrent `resolve`;
  // retry until the pending question is present, so the outcome is deterministic without a sleep-sync.
  private def resolveAnswerWhenRegistered(
      oracle: RemoteOracle,
      id: QuestionId,
      answer: OracleAnswer
  ): IO[Boolean] =
    oracle.resolveAnswer(id, answer).flatMap {
      case true => IO.pure(true)
      case false => IO.sleep(2.millis) *> resolveAnswerWhenRegistered(oracle, id, answer)
    }

  private def resolveChallengeWhenRegistered(
      oracle: RemoteOracle,
      id: QuestionId,
      term: Term
  ): IO[Boolean] =
    oracle.resolveChallenge(id, term).flatMap {
      case true => IO.pure(true)
      case false => IO.sleep(2.millis) *> resolveChallengeWhenRegistered(oracle, id, term)
    }

  test("respond parks until resolveAnswer completes it, then returns the posted answer move") {
    val id = mkQuestion("q1")
    for
      oracle <- RemoteOracle.make
      parked = oracle.respond(Question(id, "is it a fruit?"))
      result <- IO.both(parked, resolveAnswerWhenRegistered(oracle, id, OracleAnswer.Yes))
    yield assertEquals(result._1, HumanMove.Answer(OracleAnswer.Yes))
  }

  test("respond parks until resolveChallenge completes it, then returns the challenge move") {
    val id = mkQuestion("q1")
    val alive = mkTerm("alive")
    for
      oracle <- RemoteOracle.make
      parked = oracle.respond(Question(id, "is it alive?"))
      result <- IO.both(parked, resolveChallengeWhenRegistered(oracle, id, alive))
    yield assertEquals(result._1, HumanMove.Challenge(alive))
  }

  test("a consumed challenge frees the id so a re-ask re-registers a fresh respond") {
    val id = mkQuestion("q1")
    val alive = mkTerm("alive")
    for
      oracle <- RemoteOracle.make
      // First ask → challenged and consumed.
      first <- IO.both(
        oracle.respond(Question(id, "is it alive?")),
        resolveChallengeWhenRegistered(oracle, id, alive)
      )
      // The re-ask registers a FRESH Deferred for the SAME id; now an answer completes it.
      second <- IO.both(
        oracle.respond(Question(id, "is it alive?")),
        resolveAnswerWhenRegistered(oracle, id, OracleAnswer.Yes)
      )
    yield
      assertEquals(
        first._1,
        HumanMove.Challenge(alive),
        clue("the first respond got the challenge")
      )
      assertEquals(
        second._1,
        HumanMove.Answer(OracleAnswer.Yes),
        clue("the re-ask registered afresh and got the answer — the spent challenge did not linger")
      )
  }

  test("resolveAnswer of an unknown question id is a fail-closed no-op (false), never an error") {
    for
      oracle <- RemoteOracle.make
      resolved <- oracle.resolveAnswer(mkQuestion("nope"), OracleAnswer.No)
    yield assertEquals(resolved, false)
  }

  test("resolveChallenge of an unknown question id is a fail-closed no-op (false), never an error") {
    for
      oracle <- RemoteOracle.make
      resolved <- oracle.resolveChallenge(mkQuestion("nope"), mkTerm("alive"))
    yield assertEquals(resolved, false)
  }

  test("resolve is idempotent: a second resolve of the same question is a no-op") {
    val id = mkQuestion("q1")
    for
      oracle <- RemoteOracle.make
      parked <- oracle.respond(Question(id, "?")).start
      first <- resolveAnswerWhenRegistered(oracle, id, OracleAnswer.Yes)
      move <- parked.joinWithNever
      second <- oracle.resolveAnswer(id, OracleAnswer.No)
    yield
      assertEquals(first, true, clue("the first resolve woke the parked question"))
      assertEquals(
        move,
        HumanMove.Answer(OracleAnswer.Yes),
        clue("the parked respond got the FIRST reply")
      )
      assertEquals(second, false, clue("the second resolve is a no-op — no double answer"))
  }

  // Poll the read-only pending count until it reaches `n` — the same deterministic
  // wait-for-registration idiom as the resolve helpers, but observing rather than consuming, so
  // the question is still registered when the test resets.
  private def awaitPending(oracle: RemoteOracle, n: Int): IO[Unit] =
    oracle.pendingCount.flatMap {
      case m if m >= n => IO.unit
      case _ => IO.sleep(2.millis) *> awaitPending(oracle, n)
    }

  test("reset clears every pending question — a stale id then resolves to a fail-closed false") {
    val id = mkQuestion("q1")
    for
      oracle <- RemoteOracle.make
      parked <- oracle.respond(Question(id, "is it a fruit?")).start
      _ <- awaitPending(oracle, 1) // the question registered
      before <- oracle.pendingCount
      _ <- oracle.reset
      after <- oracle.pendingCount
      // The SAME id a stale POST /answer might carry, resolved AFTER the reset: a no-op.
      resolved <- oracle.resolveAnswer(id, OracleAnswer.Yes)
      _ <- parked.cancel
    yield
      assertEquals(before, 1, clue("the question was registered before the reset"))
      assertEquals(after, 0, clue("reset dropped every pending registration"))
      assertEquals(
        resolved,
        false,
        clue(
          "a stale id resolves to a no-op — it can never wake a question the new game never asked"
        )
      )
  }
