package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** The fully-measured verification-spend tradeoff (the good-but-imperfect band, on the winning
  * system). For a fixed verification budget, is fail-open lower by spending it on REDUNDANCY
  * (repeat the same checker) or on DIVERSITY (an independent checker)? The society is run
  * SEAM-GATED (`corroborationSigns = false`, so every signature goes through the confirmation — the
  * confirmation spend is then the sole lever, E2) at a degraded oracle (p = 0.7), under three
  * policies:
  *
  *   - spend 1: one confirmation (k = 1).
  *   - spend 2, REDUNDANCY: two same-family confirmations (k = 2) correlated at ρ = 0.75 — the
  *     error correlation E3 measured between two instances of one model.
  *   - spend 2, DIVERSITY: two independent confirmations (k = 2) at ρ = 0.0 — the ideal an
  *     uncorrelated (non-generative) second check approaches.
  *
  * Both sides of the tradeoff are reported: fail-open (wrong signs) AND useful-yield (correct signs
  * kept). Redundancy preserves useful-yield but leaves the fail-open correlated; diversity drives
  * the fail-open toward the geometric floor `(1−p)²` at a useful-yield cost. Every input is
  * measured — the win rate (live), the correlation ρ (E3), the reliability p.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunSpendTradeoff"}}}
  */
object RunSpendTradeoff extends IOApp.Simple:

  private val cfg =
    SocietyConfig(
      maxRounds = 16,
      roundTimeout = 30.seconds,
      hardDeadline = 5.minutes,
      corroborationSigns = false // seam-gated: the confirmation is the only sign path
    )

  private val targetNames = List("dog", "apple", "chair", "spoon")
  private val n = 4

  private val cells = List(
    SweepCell(0.7, "correlated", "spend1-k1", k = 1, rho = 0.0),
    SweepCell(0.7, "correlated", "spend2-redundancy-rho.75", k = 2, rho = 0.75),
    SweepCell(0.7, "correlated", "spend2-diversity-rho0", k = 2, rho = 0.0)
  )

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      for
        targets <- targetNames.traverse(t =>
          IO.fromEither(Answer.from(t).leftMap(RuntimeException(_)))
        )
        pairs = targets.map(t => (t, truth))
        _ <- IO.println(
          "=== verification-spend tradeoff — LIVE (Haiku society, Sonnet truth), SEAM-GATED, p=0.7," +
            s" targets=$targetNames, N=$n/target/policy ==="
        )
        _ <- IO.println(
          "spend 1 = k1; spend 2 redundancy = k2 rho=.75 (same-family, E3); spend 2 diversity = k2 rho=0 (independent).\n"
        )
        records <- OracleSweep.sweep(
          IO.pure(societyLlm),
          cfg,
          cells,
          pairs,
          gamesPerCell = n,
          concurrency = 6,
          definerFor = _ => definer
        )
        _ <- IO.println(OracleSweep.renderPrimary(records))
      yield ()
    }
