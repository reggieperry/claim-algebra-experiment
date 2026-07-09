package claimalgebra.society
package experiment

import cats.effect.syntax.all.*
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** E0 — the endgame diagnostic (fallible-oracle-interpretation §E0, the prerequisite). Sign-correct
  * was zero at every cell of the primary sweep, even at a perfect oracle on an easy target, so
  * "appropriately cautious" and "broken endgame" are observationally identical in that data. This
  * run separates the diseases by reading the logs: for each game at a PERFECT oracle on EASY
  * targets, did B1 ever pose a guess, and if so was any guess the target?
  *
  *   - never posed a guess → a convergence / B1-trigger failure (never reached a lone candidate to
  *     guess),
  *   - posed a guess, never correct → a search failure (the society narrows to the wrong thing),
  *   - posed the correct guess but did not sign → an endgame/confirmation-wiring failure,
  *   - correct signs appear once the budget loosens → the win path was merely starved.
  *
  * Contrasting a tight (12) and a loose (30) round budget tells starvation apart from a structural
  * dead path. Live and bounded; the point is the qualitative diagnosis, not tight rates.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunEndgameDiagnostic"}}}
  */
object RunEndgameDiagnostic extends IOApp.Simple:

  private def cfg(maxRounds: Int): SocietyConfig =
    SocietyConfig(maxRounds, roundTimeout = 30.seconds, hardDeadline = 8.minutes)

  private val budgets = List(12, 30)
  private val targetNames = List("dog", "water", "chair")
  private val n = 4

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      for
        targets <- targetNames.traverse(answer)
        jobs =
          for
            b <- budgets
            t <- targets
            i <- (1 to n).toList
          yield (b, t, i)
        _ <- IO.println(
          "=== E0 endgame diagnostic — LIVE (Haiku society, Sonnet truth), p=1.0 (perfect oracle)," +
            s" easy targets ${targetNames.mkString("{", ",", "}")}, N=$n/target/budget ==="
        )
        results <- jobs.parTraverseN(5) { (b, t, i) =>
          val cell = SweepCell(1.0, "perfect", s"b$b", k = 1)
          OracleSweep
            .runOneGame(
              societyLlm,
              cfg(b),
              cell,
              t,
              truth,
              seed = (b * 1000 + i).toLong,
              _ => definer
            )
            .map((rec, log) => (b, rec, log))
        }
        _ <- IO.println(render(results))
      yield ()
    }

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(m => RuntimeException(m)))

  private def render(results: List[(Int, GameRecord, Vector[Event])]): String =
    val header =
      f"${"budget"}%7s ${"N"}%4s ${"posed guess"}%12s ${"posed CORRECT"}%14s ${"sign-correct"}%13s ${"abstain"}%9s ${"fail-open"}%10s"
    val rows = results.groupBy(_._1).toList.sortBy(_._1).map { (b, games) =>
      val total = games.size
      def pct(c: Int): String = f"${c.toDouble / total}%.2f ($c/$total)"
      val posed = games.count((_, _, log) => Adjudication.guessesPosed(log).nonEmpty)
      val posedCorrect =
        games.count((_, rec, log) => Adjudication.guessesPosed(log).contains(rec.trueTarget))
      val correct = games.count((_, rec, _) => rec.outcome == PrimaryOutcome.SignCorrect)
      val abstain = games.count((_, rec, _) => rec.outcome == PrimaryOutcome.Abstain)
      val failOpen = games.count((_, rec, _) => rec.outcome == PrimaryOutcome.SignWrong)
      f"$b%7d $total%4d ${pct(posed)}%12s ${pct(posedCorrect)}%14s ${pct(correct)}%13s ${pct(abstain)}%9s ${pct(failOpen)}%10s"
    }
    (header :: rows).mkString("\n")
