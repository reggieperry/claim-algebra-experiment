package claimalgebra.society
package experiment

import cats.effect.IO
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** Slice 2 (fallible-oracle): the headless batch runner + grader, validated HERMETICALLY — a
  * scripted cohort that deterministically signs "apple" drives the full pipeline (seed the sealed
  * truth → Society.play → collect the log → classify → GameRecord) without a single live Haiku
  * call. The science needs live agents that reason over corrupted answers (Slice 3); this proves
  * the plumbing.
  */
class OracleSweepSuite extends CatsEffectSuite with SocietyFixtures:

  private val apple = mkAnswer("apple")
  private val dog = mkAnswer("dog")
  private val fast = SocietyConfig(maxRounds = 6, roundTimeout = 5.seconds, hardDeadline = 1.minute)
  private val truthYes = TruthOracle.pure((_, _) => OracleAnswer.Yes)
  private val perfectCell = SweepCell(1.0, "perfect", "easy")

  private val fallbackStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(s: String, u: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(Right(StubLlm.pass))

  private def cohortFrom(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (id: AgentId) => byId.getOrElse(id.value, fallbackStub))

  // The apple-signing cohort (mirrors RunServer.hermetic): assert + corroborate apple -> 2 backers ->
  // sign apple, regardless of the oracle's answer to "Is it a fruit?".
  private val signingScripts: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
    "driller" -> List(Right(StubLlm.move("assert", "apple", "a common fruit"))),
    "splitter" -> List(
      Right(StubLlm.move("propose", "", "Is it a fruit?")),
      Right(StubLlm.move("corroborate", "apple", "agreed"))
    ),
    "skeptic" -> List(Right(StubLlm.pass))
  )
  private val silentScripts: Map[String, List[Either[CallError, AgentMoveDto]]] =
    Map("driller" -> Nil, "splitter" -> Nil, "skeptic" -> Nil)

  test(
    "runOneGame — a correct sign against the sealed truth is SignCorrect, with the truth logged"
  ) {
    for
      llmFor <- cohortFrom(signingScripts)
      result <- OracleSweep.runOneGame(llmFor, fast, perfectCell, apple, truthYes, seed = 1L)
    yield
      val (rec, log) = result
      assertEquals(rec.outcome, PrimaryOutcome.SignCorrect)
      assertEquals(rec.signed, Some(apple))
      assert(
        log.headOption.exists {
          case Event.TargetRegistered(_, _, t, _) => t == apple
          case _ => false
        },
        clue("the full log is self-contained: the sealed truth is at its head")
      )
  }

  test(
    "runOneGame — a sign that misses the sealed truth is SignWrong (the fail-open the metric counts)"
  ) {
    for
      llmFor <- cohortFrom(signingScripts)
      result <- OracleSweep.runOneGame(llmFor, fast, perfectCell, dog, truthYes, seed = 1L)
    yield
      val rec = result._1
      assertEquals(
        rec.outcome,
        PrimaryOutcome.SignWrong,
        clue("stub signs apple; the truth is dog")
      )
      assertEquals(rec.signed, Some(apple))
  }

  test("runOneGame — a game that never signs is Abstain") {
    for
      llmFor <- cohortFrom(silentScripts)
      result <- OracleSweep.runOneGame(llmFor, fast, perfectCell, apple, truthYes, seed = 1L)
    yield assertEquals(result._1.outcome, PrimaryOutcome.Abstain)
  }

  test("summarize computes fail-open / abstain / sign-correct rates per cell") {
    val recs = List(
      GameRecord(perfectCell, apple, Some(apple), PrimaryOutcome.SignCorrect, 1L),
      GameRecord(perfectCell, dog, Some(apple), PrimaryOutcome.SignWrong, 2L),
      GameRecord(perfectCell, apple, None, PrimaryOutcome.Abstain, 3L)
    )
    val cell = OracleSweep.summarize(recs)(perfectCell)
    assertEquals(cell.n, 3)
    assertEquals(cell.failOpen.count, 1)
    assertEquals(cell.signCorrect.count, 1)
    assertEquals(cell.abstain.count, 1)
    // Wilson interval is non-degenerate and within [0,1].
    val (lo, hi) = cell.failOpen.ci95
    assert(lo >= 0.0 && hi <= 1.0 && lo < hi, clue((lo, hi)))
  }
