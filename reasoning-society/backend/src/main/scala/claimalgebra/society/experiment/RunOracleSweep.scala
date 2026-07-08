package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}

import scala.concurrent.duration.*

/** The fallible-oracle sweep entry point (fallible-oracle-build-plan §Slice 2). The DEFAULT run is
  * a HERMETIC demonstration — a scripted apple-signing cohort over a couple of cells, proving the
  * harness runs end to end and prints the three-rate report WITHOUT a single live call. The stub
  * ignores the oracle, so this exercises the PIPELINE, not the science; the real curves need live
  * agents that reason over corrupted answers plus the experimenter's truthful-oracle — a design
  * decision, sequenced as Slice 3 and gated behind `RUN_LIVE_ORACLE_SWEEP`.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunOracleSweep"}}}
  */
object RunOracleSweep extends IOApp.Simple:

  private val config =
    SocietyConfig(maxRounds = 6, roundTimeout = 5.seconds, hardDeadline = 1.minute)

  def run: IO[Unit] =
    if sys.env.contains("RUN_LIVE_ORACLE_SWEEP") then live else hermeticDemo

  private def hermeticDemo: IO[Unit] =
    val cells = List(
      SweepCell(1.0, "perfect", "easy"),
      SweepCell(0.7, "systematic", "easy"),
      SweepCell(0.7, "independent-uniform", "easy")
    )
    for
      apple <- answer("apple")
      dog <- answer("dog")
      truth = TruthOracle.pure((_, _) => OracleAnswer.Yes)
      targets = List((apple, truth), (dog, truth))
      records <- OracleSweep.sweep(
        signingCohort,
        config,
        cells,
        targets,
        gamesPerCell = 3,
        concurrency = 2
      )
      _ <- IO.println(
        "=== fallible-oracle sweep — HERMETIC demo (scripted cohort; pipeline only) ==="
      )
      _ <- IO.println(
        "(the stub ignores the oracle and always signs apple, so p has no effect here;"
      )
      _ <- IO.println(
        " it proves the harness runs. Real curves: RUN_LIVE_ORACLE_SWEEP — Slice 3.)\n"
      )
      _ <- IO.println(OracleSweep.render(OracleSweep.summarize(records)))
    yield ()

  private def live: IO[Unit] =
    IO.println(
      "A live-Haiku sweep needs the experimenter's TRUTHFUL ORACLE — a pre-registered property table " +
        "(closed question-space) or a held-fixed truthful model call (open question-space). That is " +
        "the sharpest open design decision (fallible-oracle-build-plan §Decisions), sequenced as " +
        "Slice 3. Not yet wired."
    )

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(m => RuntimeException(m)))

  private val fallbackStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(s: String, u: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(Right(StubLlm.pass))

  // A FRESH apple-signing cohort per game (StubLlm cursors are per-game state, so the factory rebuilds
  // them each time — mirrors RunServer.hermetic).
  private def signingCohort: IO[AgentId => LlmCall[AgentMoveDto]] =
    val scripts: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
      "driller" -> List(Right(StubLlm.move("assert", "apple", "a common fruit"))),
      "splitter" -> List(
        Right(StubLlm.move("propose", "", "Is it a fruit?")),
        Right(StubLlm.move("corroborate", "apple", "agreed"))
      ),
      "skeptic" -> List(Right(StubLlm.pass))
    )
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (id: AgentId) => byId.getOrElse(id.value, fallbackStub))
