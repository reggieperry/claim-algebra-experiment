package claimalgebra.society
package experiment

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import java.nio.file.Path
import scala.concurrent.duration.*

/** The stronger-closer trial (docs/reasoning-society/stronger-closer-trial-experiment.md): the
  * UNBLOCK question at a degraded oracle — does allocating compute to the reasoning roles let the
  * society sign at all at p = 0.7, where all-Haiku signed nothing (`RunSpendTradeoff`), and is it
  * the closer or the search that binds? Three arms, seam-gated, p = 0.7, k = 1: W (all Haiku), S1
  * (strong driller+skeptic, cheap splitter), D (strong everything — the closer-vs-search
  * diagnostic). Run per tier: `sonnet` (default) then `opus`. The live diversity-vs-redundancy
  * tradeoff is DEFERRED (it needs a conditional denominator and ~200–370 games/cell); this run
  * records the base rate `w` that decides whether it is ever fundable. NOT hermetic — Anthropic API
  * key.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunStrongerCloser sonnet"}}}
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunStrongerCloser opus"}}}
  */
object RunStrongerCloser extends IOApp:

  private val Haiku: Model = AnthropicLlmCall.DefaultModel
  private val truthModel: Model = Model.CLAUDE_SONNET_5

  private val cfg = SocietyConfig(
    maxRounds = 16,
    roundTimeout = 30.seconds,
    hardDeadline = 5.minutes,
    corroborationSigns = false // seam-gated: the oracle confirmation is the sole sign path
  )
  private val devNames = List("dog", "apple", "chair", "spoon", "book", "tree", "cup", "shoe")
  private val n = 8 // N = 8/target = 64/arm — the pre-registered power floor (top-up to 16 by hand)
  private val concurrency = 6
  private val DriftThreshold = 0.12

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(RuntimeException(_)))

  def run(args: List[String]): IO[ExitCode] =
    args.headOption.map(_.toLowerCase) match
      case Some("opus") => runTier(Model.CLAUDE_OPUS_4_8, "opus")
      case Some("sonnet") | None => runTier(Model.CLAUDE_SONNET_5, "sonnet")
      case Some(other) =>
        // Fail on an unrecognized tier rather than silently defaulting to Sonnet — the Opus spend
        // is high enough that a typo must not quietly run the wrong tier.
        IO.println(s"unknown strong tier '$other' — use: sonnet | opus").as(ExitCode.Error)

  private def runTier(strongTier: Model, tierName: String): IO[ExitCode] =
    AnthropicLlmCall.clientResource.use { client =>
      // Per-role model allocation: strong for the arm's strong ids, Haiku otherwise. Derived from
      // the typed AgentStrategy role ids (never bare literals), so a broken cohort shrinks the set
      // and trips the startup assertion rather than silently falling back to Haiku.
      def societyLlm(strongIds: Set[AgentId]): AgentId => LlmCall[AgentMoveDto] =
        id =>
          val model = if strongIds.contains(id) then strongTier else Haiku
          AnthropicLlmCall(client, classOf[AgentMoveDto], model = model)
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth: TruthOracle =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = truthModel))

      val closerAdversary = List(AgentStrategy.closerId, AgentStrategy.adversaryId).flatten.toSet
      val allThree =
        List(
          AgentStrategy.closerId,
          AgentStrategy.adversaryId,
          AgentStrategy.proposerId
        ).flatten.toSet

      val cellW = SweepCell(0.7, "correlated", "W-weak", k = 1, rho = 0.0)
      val cellS1 = SweepCell(0.7, "correlated", s"S1-$tierName", k = 1, rho = 0.0)
      val cellD = SweepCell(0.7, "correlated", s"D-$tierName", k = 1, rho = 0.0)
      // The allocation-INDEPENDENT surface stamp: prompts/grader/nudge/budget/k/oracle. Identical
      // across W/S1/D and across the sonnet/opus runs iff no code changed — the shared-W condition.
      val surfaceStamp = ConfigStamp.of(cfg, cellW, "SURFACE", truthModel.toString)
      val resultsDir = Path.of("results", s"stronger-closer-$tierName-$surfaceStamp")

      // Fail-closed startup check: the arm's strong set must be exactly the intended roles — else a
      // mis-wired allocation would reproduce the null and be misread as "the strong tier did not
      // unblock". Log the per-agent allocation before any spend.
      def assertAllocation(label: String, strongIds: Set[AgentId], expected: Int): IO[Unit] =
        val roster = AgentStrategy.cohort
          .map(s => s"${s.id.value}=${if strongIds.contains(s.id) then "STRONG" else "haiku"}")
          .mkString(", ")
        IO.println(
          s"  [$label] allocation: $roster  (strong=${strongIds.size}, expect $expected)"
        ) *>
          IO.raiseUnless(strongIds.size == expected && strongIds.subsetOf(allThree))(
            RuntimeException(s"$label allocation mis-wired: strong=$strongIds, expected $expected")
          )

      def runArm(
          label: String,
          cell: SweepCell,
          strongIds: Set[AgentId]
      ): IO[List[(GameRecord, Vector[Event])]] =
        for
          targets <- devNames.traverse(answer)
          entries <- OracleSweep.sweepWithLogs(
            IO.pure(societyLlm(strongIds)),
            cfg,
            List(cell),
            targets.map(t => (t, truth)),
            n,
            concurrency,
            _ => definer
          )
          _ <- Archiver.archive(resultsDir, cell.difficulty, entries)
        yield entries

      for
        _ <- IO.println(s"=== STRONGER-CLOSER TRIAL — $tierName strong tier (LIVE) ===")
        _ <- IO.println(
          s"strong=$strongTier  proposer(splitter)=Haiku unless arm D  truth=$truthModel  seam-GATED  p=0.7 correlated  k=1  N=${n}x${devNames.size}=${n * devNames.size}/arm"
        )
        _ <- IO.println(
          s"surface stamp: $surfaceStamp (allocation-independent — must match across the sonnet/opus runs); archiving under $resultsDir"
        )
        _ <- assertAllocation("W", Set.empty, 0)
        _ <- assertAllocation("S1", closerAdversary, 2)
        _ <- assertAllocation("D", allThree, 3)
        w <- runArm("W", cellW, Set.empty)
        s1 <- runArm("S1", cellS1, closerAdversary)
        d <- runArm("D", cellD, allThree)
        _ <- report(tierName, w, s1, d)
      yield ExitCode.Success
    }

  /** The pre-registered readout: the powered unblock test (Newcombe on S1−W), the drift alarm, the
    * closer-vs-search diagnostic (D−S1), the base rate `w`, and the outcome classification.
    */
  private def report(
      tierName: String,
      w: List[(GameRecord, Vector[Event])],
      s1: List[(GameRecord, Vector[Event])],
      d: List[(GameRecord, Vector[Event])]
  ): IO[Unit] =
    def correct(e: List[(GameRecord, Vector[Event])]): Int =
      e.count(_._1.outcome == PrimaryOutcome.SignCorrect)
    def wrongGuessRate(e: List[(GameRecord, Vector[Event])]): Double =
      if e.isEmpty then 0.0
      else e.count((_, log) => Adjudication.wrongGuessReachedConfirmation(log)).toDouble / e.size
    val (nW, nS1, nD) = (w.size, s1.size, d.size)
    val (cW, cS1, cD) = (correct(w), correct(s1), correct(d))
    val allRecs = (w ++ s1 ++ d).map(_._1)
    val (ubLo, ubHi) = ProportionDiff.newcombe95(cS1, nS1, cW, nW)
    val (dgLo, dgHi) = ProportionDiff.newcombe95(cD, nD, cS1, nS1)
    val (dwLo, dwHi) = ProportionDiff.newcombe95(cD, nD, cW, nW)
    val wRate = if nW == 0 then 0.0 else cW.toDouble / nW
    val drift = wRate > DriftThreshold
    // The adjudication is the tested, direction-aware classifier — never a conclusion opposite its
    // own intervals, and D is consulted even when S1 fails to clear W (the search-binds branch).
    val outcome = StrongerCloserOutcome.message(
      StrongerCloserOutcome.classify(cW, nW, cS1, nS1, cD, nD, DriftThreshold)
    )
    for
      _ <- IO.println(s"\n=== PRE-REGISTERED READOUT ($tierName tier) ===")
      _ <- IO.println(OracleSweep.renderPrimary(allRecs))
      _ <- IO.println(
        f"\ncorrect: W=$cW/$nW  S1=$cS1/$nS1  D=$cD/$nD"
      )
      _ <- IO.println(
        f"UNBLOCK    (Newcombe S1−W): [${ubLo}%.3f, ${ubHi}%.3f] ${
            if ubLo > 0 then "S1 > W -> unblocked"
            else if ubHi < 0 then "S1 < W (anomaly)"
            else "includes 0 -> not distinguishable"
          }"
      )
      _ <- IO.println(
        f"DIAGNOSTIC (Newcombe D−S1): [${dgLo}%.3f, ${dgHi}%.3f] ${
            if dgLo > 0 then "D > S1 -> search binds"
            else if dgHi < 0 then "D < S1 (anomaly)"
            else "includes 0 -> closer captures the gain"
          }"
      )
      _ <- IO.println(
        f"           (Newcombe D−W):  [${dwLo}%.3f, ${dwHi}%.3f] ${
            if dwLo > 0 then "D > W" else "includes 0"
          }"
      )
      _ <- IO.println(
        f"DRIFT: W rate ${wRate}%.3f vs threshold $DriftThreshold ${
            if drift then "-> DRIFT CAVEAT" else "-> within weak band"
          }"
      )
      _ <- IO.println(
        f"w (wrong-guess-reached-confirmation): W=${wrongGuessRate(w)}%.3f  S1=${wrongGuessRate(s1)}%.3f  D=${wrongGuessRate(d)}%.3f  (the deferred tradeoff's base rate)"
      )
      _ <- IO.println(s"\n$outcome")
    yield ()
