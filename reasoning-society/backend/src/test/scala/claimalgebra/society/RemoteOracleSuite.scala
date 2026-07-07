package claimalgebra.society

import cats.effect.IO
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The wire oracle seam (deterministic via Deferred/Ref). A parked `answer` wakes with the reply a
  * `resolve` supplies; an unknown or already-answered id is a fail-closed no-op, never a throw and
  * never a second wake.
  */
class RemoteOracleSuite extends CatsEffectSuite:

  private def mkQuestion(raw: String): QuestionId = QuestionId.from(raw).fold(fail(_), identity)

  // Registration of `answer` is an IO effect that may run slightly after a concurrent `resolve`;
  // retry until the pending question is present, so the outcome is deterministic without a sleep-sync.
  private def resolveWhenRegistered(
      oracle: RemoteOracle,
      id: QuestionId,
      answer: OracleAnswer
  ): IO[Boolean] =
    oracle.resolve(id, answer).flatMap {
      case true => IO.pure(true)
      case false => IO.sleep(2.millis) *> resolveWhenRegistered(oracle, id, answer)
    }

  test("answer parks until resolve completes it, then returns the posted reply") {
    val id = mkQuestion("q1")
    for
      oracle <- RemoteOracle.make
      parked = oracle.answer(Question(id, "is it a fruit?"))
      result <- IO.both(parked, resolveWhenRegistered(oracle, id, OracleAnswer.Yes))
    yield assertEquals(result._1, OracleAnswer.Yes)
  }

  test("resolve of an unknown question id is a fail-closed no-op (false), never an error") {
    for
      oracle <- RemoteOracle.make
      resolved <- oracle.resolve(mkQuestion("nope"), OracleAnswer.No)
    yield assertEquals(resolved, false)
  }

  test("resolve is idempotent: a second resolve of the same question is a no-op") {
    val id = mkQuestion("q1")
    for
      oracle <- RemoteOracle.make
      parked <- oracle.answer(Question(id, "?")).start
      first <- resolveWhenRegistered(oracle, id, OracleAnswer.Yes)
      answer <- parked.joinWithNever
      second <- oracle.resolve(id, OracleAnswer.No)
    yield
      assertEquals(first, true, clue("the first resolve woke the parked question"))
      assertEquals(answer, OracleAnswer.Yes, clue("the parked answer got the FIRST reply"))
      assertEquals(second, false, clue("the second resolve is a no-op — no double answer"))
  }

  // Poll the read-only pending count until it reaches `n` — the same deterministic
  // wait-for-registration idiom as `resolveWhenRegistered`, but observing rather than consuming, so
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
      parked <- oracle.answer(Question(id, "is it a fruit?")).start
      _ <- awaitPending(oracle, 1) // the question registered
      before <- oracle.pendingCount
      _ <- oracle.reset
      after <- oracle.pendingCount
      // The SAME id a stale POST /answer might carry, resolved AFTER the reset: a no-op.
      resolved <- oracle.resolve(id, OracleAnswer.Yes)
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
