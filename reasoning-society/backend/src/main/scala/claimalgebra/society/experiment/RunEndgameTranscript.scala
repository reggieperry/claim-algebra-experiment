package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** A DIAGNOSTIC (not a measurement): print the full transcript of a few games on an easy target at
  * a perfect oracle, so the endgame failure (E0: reaches a guess ~25%, never the correct one) can
  * be READ rather than inferred from rates. Shows what the agents actually propose, assert, and
  * answer.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunEndgameTranscript"}}}
  */
object RunEndgameTranscript extends IOApp.Simple:

  private val cfg =
    SocietyConfig(maxRounds = 15, roundTimeout = 30.seconds, hardDeadline = 5.minutes)

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      val cell = SweepCell(1.0, "systematic", "diag", k = 1)
      for
        target <- IO.fromEither(Answer.from("dog").leftMap(m => RuntimeException(m)))
        _ <- IO.println(
          s"=== endgame transcript — target=dog, p=1.0, maxRounds=${cfg.maxRounds}, 3 games ==="
        )
        _ <- (0 until 3).toList.traverse_ { i =>
          OracleSweep
            .runOneGame(societyLlm, cfg, cell, target, truth, seed = i.toLong, _ => definer)
            .flatMap { case (rec, log) =>
              IO.println(
                s"\n--- game $i: outcome=${rec.outcome}, signed=${rec.signed}, path=${rec.signPath} ---\n" +
                  OracleSweep.renderLog(log)
              )
            }
        }
      yield ()
    }
