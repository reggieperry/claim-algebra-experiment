package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** The endgame-fix win-rate check: the society at a PERFECT oracle over several easy targets, after
  * the fixes (semantic target-match, a decisive truth oracle, round-budget awareness + endgame
  * commitment). The E0 diagnostic found sign-correct = 0 across the board; this measures whether
  * the fixes let the society actually win. Reports the outcome split (correct / abstain /
  * fail-open) via `renderPrimary`.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunWinRate"}}}
  */
object RunWinRate extends IOApp.Simple:

  private val cfg =
    SocietyConfig(maxRounds = 16, roundTimeout = 30.seconds, hardDeadline = 5.minutes)

  private val targetNames = List("dog", "apple", "chair", "spoon", "book", "tree", "cup", "shoe")
  private val n = 4

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      val cell = SweepCell(1.0, "systematic", "win", k = 1)
      for
        targets <- targetNames.traverse(t =>
          IO.fromEither(Answer.from(t).leftMap(RuntimeException(_)))
        )
        pairs = targets.map(t => (t, truth))
        _ <- IO.println(
          s"=== endgame win-rate — LIVE (Haiku society, Sonnet truth), p=1.0, targets=$targetNames," +
            s" N=$n/target, budget=${cfg.maxRounds} ==="
        )
        records <- OracleSweep.sweep(
          IO.pure(societyLlm),
          cfg,
          List(cell),
          pairs,
          gamesPerCell = n,
          concurrency = 6,
          definerFor = _ => definer
        )
        _ <- IO.println(OracleSweep.renderPrimary(records))
        correct = records.count(_.outcome == PrimaryOutcome.SignCorrect)
        wrong = records.count(_.outcome == PrimaryOutcome.SignWrong)
        abstain = records.count(_.outcome == PrimaryOutcome.Abstain)
        _ <- IO.println(
          s"\nTOTAL ${records.size} games: correct=$correct wrong=$wrong abstain=$abstain"
        )
      yield ()
    }
