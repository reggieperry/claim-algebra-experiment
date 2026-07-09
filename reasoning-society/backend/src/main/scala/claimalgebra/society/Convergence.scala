package claimalgebra.society

/** The librarian's convergence monitor (librarian-convergence-monitor) — a PURE, STRUCTURAL,
  * NON-GENERATIVE detector of non-convergence over the belief-state history.
  *
  * The failure it catches is the "apple bug" in its fullest form: a game poisoned at the root (a
  * grounded, faithfully-recorded, game-fatally-wrong early premise) reasons impeccably into a
  * region that cannot contain the answer, never consolidating, thrashing to budget exhaustion. What
  * makes that evidence "suspicious" is a SEMANTIC judgment the librarian must never make (the same
  * discipline that keeps retirement honest). So the librarian detects the STRUCTURE — that the
  * search is not converging — not the MEANING, and raises a flag; a judge (the human, or the
  * agents) diagnoses WHAT is wrong. The detect/diagnose split.
  *
  * Everything here is a pure fold over the event log (the belief-state history is itself
  * `GameView.from` / the retirement-masked live-candidate structure per round — no side state). It
  * reads only STRUCTURE: candidate identities as OPAQUE keys, distinct-backer counts, per-candidate
  * pro/con presence (the Belnap glut), and round/budget counts — NEVER a note/content string, NEVER
  * an oracle answer's value, NEVER what a candidate MEANS. This is the non-generativity contract,
  * pinned by the metamorphic check: permuting the claims' content (renaming candidates, scrambling
  * notes, flipping oracle answers) while holding the belief-state STRUCTURE fixed leaves the
  * fire/no-fire decision and the emitted counts IDENTICAL.
  *
  * It is tuned CONSERVATIVE — it flags LATE (clear non-convergence), never on early normal
  * narrowing (asking questions is progress; a game with no candidates-yet or a candidate steadily
  * consolidating does not fire). The output is a [[Warning]] carrying structural evidence only; the
  * flag is a request for help, never a change to the gate or a manufactured sign.
  */
