package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall}
import com.anthropic.models.messages.Model

import scala.concurrent.duration.*

/** E2 — the live seam-closure A/B (fallible-oracle-interpretation §E2). Re-runs the perfect-oracle
  * cell of the primary sweep under two configs — seam-OPEN (the shipped `verify = C ∨ O`) and
  * seam-GATED (`corroborationSigns = false`, dropping the standalone corroboration disjunct so a
  * 2-backer candidate must reach a ground-truth confirmation to sign). Seam-open reproduces the
  * perfect-oracle fail-open (the 2-backer wrong signs); seam-gated should drop it toward zero,
  * because at a perfect oracle a wrong candidate routed to the oracle is answered No and struck.
  * The fail-open is reported split by sign path; the headline is the `SignWrong` OUTCOME.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunSeamClosure"}}}
  */
object RunSeamClosure extends IOApp.Simple:

  private def cfg(corroborationSigns: Boolean): SocietyConfig =
    SocietyConfig(
      maxRounds = 12,
      roundTimeout = 30.seconds,
      hardDeadline = 4.minutes,
      corroborationSigns = corroborationSigns
    )

  private val n = 6

  def run: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val societyLlm: AgentId => LlmCall[AgentMoveDto] =
        _ => AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      val truth =
        ModelTruthOracle(AnthropicLlmCall(client, classOf[TruthDto], model = Model.CLAUDE_SONNET_5))
      val cell = SweepCell(1.0, "systematic", "seam", k = 1) // p=1.0 is a perfect oracle
      def arm(
          label: String,
          corroborationSigns: Boolean,
          pairs: List[(Answer, TruthOracle)]
      ): IO[Unit] =
        OracleSweep
          .sweep(
            IO.pure(societyLlm),
            cfg(corroborationSigns),
            List(cell),
            pairs,
            gamesPerCell = n,
            concurrency = 5,
            definerFor = _ => definer
          )
          .flatMap(records =>
            IO.println(s"--- $label ---\n" + OracleSweep.renderPrimary(records) + "\n")
          )
      for
        targets <- List("apple", "dog").traverse(answer)
        pairs = targets.map(t => (t, truth))
        _ <- IO.println(
          "=== E2 seam closure — LIVE (Haiku society, Sonnet truth), p=1.0, seam-OPEN vs seam-GATED," +
            s" targets={apple,dog}, N=$n/target/arm ==="
        )
        _ <- IO.println(
          "seam-open reproduces the perfect-oracle 2-backer fail-open; seam-gated should drop it to ~0.\n"
        )
        _ <- arm("seam-OPEN  (verify = C ∨ O)", corroborationSigns = true, pairs)
        _ <- arm("seam-GATED (verify → O)", corroborationSigns = false, pairs)
      yield ()
    }

  private def answer(raw: String): IO[Answer] =
    IO.fromEither(Answer.from(raw).leftMap(m => RuntimeException(m)))
