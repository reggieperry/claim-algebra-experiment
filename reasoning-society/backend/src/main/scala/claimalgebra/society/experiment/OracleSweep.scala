package claimalgebra.society
package experiment

import cats.effect.syntax.all.*
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}

/** The coordinates of one sweep point (fallible-oracle-experiment-design §Independent variables).
  * `errorModel` names the adversary: `"perfect"`, `"independent-uniform"`, `"systematic"`,
  * `"correlated"`. `k` is the confirmation quorum (Slice 4) and `rho` the confirmation correlation
  * (used only by the `"correlated"` model); both default to the shipped single-confirmation game.
  */
final case class SweepCell(
    reliability: Double,
    errorModel: String,
    difficulty: String,
    k: Int = 1,
    rho: Double = 0.0
)

/** One trial's record: the cell, the sealed truth, what (if anything) was signed, HOW it signed
  * (the sign-disjunct attribution — Slice 4, so k-invariant backer signs stay out of the redundancy
  * curve), the adjudicated primary outcome, and the seed (for bit-reproducibility).
  */
final case class GameRecord(
    cell: SweepCell,
    trueTarget: Answer,
    signed: Option[Answer],
    outcome: PrimaryOutcome,
    signPath: Option[SignPath],
    seed: Long
)

/** The three headline rates for one cell, each with its Wilson interval. */
final case class CellReport(failOpen: Rate, abstain: Rate, signCorrect: Rate, n: Int)

/** The headless batch runner (fallible-oracle-build-plan §Slice 2). One `Society.play` per game —
  * no HTTP — with a fallible [[ExperimentOracle]], a collecting sink, and the sealed truth seeded
  * via the `initial: LogState` param; classification is a pure fold over the collected log.
  */
