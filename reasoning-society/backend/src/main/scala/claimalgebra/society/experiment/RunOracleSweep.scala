package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, CallError, LlmCall}

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

  /** The live Arm-1 path (Slice 3). The society runs on Haiku (the agent under test); the
    * experimenter's ground truth is a held-fixed [[ModelTruthOracle]] — model-backed because the
    * live game's questions are free text (a table can't match them). This DEFAULT config is a SMALL
    * SMOKE (2 games) to validate the live path end to end; scale the cells / targets / gamesPerCell
    * up for the real sweep — a deliberate, billed run behind a cost check. A stronger
    * truthful-oracle tier (e.g. `Model.CLAUDE_SONNET_5`) is recommended for the real ground truth;
    * Haiku keeps the smoke cheap.
    */
  private def live: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth = ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto]))
      val cells = List(
        SweepCell(1.0, "perfect", "common"),
        SweepCell(0.6, "systematic", "common")
      )
      for
        apple <- answer("apple")
        targets = List((apple, truth))
        _ <- IO.println("=== fallible-oracle sweep — LIVE Haiku (billed) — SMOKE (2 games) ===\n")
        records <- OracleSweep.sweep(
          IO.pure((_: AgentId) => societyLlm),
          config,
          cells,
          targets,
          gamesPerCell = 1,
          concurrency = 1,
          definerFor = _ => definer
        )
        _ <- records.traverse_ { r =>
          IO.println(
            s"  target=${r.trueTarget.value}  p=${r.cell.reliability} ${r.cell.errorModel}  " +
              s"-> ${r.outcome}  (signed ${r.signed.map(_.value).getOrElse("nothing")})"
          )
        }
        _ <- IO.println("")
        _ <- IO.println(OracleSweep.render(OracleSweep.summarize(records)))
      yield ()
    }

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
