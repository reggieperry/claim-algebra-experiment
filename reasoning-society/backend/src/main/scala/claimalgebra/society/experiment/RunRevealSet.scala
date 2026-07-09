package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, CallError, LlmCall}
import com.anthropic.models.messages.Model

import java.nio.file.Path
import scala.concurrent.duration.*

/** The reveal-the-set cell (docs/reasoning-society/reveal-the-set-cell.md): separate the
  * stronger-closer null's channel term from its decoder term by collapsing the decoder's job. Two
  * arms at p = 0.7, all-Sonnet, seam-gated — A (set withheld, the current society, reproduces the
  * D-Sonnet null) and B (the eight-word candidate set revealed to every agent as a trusted
  * system-prompt preamble, so the open search becomes the true 3-bit identification). If B unblocks
  * where A does not, the open search was the binding constraint (decoder); if neither unblocks, the
  * channel binds even the collapsed task. NOT hermetic — Anthropic API key.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunRevealSet"}}}
  */
object RunRevealSet extends IOApp.Simple:

  private val strongTier: Model =
    Model.CLAUDE_SONNET_5 // the best decoder the trial found; Opus adds nothing
  private val truthModel: Model = Model.CLAUDE_SONNET_5

  private val cfg = SocietyConfig(
    maxRounds = 16,
    roundTimeout = 30.seconds,
    hardDeadline = 5.minutes,
    corroborationSigns = false
  )
  private val devNames = List("dog", "apple", "chair", "spoon", "book", "tree", "cup", "shoe")
  private val n = 8
  private val concurrency = 6

  /** The trusted reveal: the candidate SET (never the sealed target), spliced ahead of each agent's
    * fixed rubric. Trusted experimenter content, so the system prompt is the right home
    * (scala-security: only untrusted content is barred from the system prompt).
    */
  private[experiment] val setPreamble =
    "IMPORTANT: the hidden thing is exactly ONE of these eight, and no other: " +
      devNames.mkString(", ") + ". Your task is to identify WHICH one it is."

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(RuntimeException(_)))

  /** Wrap an [[LlmCall]] to prepend a trusted preamble to the system prompt (the reveal). */
  private[experiment] def withPreamble[A](inner: LlmCall[A], preamble: String): LlmCall[A] =
    new LlmCall[A]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, A]] =
        inner.call(preamble + "\n\n" + systemPrompt, userMessage)

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      def base: LlmCall[AgentMoveDto] =
        AnthropicLlmCall(client, classOf[AgentMoveDto], model = strongTier)
      val withheld: AgentId => LlmCall[AgentMoveDto] = _ => base
      val revealed: AgentId => LlmCall[AgentMoveDto] = _ => withPreamble(base, setPreamble)
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth: TruthOracle =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = truthModel))

      val cellA = SweepCell(0.7, "correlated", "A-withheld", k = 1, rho = 0.0)
      val cellB = SweepCell(0.7, "correlated", "B-revealed", k = 1, rho = 0.0)
      val surfaceStamp = ConfigStamp.of(cfg, cellA, "SURFACE", truthModel.toString)
      val resultsDir = Path.of("results", s"reveal-the-set-$surfaceStamp")

      def runArm(
          cell: SweepCell,
          llmFor: AgentId => LlmCall[AgentMoveDto]
      ): IO[List[(GameRecord, Vector[Event])]] =
        for
          targets <- devNames.traverse(answer)
          entries <- OracleSweep.sweepWithLogs(
            IO.pure(llmFor),
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
        _ <- IO.println("=== REVEAL-THE-SET CELL — channel vs decoder (LIVE) ===")
        _ <- IO.println(
          s"society=all-$strongTier  truth=$truthModel  seam-GATED  p=0.7 correlated  k=1  N=${n}x${devNames.size}=${n * devNames.size}/arm"
        )
        _ <- IO.println(s"reveal preamble (arm B): \"$setPreamble\"")
        _ <- IO.println(
          s"surface stamp (allocation/preamble-independent): $surfaceStamp; archiving under $resultsDir"
        )
        a <- runArm(cellA, withheld)
        b <- runArm(cellB, revealed)
        _ <- report(a, b)
      yield ()
    }

  private def report(
      a: List[(GameRecord, Vector[Event])],
      b: List[(GameRecord, Vector[Event])]
  ): IO[Unit] =
    def correct(e: List[(GameRecord, Vector[Event])]): Int =
      e.count(_._1.outcome == PrimaryOutcome.SignCorrect)
    def wrongGuessRate(e: List[(GameRecord, Vector[Event])]): Double =
      if e.isEmpty then 0.0
      else e.count((_, log) => Adjudication.wrongGuessReachedConfirmation(log)).toDouble / e.size
    val (nA, nB) = (a.size, b.size)
    val (cA, cB) = (correct(a), correct(b))
    val (lo, hi) = ProportionDiff.newcombe95(cB, nB, cA, nA) // B − A
    val bAboveA = lo > 0.0
    val outcome =
      if bAboveA then
        "READING — DECODER-bound: revealing the set UNBLOCKS (B > A), so the open search was the binding constraint at p=0.7; the stronger-closer null was capability/protocol, not channel."
      else if hi < 0 then
        "READING — ANOMALY: B < A (revealing the set HURTS) — not adjudicable from rates; read the archived logs."
      else if cB == 0 then
        "READING — CHANNEL/deep-bound: revealing the set does NOT unblock (B ~= 0), so collapsing the search to 8 candidates leaves the blocker in place — the channel binds even the 3-bit task (or a deep decoding limit survives the collapse)."
      else
        "READING — B > 0 but not distinguishable from A at this N: revealing the set produced some wins but the difference includes 0; report both, consider the top-up."
    for
      _ <- IO.println("\n=== PRE-REGISTERED READOUT (reveal-the-set) ===")
      _ <- IO.println(OracleSweep.renderPrimary((a ++ b).map(_._1)))
      _ <- IO.println(f"\ncorrect: A(withheld)=$cA/$nA  B(revealed)=$cB/$nB")
      _ <- IO.println(
        f"CONTRAST (Newcombe B−A): [$lo%.3f, $hi%.3f] ${
            if bAboveA then "B > A -> reveal unblocks (decoder)"
            else if hi < 0 then "B < A (anomaly)"
            else "includes 0 -> not distinguishable"
          }"
      )
      _ <- IO.println(
        f"w (wrong-guess-reached-confirmation): A=${wrongGuessRate(a)}%.3f  B=${wrongGuessRate(b)}%.3f  (search-reach; expect B >> A if the set collapses the search)"
      )
      _ <- IO.println(s"\n$outcome")
    yield ()
