package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, CallError, LlmCall}

import scala.concurrent.duration.*

/** The fallible-oracle sweep entry point. The DEFAULT run is the HERMETIC redundancy/correlation
  * curve ([[correlationSweep]], Slice 4 — the crown jewel): a deterministic, large-N sweep over
  * `(k, rho)` through the real society / gate / re-pose loop that shows the fail-open rate falling
  * from `(1-p)^k` (independent confirmations) to `(1-p)` (correlated — the monoculture) as `rho`
  * rises. `RUN_LIVE_ORACLE_SWEEP` selects the billed live diagnostic ([[live]], Slice 3) instead.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunOracleSweep"}}}
  */
object RunOracleSweep extends IOApp.Simple:

  def run: IO[Unit] =
    if sys.env.contains("RUN_LIVE_ORACLE_SWEEP") then live else correlationSweep

  // A tight budget: the lone cohort asserts once then passes, so a game reaches the give-up GUESS in
  // a few rounds. Rounds close on full-cohort report (every agent posts each round), so the timers
  // never fire and the sweep stays fast and fully hermetic.
  private val hermeticConfig =
    SocietyConfig(maxRounds = 3, roundTimeout = 5.seconds, hardDeadline = 1.minute)

  /** The HERMETIC redundancy/correlation curve (fallible-oracle Slice 4). A scripted LONE-candidate
    * cohort (asserts "apple", one backer, then passes) drives every game straight to the give-up
    * GUESS "is it apple?" — but the sealed target is "dog", so the guess is WRONG and any signature
    * is a FAIL-OPEN. The oracle answers the guess through `CorrelatedConfirmations(p, rho)`: a
    * fail-open needs ALL `k` poses to corrupt No→Yes, so the fail-open rate IS the joint flip rate
    * — `(1-p)^k` at rho=0 (independent confirmations → redundancy pays) climbing to `(1-p)` at
    * rho=1 (correlated → the monoculture failure mode, where redundancy buys nothing). It runs
    * through the REAL society / gate / re-pose loop, not the ErrorModel in isolation, so it
    * VALIDATES that the k-quorum machinery yields that rate end to end (a buggy loop would break
    * the curve).
    *
    * Scope, stated honestly: `rho` is an ASSUMED, swept parameter — this makes the Part-V
    * monoculture failure mode CONCRETE as a function of a given confirmation-correlation; it does
    * NOT measure the real correlation of redundant model checks (this bench says nothing about that
    * magnitude), and the curve is a closed form a pure test already pins
    * ([[ExperimentOracleSuite]]), so the value here is end-to-end integration confidence, not a
    * numerical discovery.
    */
  private def correlationSweep: IO[Unit] =
    val p = 0.7
    val n = 800
    val cells = for
      k <- List(1, 2, 3)
      rho <- List(0.0, 0.5, 1.0)
    yield SweepCell(p, "correlated", "lone-wrong-guess", k = k, rho = rho)
    for
      dog <- answer("dog")
      // never consulted: the cohort proposes no property questions, and guesses are answered
      // structurally (guessTruth), so the truth oracle is inert here.
      truth = TruthOracle.pure((_, _) => OracleAnswer.Unknown)
      _ <- IO.println(
        s"=== fallible-oracle CORRELATION sweep — HERMETIC (p=$p, N=$n/cell, k × rho) ==="
      )
      _ <- IO.println(
        "every sign is an oracle-confirmed fail-open (target=dog, lone guess=apple);" +
          " expect (1-p)^k at rho=0, (1-p) at rho=1\n"
      )
      records <- OracleSweep.sweep(
        loneCohort,
        hermeticConfig,
        cells,
        List((dog, truth)),
        gamesPerCell = n,
        concurrency = 8
      )
      _ <- IO.println(OracleSweep.renderCorrelation(records, p))
    yield ()

  // A bigger budget than the hermetic config: live Haiku needs room to converge on a lone candidate
  // before B1's guess (the fail-open locus) can fire at all — the 6-round smoke abstained every game.
  // Still bounded per game by the hard deadline.
  private val liveConfig =
    SocietyConfig(maxRounds = 12, roundTimeout = 30.seconds, hardDeadline = 5.minutes)

  /** The live Arm-1 path (Slice 3). The society runs on Haiku (the agent under test); the
    * experimenter's ground truth is a held-fixed [[ModelTruthOracle]] — model-backed because the
    * live game's questions are free text (a table can't match them). This DIAGNOSTIC config runs 2
    * games with full logs printed ([[OracleSweep.renderLog]]); it VALIDATED a live fail-open — a
    * p=0.6 systematic game drifted to a wrong "crystal_vase" candidate (organic→NO, materials→YES
    * corrupted) and the corrupted oracle confirmed the guess → SignWrong, while the p=1.0 game
    * abstained. Scale cells / targets / N up for the real sweep — a deliberate, billed run behind a
    * cost check. A stronger truthful-oracle tier (e.g. `Model.CLAUDE_SONNET_5`) is recommended for
    * the real ground truth.
    */
  private def live: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth = ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto]))
      val cells = List(
        SweepCell(1.0, "perfect", "common"),
        SweepCell(0.6, "systematic", "common")
      )
      for
        apple <- answer("apple")
        _ <- IO.println(
          "=== fallible-oracle — LIVE Haiku (billed) — DIAGNOSTIC (2 games, maxRounds=12) ==="
        )
        results <- cells.traverse { cell =>
          OracleSweep.runOneGame(
            societyLlm,
            liveConfig,
            cell,
            apple,
            truth,
            seed = cell.reliability.hashCode.toLong,
            definerFor = _ => definer
          )
        }
        _ <- results.traverse_ { case (rec, log) =>
          IO.println(
            s"\n--- p=${rec.cell.reliability} ${rec.cell.errorModel} · target=${rec.trueTarget.value}" +
              s" · ${rec.outcome} (signed ${rec.signed.map(_.value).getOrElse("nothing")}) ---"
          ) *> IO.println(OracleSweep.renderLog(log))
        }
        _ <- IO.println("\n" + OracleSweep.render(OracleSweep.summarize(results.map(_._1))))
      yield ()
    }

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(m => RuntimeException(m)))

  private val fallbackStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(s: String, u: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(Right(StubLlm.pass))

  // A FRESH lone cohort per game: driller asserts "apple" ONCE (one backer, no corroborator) then
  // passes, and every other agent passes → a lone Unconfirmed winner → the give-up ladder GUESSES
  // "is it apple?". (StubLlm cursors are per-game state, so the factory rebuilds them each game;
  // `[assert, pass]` is explicit because an exhausted script repeats its LAST element.)
  private def loneCohort: IO[AgentId => LlmCall[AgentMoveDto]] =
    val scripts: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
      "driller" -> List(Right(StubLlm.move("assert", "apple", "a lone guess")), Right(StubLlm.pass))
    )
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (id: AgentId) => byId.getOrElse(id.value, fallbackStub))