object Convergence:

  /** The look-back window K (in rounds). A signal fires only on a sustained run of K rounds without
    * consolidation — chosen large enough that a normal narrowing game (which signs within a couple
    * of rounds, or is still asking questions) never fills the window. Conservative by construction:
    * raise it to flag later, lower it to flag sooner.
    */
  val WindowRounds: Int = 4

  /** Signal 3's budget backstop threshold: the flag may fire when at least this fraction of the
    * round budget ([[SocietyConfig.maxRounds]]) has been spent with candidates tried and none
    * consolidating — "we are running out of questions and are nowhere."
    */
  val BudgetFraction: Double = 0.75

  /** The structural evidence a flag carries — and nothing else. `roundsWithoutConsolidation` is the
    * length of the most-recent run of rounds in which no live candidate reached signable support;
    * `glutPersistence` is the longest run of rounds one live glut sat unresolved. NO candidate
    * name, NO reason string, NO diagnosis: the librarian counts, a judge diagnoses.
    */
  final case class Warning(roundsWithoutConsolidation: Int, glutPersistence: Int)

  /** One round's belief-state read: the live (non-retired) candidates with their distinct-backer
    * counts, and the live gluts (a candidate carrying both pro and con, not retired by the
    * lifecycle predicate). Candidate identities are OPAQUE keys — never their string value — so the
    * monitor is blind to meaning.
    */
  final private case class Snapshot(leaders: Map[Answer, Int], gluts: Set[Answer])

  /** The pure detector: does the belief-state history show CLEAR non-convergence? Returns the
    * structural [[Warning]] to raise, or `None`. Idempotency (fire at most once per stuck episode)
    * is NOT here — see [[warningToEmit]]; this is the raw signal read.
    *
    * Fires when any of the three structural signals holds and the search is not currently signable:
    *   - Signal 1 (primary) — no candidate consolidating AND the live set churning: over the last
    *     `rwc ≥` [[WindowRounds]] rounds no live candidate reached signable support, and candidates
    *     turned over (something that was live got dropped/retired — the fossil-region thrash).
    *   - Signal 2 — a persistent unresolved glut: some live glut sat for ≥ [[WindowRounds]] rounds.
    *   - Signal 3 (backstop) — budget consumed without convergence: ≥ [[BudgetFraction]] of the
    *     round budget spent, a sustained no-consolidation run, with candidates actually tried.
    */
  def flag(log: Vector[Event], config: SocietyConfig): Option[Warning] =
    val hist = history(log)
    // Nothing to judge on an empty history; and if the slot is signable RIGHT NOW the search is not
    // stuck — never flag a game that is about to sign (and never manufacture a reason to withhold).
    if hist.isEmpty || currentlySignable(log, config.corroborationSigns) then None
    else
      val roundsElapsed = roundEndPrefixLengths(log).size
      val rwc = roundsWithoutConsolidation(hist)
      val gp = glutPersistence(hist)
      val stuckRun = hist.takeRight(rwc)
      val budgetSpent =
        config.maxRounds > 0 && roundsElapsed.toDouble >= BudgetFraction * config.maxRounds
      val candidatesTried = hist.exists(s => s.leaders.nonEmpty || s.gluts.nonEmpty)

      val signal1 = rwc >= WindowRounds && churning(stuckRun)
      val signal2 = gp >= WindowRounds
      val signal3 = budgetSpent && rwc >= WindowRounds && candidatesTried

      if signal1 || signal2 || signal3 then Some(Warning(rwc, gp)) else None

  /** The warning to emit at a round close, if any: the detector [[flag]] fired AND no warning is
    * already standing for the current stuck episode ([[active]]). This is the idempotency the
    * single-writer LogActor needs — fire at most once per stuck episode, not every round once
    * flagged. A stuck episode ends when a later round consolidates; a fresh stall can flag again.
    */
  def warningToEmit(log: Vector[Event], config: SocietyConfig): Option[Warning] =
    if active(log) then None else flag(log, config)

  /** Is a convergence flag already STANDING? True when a `ConvergenceWarning` was emitted and no
    * round has consolidated since — the stuck episode it flagged is unresolved, so re-flagging
    * would nag. Pure and structural (reads only the marker's position and the consolidation reads).
    */
  def active(log: Vector[Event]): Boolean =
    lastWarningPrefixLen(log).exists(wLen => !consolidatedAfter(log, wLen))

  // --- the belief-state history (a pure fold over the log) ---

  /** The per-round belief-state history: one [[Snapshot]] at each round boundary (an `AnswerGiven`
    * — one answered question is one round) plus the current tail (the in-progress round at the
    * call). Reads round boundaries by the PRESENCE of an `AnswerGiven`, never its answer value.
    */
  private def history(log: Vector[Event]): List[Snapshot] =
    val cuts = (roundEndPrefixLengths(log) :+ log.size).filter(_ > 0).distinct
    cuts.map(len => snapshotAt(log, len))

  /** The prefix lengths (1-based positions) at each `AnswerGiven` — the round boundaries. Reads the
    * event's POSITION and TYPE only; the oracle answer value is never consulted (a flip of it must
    * not move a boundary — the metamorphic contract).
    */
  private def roundEndPrefixLengths(log: Vector[Event]): List[Int] =
    log.zipWithIndex.collect { case (_: Event.AnswerGiven, i) => i + 1 }.toList

  /** The belief-state read at a prefix — the live (non-retired) candidates with distinct-backer
    * counts (reusing [[GameView.from]], already retirement-masked) and the live gluts.
    */
  private def snapshotAt(log: Vector[Event], len: Int): Snapshot =
    val prefix = log.take(len)
    val leaders = GameView.from(prefix).hypotheses.toMap
    Snapshot(leaders, liveGluts(prefix))

  /** The live gluts at a prefix: a candidate carrying BOTH pro (assert/corroborate) and con
    * (refute), and NOT retired by the lifecycle predicate — the structural `pro > 0 ∧ con > 0`
    * Belnap glut, per candidate, read as opaque keys.
    */
  private def liveGluts(prefix: Vector[Event]): Set[Answer] =
    val retired = GameCore.retiredCandidates(prefix)
    val pro = prefix.collect {
      case Event.Assert(_, _, _, c, _) => c
      case Event.Corroborate(_, _, _, c, _) => c
    }.toSet
    val con = prefix.collect { case Event.Refute(_, _, _, c, _) => c }.toSet
    pro.intersect(con).diff(retired)

  // --- the three structural reads ---

  /** A round is CONSOLIDATED when some live candidate reached signable support — ≥
    * [[GameCore.MinCorroboration]] distinct backers on a clean (non-glut) corner. This is the
    * structural mark of progress; a game that consolidates is narrowing, not stuck.
    */
  private def consolidated(s: Snapshot): Boolean =
    s.leaders.exists((c, n) => n >= GameCore.MinCorroboration && !s.gluts.contains(c))

  /** How many of the most-recent rounds passed with NO consolidation — the trailing run length. */
  private def roundsWithoutConsolidation(history: List[Snapshot]): Int =
    history.reverse.takeWhile(s => !consolidated(s)).size

  /** The longest run of consecutive rounds a single live glut persisted — read per candidate
    * identity, so a glut that keeps being re-refuted without resolving accumulates.
    */
  private def glutPersistence(history: List[Snapshot]): Int =
    val everGlut = history.flatMap(_.gluts).toSet
    everGlut.toList.map(c => maxRun(history.map(_.gluts.contains(c)))).maxOption.getOrElse(0)

  /** Turnover over a run: some candidate that was live earlier is no longer live (dropped or
    * retired) — the "candidates proposed and abandoned, nothing accumulating" churn.
    */
  private def churning(run: List[Snapshot]): Boolean =
    val everLive = run.flatMap(_.leaders.keySet).toSet
    val finalLive = run.lastOption.map(_.leaders.keySet).getOrElse(Set.empty[Answer])
    everLive.diff(finalLive).nonEmpty

  /** Is the slot signable at the full log? Reads [[GameCore.decide]] — a pure structural read of
    * the gate (it never signs because of this call; the monitor never touches the sign path).
    */
  private def currentlySignable(log: Vector[Event], corroborationSigns: Boolean): Boolean =
    GameCore.decide(log, log.size, corroborationSigns = corroborationSigns) match
      case GateDecision.Sign(_) => true
      case GateDecision.Abstain(_) => false

  // --- idempotency helpers ---

  private def lastWarningPrefixLen(log: Vector[Event]): Option[Int] =
    log.zipWithIndex.collect { case (_: Event.ConvergenceWarning, i) => i + 1 }.lastOption

  private def consolidatedAfter(log: Vector[Event], wLen: Int): Boolean =
    val laterCuts = (roundEndPrefixLengths(log) :+ log.size).filter(_ > wLen).distinct
    laterCuts.exists(len => consolidated(snapshotAt(log, len)))

  /** The longest run of `true` in a boolean sequence — the consecutive-glut counter. */
  private def maxRun(flags: List[Boolean]): Int =
    flags
      .foldLeft((0, 0)) { case ((cur, best), f) =>
        val c = if f then cur + 1 else 0
        (c, math.max(best, c))
      }
      ._2
