package claimalgebra.society
package experiment

/** The primary outcome of one fallible-oracle game, adjudicated against the sealed true target
  * (fallible-oracle-experiment-design §Dependent variables). Exactly one per finished game.
  */
enum PrimaryOutcome:
  /** The gate signed slot `C` and `C` equals the true target. */
  case SignCorrect

  /** The gate signed slot `C` and `C` differs from the true target. THE FAIL-OPEN — the cardinal
    * metric, the negation of the accept biconditional's soundness under a corrupted `verify()`.
    */
  case SignWrong

  /** The gate signed nothing (inconclusive / budget exhausted). */
  case Abstain

/** The mechanical adjudicator: a pure fold over a finished game's log that classifies the outcome
  * against the sealed truth. No LLM judge, no similarity threshold (consistent with librarian
  * non-generativity, fallible-oracle-experiment-design §Dependent variables): SignWrong holds iff
  * the signed label DIFFERS from the registered truth. The log is the substrate — the same
  * log-centric discipline as the rest of the system, now pointed at itself.
  */
object Adjudication:

  /** The registered true target, if the log carries the [[Event.TargetRegistered]] marker the
    * harness seeds at game start. `None` only when a log was not produced by the experiment
    * harness.
    */
  def trueTarget(log: Seq[Event]): Option[Answer] =
    log.collectFirst { case Event.TargetRegistered(_, _, t, _) => t }

  /** The signed candidate, if any — the terminal [[Event.GateSign]]. A game signs at most once;
    * take the last defensively.
    */
  def signed(log: Seq[Event]): Option[Answer] =
    log.collect { case Event.GateSign(_, _, c) => c }.lastOption

  /** Classify a finished game's log against its sealed truth. Fail-closed: a log with no
    * [[Event.TargetRegistered]] marker cannot be adjudicated (a harness error, never a valid
    * trial), so it is a `Left`, never silently an `Abstain`.
    */
  def classify(log: Seq[Event]): Either[String, PrimaryOutcome] =
    trueTarget(log) match
      case None => Left("cannot adjudicate: no TargetRegistered marker in the log")
      case Some(truth) =>
        Right(signed(log) match
          case None => PrimaryOutcome.Abstain
          case Some(c) if c == truth => PrimaryOutcome.SignCorrect
          case Some(_) => PrimaryOutcome.SignWrong)