object OracleSweep:

  /** A fail-closed definer for games with no clarification (mirrors `Society.noDefiner`). */
  private val noDefiner: LlmCall[DefinitionDto] = new LlmCall[DefinitionDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, DefinitionDto]] =
      IO.pure(Left(CallError.Malformed("no definer wired for the oracle sweep")))

  def modelFor(cell: SweepCell, seed: Long): ErrorModel = cell.errorModel match
    case "independent-uniform" => ErrorModel.IndependentUniform(cell.reliability)
    case "systematic" => ErrorModel.SystematicPerQuestion(cell.reliability, seed)
    case "correlated" => ErrorModel.CorrelatedConfirmations(cell.reliability, cell.rho, seed)
    case "gap-guess" => ErrorModel.GapAt(gapGuesses = true, gapProperties = false)
    case "gap-property" => ErrorModel.GapAt(gapGuesses = false, gapProperties = true)
    case _ => ErrorModel.perfect

  private def collectingSink: IO[(EventSink, IO[Vector[Event]])] =
    Ref[IO].of(Vector.empty[Event]).map { ref =>
      val sink: EventSink = event => ref.update(_ :+ event)
      (sink, ref.get)
    }

  /** Run ONE headless game and adjudicate it. Seed the sealed truth at the head of the log (via the
    * `initial` LogState — the marker is belief-inert and cannot reach the agents), play with the
    * fallible oracle and a collecting sink, then classify the full self-contained log (`initial.log
    * ++ emitted`, so the truth marker is present for the fold). Returns the record AND the full log
    * (the discovery substrate — read the SignWrong logs).
    */
  def runOneGame(
      llmFor: AgentId => LlmCall[AgentMoveDto],
      config: SocietyConfig,
      cell: SweepCell,
      target: Answer,
      truth: TruthOracle,
      seed: Long,
      definerFor: AgentId => LlmCall[DefinitionDto] = _ => noDefiner
  ): IO[(GameRecord, Vector[Event])] =
    for
      oracle <- ExperimentOracle.make(target, truth, modelFor(cell, seed), seed)
      sinkAndGet <- collectingSink
      (sink, getEvents) = sinkAndGet
      initial = LogState.initial.copy(log = Vector(Event.TargetRegistered(1, 0L, target, seed)))
      _ <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        oracle,
        sink,
        config.copy(k = cell.k), // the cell's confirmation quorum drives this game
        definerFor = definerFor,
        initial = initial
      )
      emitted <- getEvents
      fullLog = initial.log ++ emitted
      primary <- IO.fromEither(Adjudication.classify(fullLog).leftMap(m => RuntimeException(m)))
    yield (
      GameRecord(
        cell,
        target,
        Adjudication.signed(fullLog),
        primary,
        Adjudication.signPath(fullLog),
        seed
      ),
      fullLog
    )

  /** The sweep grid: for each `(cell, target)` run `gamesPerCell` games with BOUNDED fan-out —
    * never unbounded, since each game already fans out ~3 agent calls per round (rate-limit
    * caution, fallible-oracle-build-plan §Slice 2). Seeds are derived deterministically from the
    * coordinates.
    *
    * `llmFactory` yields a cohort PER GAME: `IO.pure(sharedLlm)` for stateless live Haiku (one
    * client reused across every game), or a fresh scripted cohort for a hermetic run (each stub
    * carries a per-game cursor, so it must not be shared).
    */
  def sweep(
      llmFactory: IO[AgentId => LlmCall[AgentMoveDto]],
      config: SocietyConfig,
      cells: List[SweepCell],
      targets: List[(Answer, TruthOracle)],
      gamesPerCell: Int,
      concurrency: Int,
      definerFor: AgentId => LlmCall[DefinitionDto] = _ => noDefiner
  ): IO[List[GameRecord]] =
    val jobs =
      for
        cell <- cells
        (target, truth) <- targets
        i <- (1 to gamesPerCell).toList
      yield llmFactory.flatMap { llmFor =>
        runOneGame(llmFor, config, cell, target, truth, seedFor(cell, target, i), definerFor).map(
          _._1
        )
      }
    jobs.parTraverseN(concurrency)(identity)

  private def seedFor(cell: SweepCell, target: Answer, i: Int): Long =
    scala.util.hashing.MurmurHash3
      .stringHash(s"${cell.reliability}:${cell.errorModel}:${target.value}:$i")
      .toLong

  /** The three-rate report per cell — the primary curves' raw material. */
  def summarize(records: List[GameRecord]): Map[SweepCell, CellReport] =
    records.groupBy(_.cell).map { (cell, rs) =>
      val n = rs.size
      cell -> CellReport(
        failOpen = Rate(rs.count(_.outcome == PrimaryOutcome.SignWrong), n),
        abstain = Rate(rs.count(_.outcome == PrimaryOutcome.Abstain), n),
        signCorrect = Rate(rs.count(_.outcome == PrimaryOutcome.SignCorrect), n),
        n = n
      )
    }

  /** A compact one-line-per-event render of a game log — the discovery-loop tool
    * (fallible-oracle-experiment-design §Secondary: inspect the fail-open logs) and a diagnostic
    * for why a game did/didn't reach a signature. Shows the moves that matter (asserts, Q&A, the
    * guess, the sign); the truth marker is shown as `[truth: …]` (harness-facing only).
    */
  def renderLog(log: Vector[Event]): String =
    log
      .map { e =>
        val what = e match
          case Event.TargetRegistered(_, _, t, _) => s"[truth: ${t.value}]"
          case Event.Assert(_, _, a, c, _) => s"${a.value} asserts \"${c.value}\""
          case Event.Corroborate(_, _, a, c, _) => s"${a.value} corroborates \"${c.value}\""
          case Event.Refute(_, _, a, c, _) => s"${a.value} refutes \"${c.value}\""
          case Event.Strike(_, _, a, c, _) => s"${a.value} strikes \"${c.value}\""
          case Event.QuestionProposed(_, _, a, q, t) =>
            s"${a.value} proposes ${q.value}: ${t.take(52)}"
          case Event.QuestionAsked(_, _, _, q, t) => s"asks ${q.value}: ${t.take(52)}"
          case Event.AnswerGiven(_, _, q, ans, _) =>
            s"oracle ${q.value} = ${ans.toString.toUpperCase}"
          case Event.GuessAnswered(_, _, c, ans) =>
            s"GUESS \"${c.value}\" = ${ans.toString.toUpperCase}"
          case Event.GateSign(_, _, c) => s"*** SIGN \"${c.value}\" ***"
          case Event.GateAbstain(_, _, r) => s"abstain — ${r.take(48)}"
          case Event.ConvergenceWarning(_, _, rc, gp) =>
            s"convergence-warning (rounds=$rc glut=$gp)"
          case other => other.getClass.getSimpleName
        f"  ${e.seq}%3d  $what"
      }
      .mkString("\n")

  /** A plain-text render of the report — fail-open leads (the cardinal metric), each with its CI.
    */
  def render(report: Map[SweepCell, CellReport]): String =
    def fmt(r: Rate): String =
      val (lo, hi) = r.ci95
      f"${r.point}%.2f[${lo}%.2f-${hi}%.2f]"
    val header =
      f"${"p"}%5s ${"model"}%20s ${"diff"}%8s ${"N"}%4s ${"FAIL-OPEN"}%16s ${"abstain"}%16s ${"correct"}%16s"
    val rows = report.toList
      .sortBy(r => (r._1.errorModel, -r._1.reliability, r._1.difficulty))
      .map { (cell, rep) =>
        f"${cell.reliability}%5.2f ${cell.errorModel}%20s ${cell.difficulty}%8s ${rep.n}%4d ${fmt(rep.failOpen)}%16s ${fmt(rep.abstain)}%16s ${fmt(rep.signCorrect)}%16s"
      }
    (header :: rows).mkString("\n")

  /** The correlation-curve report (fallible-oracle Slice 4). It does NOT claim to *measure* real
    * check correlation: `rho` is an ASSUMED, swept sweep-parameter, so this validates the
    * *consequence* of a given correlation end to end, not its real-world magnitude. Per `(k, rho)`
    * cell it reports the ORACLE-CONFIRMED fail-open rate — `SignWrong` via the k-quorum disjunct,
    * read off `signPath`, so a k-invariant `BackerQuorum` sign is EXCLUDED by construction rather
    * than merely assumed absent (a future corroborator in the cohort can no longer silently pollute
    * the curve; any such sign is counted and flagged on a trailing line). In the lone-wrong-guess
    * harness that rate IS the joint probability that all `k` guess-confirmations corrupt, shown
    * beside the two analytic anchors: `(1-p)^k` (the `rho = 0` independent floor, where redundancy
    * pays) and `(1-p)` (the `rho = 1` monoculture, where correlated confirmations buy nothing).
    * This adds INTEGRATION confidence — a buggy re-pose loop would break the curve — over a closed
    * form a pure test already pins ([[ExperimentOracleSuite]]); it is not a numerical discovery.
    */
  def renderCorrelation(records: List[GameRecord], p: Double): String =
    val header =
      f"${"k"}%3s ${"rho"}%5s ${"N"}%6s ${"FAIL-OPEN (oracle-confirmed)"}%29s ${"(1-p)^k"}%9s ${"(1-p)"}%7s"
    val byCell = records.groupBy(_.cell).toList.sortBy((c, _) => (c.k, c.rho))
    val rows = byCell.map { (cell, rs) =>
      val n = rs.size
      // Only OracleConfirmed fail-opens count — the sign-disjunct attribution the curve depends on,
      // ENFORCED via signPath, not assumed from the cohort construction.
      val failOpen = Rate(
        rs.count(r =>
          r.outcome == PrimaryOutcome.SignWrong && r.signPath.contains(SignPath.OracleConfirmed)
        ),
        n
      )
      val (lo, hi) = failOpen.ci95
      val obs = f"${failOpen.point}%.3f [${lo}%.2f-${hi}%.2f]"
      val indep = math.pow(1.0 - p, cell.k.toDouble)
      f"${cell.k}%3d ${cell.rho}%5.2f ${n}%6d ${obs}%29s ${indep}%9.3f ${1.0 - p}%7.3f"
    }
    // Guard the load-bearing invariant: a BackerQuorum sign is k-invariant and would pollute the curve.
    val bogus = records.count(_.signPath.contains(SignPath.BackerQuorum))
    val note =
      if bogus == 0 then Nil
      else
        List(
          s"!! WARNING: $bogus BackerQuorum sign(s) — the cohort is no longer lone; curve polluted"
        )
    (header :: rows ++ note).mkString("\n")

  /** Arm 1's three-rate report, with the fail-open SPLIT BY SIGN PATH (fallible-oracle Arm 1). The
    * split is the finding: a fail-open via the 2-distinct-backer floor (`BackerQuorum`) is an
    * AGENT-agreement error the oracle never checks — present even at a perfect oracle — while a
    * fail-open via the oracle-confirmed path (`OracleConfirmed`) is a corrupted guess-confirmation,
    * the increment oracle degradation can add. Reporting them apart separates the agent-level
    * fail-open floor from the oracle-driven part.
    */
  def renderPrimary(records: List[GameRecord]): String =
    def ci(count: Int, n: Int): String =
      val r = Rate(count, n)
      val (lo, hi) = r.ci95
      f"${r.point}%.2f[$lo%.2f-$hi%.2f]"
    val header =
      f"${"p"}%5s ${"N"}%4s ${"FAIL-OPEN"}%16s ${"backer"}%7s ${"oracle"}%7s ${"abstain"}%16s ${"correct"}%16s"
    val rows = records.groupBy(_.cell).toList.sortBy((c, _) => -c.reliability).map { (cell, rs) =>
      val n = rs.size
      val fo = rs.count(_.outcome == PrimaryOutcome.SignWrong)
      val foBacker = rs.count(r =>
        r.outcome == PrimaryOutcome.SignWrong && r.signPath.contains(SignPath.BackerQuorum)
      )
      val foOracle = rs.count(r =>
        r.outcome == PrimaryOutcome.SignWrong && r.signPath.contains(SignPath.OracleConfirmed)
      )
      val ab = rs.count(_.outcome == PrimaryOutcome.Abstain)
      val cor = rs.count(_.outcome == PrimaryOutcome.SignCorrect)
      f"${cell.reliability}%5.2f ${n}%4d ${ci(fo, n)}%16s ${foBacker}%7d ${foOracle}%7d ${ci(ab, n)}%16s ${ci(cor, n)}%16s"
    }
    (header :: rows).mkString("\n")
