package claimalgebra.society
package experiment

import cats.effect.std.Random
import cats.effect.{IO, Ref}

/** The fallible oracle CHANNEL (fallible-oracle-experiment-design §split the oracle role). It
  * consults the sealed [[TruthOracle]] for the correct answer, applies the [[ErrorModel]], records
  * the draw for reproducibility/inspection, and returns the (possibly corrupted) [[HumanMove]] — a
  * plain [[Oracle]] impl passed to `Society.play`, so the experimenter/oracle-channel split lives
  * entirely behind `respond`. The sealed target lives HERE (outside the actor graph, like the
  * human), never in the society's view; `Event.TargetRegistered` in the log is only the
  * adjudication record.
  *
  * Guesses ("Is it X?") flow through the same `respond` as property questions, so the error model
  * corrupts the guess-confirmation too — the one place a corrupted `Yes` becomes a wrong signature.
  */
final class ExperimentOracle private (
    target: Answer,
    truth: TruthOracle,
    model: ErrorModel,
    rng: Random[IO],
    draws: Ref[IO, Vector[ExperimentOracle.Draw]]
) extends Oracle:

  def respond(question: Question): IO[HumanMove] =
    for
      truthful <- truth.truth(target, question)
      draw <- rng.nextDouble
      delivered = model.corrupt(question, truthful, draw)
      _ <- draws.update(_ :+ ExperimentOracle.Draw(question.id, truthful, delivered))
    yield HumanMove.Answer(delivered)

  /** The ordered per-question draws this oracle produced — the reproducibility/inspection record
    * the harness reads post-game (alongside the seed) to classify which answers were corrupted.
    */
  def recordedDraws: IO[Vector[ExperimentOracle.Draw]] = draws.get

object ExperimentOracle:

  /** One oracle draw: the question, the truthful answer, and what was actually delivered. */
  final case class Draw(questionId: QuestionId, truthful: OracleAnswer, delivered: OracleAnswer):
    def corrupted: Boolean = truthful != delivered

  /** Build a fresh fallible oracle for one game — its own seeded RNG and draw log. `seed` makes the
    * error draws bit-reproducible (record it alongside the target).
    */
  def make(
      target: Answer,
      truth: TruthOracle,
      model: ErrorModel,
      seed: Long
  ): IO[ExperimentOracle] =
    for
      rng <- Random.scalaUtilRandomSeedLong[IO](seed)
      draws <- Ref[IO].of(Vector.empty[Draw])
    yield new ExperimentOracle(target, truth, model, rng, draws)
