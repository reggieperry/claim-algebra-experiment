package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import java.nio.file.Path
import scala.concurrent.duration.*

/** The composed cell (docs/reasoning-society/fallible-oracle-composed-cell-experiment.md): the
  * fourth corner of the endgame × seam grid — improved endgame × seam-GATED × perfect oracle —
  * measured WITHIN-RUN against its own seam-OPEN control, on the improvement's own target sets
  * (dev-8 N=4, held-out-10 N=2). It runs the OPEN arm first and commits the outcome-2a pre-pull
  * (the seam-open correct-`BackerQuorum` count) BEFORE the GATED arm's wins are read — a temporal
  * firewall against motivated reading — archives both arms' full event logs, and prints the
  * config-surface stamp. Fail-open is the deductive headline; wins are the open question. NOT
  * hermetic: live Haiku society, Sonnet truth oracle, Anthropic API key only.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunComposedCell"}}}
  */
object RunComposedCell extends IOApp.Simple:

  private def cfg(corroborationSigns: Boolean): SocietyConfig =
    SocietyConfig(
      maxRounds = 16, // matches RunWinRate (Run A); a shorter budget confounds the win comparison
      roundTimeout = 30.seconds,
      hardDeadline = 5.minutes,
      corroborationSigns = corroborationSigns
    )

  // dev-8 (RunWinRate.targetNames) and held-out-10 (RunEndgameAudit.heldOut) — disjoint by construction.
  private val devNames = List("dog", "apple", "chair", "spoon", "book", "tree", "cup", "shoe")
  private val heldNames =
    List(
      "banana",
      "pencil",
      "clock",
      "guitar",
      "hammer",
      "sock",
      "kite",
      "umbrella",
      "candle",
      "drum"
    )
  private val devN = 4
  private val heldN = 2
  // ErrorModel.perfect (NOT "systematic"@1.0, which flips at a measure-zero boundary); tagged so
  // renderPrimary reports dev and held-out on separate rows rather than pooling them.
  private val devCell = SweepCell(1.0, "perfect", "dev", k = 1)
  private val heldCell = SweepCell(1.0, "perfect", "held", k = 1)
  private val concurrency = 6

  private val societyModel: Model = AnthropicLlmCall.DefaultModel
  private val truthModel: Model = Model.CLAUDE_SONNET_5

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(RuntimeException(_)))

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth: TruthOracle =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = truthModel))
      val stamp = ConfigStamp.of(cfg(true), devCell, societyModel.toString, truthModel.toString)
      val resultsDir = Path.of("results", s"composed-cell-$stamp")

      def sweepArm(corroborationSigns: Boolean): IO[List[(GameRecord, Vector[Event])]] =
        for
          devTargets <- devNames.traverse(answer)
          heldTargets <- heldNames.traverse(answer)
          dev <- OracleSweep.sweepWithLogs(
            IO.pure(societyLlm),
            cfg(corroborationSigns),
            List(devCell),
            devTargets.map(t => (t, truth)),
            devN,
            concurrency,
            _ => definer
          )
          held <- OracleSweep.sweepWithLogs(
            IO.pure(societyLlm),
            cfg(corroborationSigns),
            List(heldCell),
            heldTargets.map(t => (t, truth)),
            heldN,
            concurrency,
            _ => definer
          )
        yield dev ++ held

      def reportArm(label: String, entries: List[(GameRecord, Vector[Event])]): IO[Unit] =
        val recs = entries.map(_._1)
        val held = recs.filter(_.cell.difficulty == "held")
        val looseCor = held.count(_.outcome == PrimaryOutcome.SignCorrect)
        val exactCor = held.count(r =>
          r.signed.exists(s => s.value.trim.toLowerCase == r.trueTarget.value.trim.toLowerCase)
        )
        IO.println(s"\n--- $label ---") *>
          IO.println(OracleSweep.renderPrimary(recs)) *>
          IO.println(
            s"held-out grading guard: loose-correct=$looseCor exact-correct=$exactCor" +
              " (a gap is loose-grading credit, not exact identification)"
          )

      for
        _ <- IO.println(
          "=== COMPOSED CELL — improved endgame × seam-{open,gated} × perfect oracle (LIVE) ==="
        )
        _ <- IO.println(
          s"society=$societyModel  truth=$truthModel  maxRounds=${cfg(true).maxRounds}  k=${devCell.k}  errorModel=perfect"
        )
        _ <- IO.println(
          s"config-surface stamp: $stamp  (hashed: cohort/oracle/nudge/grader/maxRounds/k/models;" +
            " unhashed but DECLARED here: the treatment variable corroborationSigns, errorModel=perfect, the dev/held sets)"
        )
        _ <- IO.println(
          s"targets: dev-8 N=$devN + held-out-10 N=$heldN = ${devNames.size * devN + heldNames.size * heldN} games/arm;" +
            s" archiving full logs under $resultsDir"
        )

        // OPEN arm FIRST — the control and the outcome-2a pre-pull, committed before the gated read.
        openEntries <- sweepArm(corroborationSigns = true)
        _ <- Archiver.archive(resultsDir, "seam-open", openEntries)
        _ <- reportArm(
          "seam-OPEN (verify = C ∨ O) — the stamped Run A reproduction / control",
          openEntries
        )
        openRecs = openEntries.map(_._1)
        (openCorBk, openCorOr) = OracleSweep.signCorrectByPath(openRecs)
        openCorrect = openRecs.count(_.outcome == PrimaryOutcome.SignCorrect)
        _ <- IO.println(
          f"\n>>> PRE-PULL COMMITTED (outcome-2a prior): seam-open correct=$openCorrect" +
            f" (via BackerQuorum=$openCorBk, via OracleConfirmed=$openCorOr)."
        )
        _ <- IO.println(
          s">>> Benign gated correct-drop CEILING = $openCorBk (the standalone 2-backer wins that must re-route);" +
            " a drop beyond it is a mechanism bug (2b)."
        )

        // GATED arm — the treatment, read against the committed pre-pull.
        gatedEntries <- sweepArm(corroborationSigns = false)
        _ <- Archiver.archive(resultsDir, "seam-gated", gatedEntries)
        _ <- reportArm("seam-GATED (verify → O) — the composed cell", gatedEntries)
        _ <- adjudicate(openRecs, gatedEntries.map(_._1), openCorBk)
      yield ()
    }

  /** The pre-registered readout: fail-open headline (deductive), win band (open), and the outcome
    * classification against the committed pre-pull. Fail-open > 0 at this cell is outcome 3 and
    * outranks everything; each instance is listed as an existence proof pointing at its archived
    * log.
    */
  private def adjudicate(
      openRecs: List[GameRecord],
      gatedRecs: List[GameRecord],
      openCorBk: Int
  ): IO[Unit] =
    val n = gatedRecs.size
    val fo = gatedRecs.count(_.outcome == PrimaryOutcome.SignWrong)
    val cor = gatedRecs.count(_.outcome == PrimaryOutcome.SignCorrect)
    val (foLo, foHi) = Rate(fo, n).ci95
    val openCor = openRecs.count(_.outcome == PrimaryOutcome.SignCorrect)
    val drop = openCor - cor
    val winNote =
      if cor == 0 then "wins ZERO"
      else if cor <= 5 then
        "wins in the INCONCLUSIVE band (1-5) — pre-registered top-up to N>=150 triggers"
      else "wins clearly nonzero (>5)"
    val outcome =
      if fo > 0 then
        "OUTCOME 3 — fail-open > 0 at a perfect oracle with the seam gated: an UNENUMERATED sign path exists. " +
          "Read each fail-open's archived log; a single instance is an existence proof. This outranks everything in the queue."
      else if cor == 0 then
        "OUTCOME 2 — fail-open ~0 but wins collapsed to zero. Inspect the gated logs for winners that reached the oracle and failed to sign (a re-pose-loop mechanism bug)."
      else if drop <= openCorBk then
        "OUTCOME 1 / 2a — fail-open ~0, wins hold (nonzero) with the drop within the benign 2-backer-loss ceiling. " +
          "The coupling dissolves; the loss is the expected, quantified cost of removing the standalone path."
      else
        "OUTCOME 2b — fail-open ~0 but the correct-drop EXCEEDS the benign ceiling. A gate/nudge interaction bug in the re-pose loop is likely; read the gated logs."
    for
      _ <- IO.println(s"\n=== PRE-REGISTERED READOUT (n=$n gated games) ===")
      _ <- IO.println(
        f"fail-open (headline): $fo/$n = ${Rate(fo, n).point}%.3f [$foLo%.3f-$foHi%.3f]  — success iff the interval excludes 0.22"
      )
      _ <- IO.println(
        s"sign-correct: $cor/$n (seam-open: $openCor); drop=$drop, benign ceiling=$openCorBk; $winNote"
      )
      _ <- IO.println(s"\n$outcome")
      _ <- gatedRecs.filter(_.outcome == PrimaryOutcome.SignWrong).traverse_ { r =>
        IO.println(
          s"  !! FAIL-OPEN: target=${r.trueTarget.value} signed=${r.signed.map(_.value).getOrElse("-")}" +
            s" path=${r.signPath.map(_.toString).getOrElse("-")} seed=${r.seed} — see the archived log"
        )
      }
    yield ()
