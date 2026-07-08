package claimalgebra.society
package experiment

import cats.effect.IO

/** The experimenter's GROUND TRUTH: the correct answer to a question about the sealed target
  * (fallible-oracle-experiment-design §split the oracle role). In a real run this is a
  * pre-registered property table (closed question-space, deterministic) or a held-fixed truthful
  * model call (open question-space) — the sharpest design decision (fallible-oracle-build-plan
  * §Decisions). IO-based so a model-backed truth is admissible; the table impl is pure.
  */
trait TruthOracle:
  def truth(target: Answer, question: Question): IO[OracleAnswer]

object TruthOracle:

  /** A pre-registered property table keyed on `(target, question text)`; FAIL-CLOSED to `Unknown`
    * (a genuine gap) for a question not in the table — the experimenter never guesses.
    */
  def table(entries: Map[(Answer, String), OracleAnswer]): TruthOracle =
    (target, q) => IO.pure(entries.getOrElse((target, q.text), OracleAnswer.Unknown))

  /** Any pure function, for tests and a closed synthetic question-space. */
  def pure(f: (Answer, Question) => OracleAnswer): TruthOracle =
    (target, q) => IO.pure(f(target, q))

/** How the oracle is WRONG (fallible-oracle-experiment-design §Independent variables — "the error
  * model is the adversary"). A PURE transform: given the question, the truthful answer, and a draw
  * in `[0, 1)`, return the (possibly corrupted) answer. A gap (`Unknown`) is never flipped — the
  * error model perturbs yes↔no, it does not invent knowledge.
  */
trait ErrorModel:
  def corrupt(question: Question, truthful: OracleAnswer, draw: Double): OracleAnswer

object ErrorModel:

  /** No corruption — the perfect oracle (`p = 1.0`). The trivially-satisfiable case the whole
    * experiment exists to break; the `p = 1.0` end of every sweep.
    */
  val perfect: ErrorModel = (_, truthful, _) => truthful

  /** Independent-uniform (the OPTIMISTIC, unrealistic case): each answer flips i.i.d. with
    * probability `1 − p`. Errors are uncorrelated, so redundancy and averaging work well — use it
    * only as an upper bound on how well the architecture could possibly do.
    */
  final case class IndependentUniform(p: Double) extends ErrorModel:
    def corrupt(question: Question, truthful: OracleAnswer, draw: Double): OracleAnswer =
      if draw < p then truthful else flip(truthful)

  /** Systematic-per-question (the HONEST case, lead with this): the oracle is wrong on a FIXED
    * `(1 − p)` fraction of questions and re-asking the same question yields the same (possibly
    * wrong) answer — a check with blind spots. The blind-spot decision is a stable hash of the
    * question text keyed by `seed`, so it IGNORES the per-answer draw and is deterministic on
    * re-ask.
    */
  final case class SystematicPerQuestion(p: Double, seed: Long) extends ErrorModel:
    def corrupt(question: Question, truthful: OracleAnswer, draw: Double): OracleAnswer =
      if blindSpot(question, seed, p) then flip(truthful) else truthful

  /** Correlated confirmations (fallible-oracle Slice 4 — THE redundancy/correlation study, Part-V
    * monoculture made measurable). The `k` re-poses of ONE guess draw their errors with correlation
    * `rho`: a candidate-stable COMMON-MODE latent (a Bernoulli(1−p) hashed from the question text +
    * `seed`, IDENTICAL across every pose of that guess) and a fresh per-pose INDEPENDENT component
    * (the `draw`). Each pose delivers the common error with probability `rho`, else its independent
    * error. The per-pose MARGINAL flip rate is `(1 − p)` at every `rho` (so per-question
    * reliability is unchanged); `rho` moves only the JOINT rate that all `k` poses flip together —
    * the fail-open. `rho = 0` → independent, joint ≈ `(1 − p)^k` (redundancy pays); `rho = 1` → the
    * SAME error every pose, joint ≈ `(1 − p)` (redundancy buys nothing — the monoculture failure).
    * The common-mode keys on the candidate-stable question (`guess-<c>`), which is exactly why the
    * guess QuestionId must NOT be de-collided per pose.
    */
  final case class CorrelatedConfirmations(p: Double, rho: Double, seed: Long) extends ErrorModel:
    def corrupt(question: Question, truthful: OracleAnswer, draw: Double): OracleAnswer =
      // draw < rho selects the correlated (common) error; otherwise an independent flip, taken from
      // the leftover uniform mass (draw − rho)/(1 − rho), so a single per-pose draw carries both the
      // mixing coin and the independent component. At rho = 1 the else branch is unreachable
      // (draw < 1 always), so there is no division by zero.
      val flipped =
        if draw < rho then CorrelatedConfirmations.commonUniform(question, seed) < (1.0 - p)
        else ((draw - rho) / (1.0 - rho)) < (1.0 - p)
      if flipped then flip(truthful) else truthful

  object CorrelatedConfirmations:
    /** The candidate-stable common-mode latent — a uniform in `[0, 1)` hashed from `(seed, question
      * text)`, identical across the `k` poses of one guess, so at `rho = 1` every pose flips
      * together.
      */
    private[experiment] def commonUniform(question: Question, seed: Long): Double =
      val h =
        scala.util.hashing.MurmurHash3.stringHash(s"$seed:common:${question.text}") & 0x7fffffff
      h.toDouble / Int.MaxValue.toDouble

  /** Flip yes↔no; a gap stays a gap (a corruption does not manufacture knowledge). */
  def flip(a: OracleAnswer): OracleAnswer = a match
    case OracleAnswer.Yes => OracleAnswer.No
    case OracleAnswer.No => OracleAnswer.Yes
    case OracleAnswer.Unknown => OracleAnswer.Unknown

  /** Stable per `(seed, question text)`: a `(1 − p)` fraction of questions fall in the blind spot.
    */
  private def blindSpot(question: Question, seed: Long, p: Double): Boolean =
    val h = scala.util.hashing.MurmurHash3.stringHash(s"$seed:${question.text}") & 0x7fffffff
    (h.toDouble / Int.MaxValue.toDouble) >= p
