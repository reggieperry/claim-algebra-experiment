package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** An honesty audit of the endgame fix: is the improved win rate GENUINE identification, or an
  * artifact of the loosened grader and prompts tuned on the targets I looked at? Two guards against
  * self-deception:
  *
  *   1. HELD-OUT targets — none used while developing the fixes or in the win-rate run, so a win
  *      here cannot be overfitting to a target I inspected.
  *   2. EXACT vs LOOSE grading on the SAME game — for every signed candidate it reports both the
  *      exact-string verdict and the `TargetMatch` verdict, and prints WHAT was signed, so a human
  *      can read whether each win is a real identification ("banana" for banana, "domestic dog" for
  *      dog) or a lenient-match credit for a miss.
  *
  * A win rate that survives exact grading and holds on held-out targets is real; one that collapses
  * to loose-grading-only on held-out targets was inflated. (Caveat: for an ORACLE-confirmed sign
  * the loosened guess-truth also changed the trajectory, so the exact column understates a
  * fully-strict run only for those; the printed signs let a reader judge.)
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunEndgameAudit"}}}
  */
object RunEndgameAudit extends IOApp.Simple:

  private val cfg =
    SocietyConfig(maxRounds = 16, roundTimeout = 30.seconds, hardDeadline = 5.minutes)

  // HELD-OUT: concrete common nouns NOT used in development, the diagnostic, or the win-rate run.
  private val heldOut =
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
  private val n = 2

  private def exactMatch(signed: Answer, target: Answer): Boolean =
    signed.value.trim.toLowerCase == target.value.trim.toLowerCase

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      val cell = SweepCell(1.0, "systematic", "audit", k = 1)
      for
        targets <- heldOut.traverse(t => IO.fromEither(Answer.from(t).leftMap(RuntimeException(_))))
        pairs = targets.map(t => (t, truth))
        _ <- IO.println(
          s"=== endgame HONESTY AUDIT — held-out targets $heldOut, p=1.0, N=$n/target ===\n" +
            "per game: outcome | signed | exact-correct? | loose(TargetMatch)-correct?\n"
        )
        records <- OracleSweep.sweep(
          IO.pure(societyLlm),
          cfg,
          List(cell),
          pairs,
          gamesPerCell = n,
          concurrency = 5,
          definerFor = _ => definer
        )
        _ <- records.traverse_ { r =>
          val signedStr = r.signed.map(_.value).getOrElse("-")
          val exact = r.signed.exists(s => exactMatch(s, r.trueTarget))
          val loose = r.outcome == PrimaryOutcome.SignCorrect
          IO.println(
            f"  target=${r.trueTarget.value}%-10s | ${r.outcome}%-11s | signed=$signedStr%-16s | exact=${
                if exact then "Y" else "N"
              } loose=${if loose then "Y" else "N"}"
          )
        }
        looseCorrect = records.count(_.outcome == PrimaryOutcome.SignCorrect)
        exactCorrect = records.count(r => r.signed.exists(s => exactMatch(s, r.trueTarget)))
        wrong = records.count(_.outcome == PrimaryOutcome.SignWrong)
        abstain = records.count(_.outcome == PrimaryOutcome.Abstain)
        _ <- IO.println(
          s"\nHELD-OUT SUMMARY (${records.size} games): loose-correct=$looseCorrect exact-correct=$exactCorrect" +
            s" wrong=$wrong abstain=$abstain"
        )
      yield ()
    }
