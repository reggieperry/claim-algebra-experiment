package claimalgebra.society
package experiment

import cats.effect.syntax.all.*
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}

/** The coordinates of one sweep point (fallible-oracle-experiment-design §Independent variables).
  * `errorModel` names the adversary: `"perfect"`, `"independent-uniform"`, `"systematic"`.
  */
final case class SweepCell(reliability: Double, errorModel: String, difficulty: String)

/** One trial's record: the cell, the sealed truth, what (if anything) was signed, the adjudicated
  * primary outcome, and the seed (for bit-reproducibility).
  */
final case class GameRecord(
    cell: SweepCell,
    trueTarget: Answer,
    signed: Option[Answer],
    outcome: PrimaryOutcome,
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
        config,
        definerFor = definerFor,
        initial = initial
      )
      emitted <- getEvents
      fullLog = initial.log ++ emitted
      primary <- IO.fromEither(Adjudication.classify(fullLog).leftMap(m => RuntimeException(m)))
    yield (GameRecord(cell, target, Adjudication.signed(fullLog), primary, seed), fullLog)

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
