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

/** Which acceptance disjunct produced a signature (fallible-oracle Slice 4). The k-confirmation
  * quorum gates ONLY [[OracleConfirmed]], so [[BackerQuorum]] signs are k-INVARIANT and must be
  * isolated from the crown-jewel curve in analysis (otherwise they flatten the redundancy effect).
  */
enum SignPath:
  /** Signed via ≥ MinCorroboration distinct backers — structural corroboration, independent of the
    * oracle and of `k`.
    */
  case BackerQuorum

  /** Signed via the `k` ground-truth guess-confirmations (the oracle-confirmed floor relaxation) —
    * the path `k` tightens and the correlation study measures.
    */
  case OracleConfirmed

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

  /** The distinct candidates B1 ever posed as a guess (any [[Event.GuessAnswered]], regardless of
    * the answer) — the endgame-diagnostic read (E0). An abstention with an empty list never reached
    * a guess (a convergence/trigger failure); a non-empty list that omits the target reached a
    * guess but searched wrong.
    */
  def guessesPosed(log: Seq[Event]): List[Answer] =
    log.collect { case Event.GuessAnswered(_, _, c, _) => c }.toList.distinct

  /** Which disjunct produced the signature, if any: [[SignPath.BackerQuorum]] when the signed
    * candidate had ≥ `MinCorroboration` distinct backers (k-invariant), else
    * [[SignPath.OracleConfirmed]] (the k-gated ground-truth path the correlation study measures).
    * `None` when the game abstained. Read from the final log — the same folds `decide` used.
    */
  def signPath(log: Seq[Event]): Option[SignPath] =
    signed(log).map { c =>
      if GameCore.distinctBackers(log, c) >= GameCore.MinCorroboration then SignPath.BackerQuorum
      else SignPath.OracleConfirmed
    }

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
