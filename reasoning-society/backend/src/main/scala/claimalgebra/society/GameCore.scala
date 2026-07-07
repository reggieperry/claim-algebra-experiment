package claimalgebra.society

import claimalgebra.*
import claimalgebra.calculus.{Evidence, Ledger, Resolution}

/** Why the gate did not sign. Either the shipped [[Gate]] blocked (a non-`True` corner, or an
  * ambiguity) — carried as the gate's own [[BlockReason]] — or the no-lone-sign FLOOR held a
  * would-be-signable value back as `Unconfirmed`. `Unconfirmed` is a society-added reason beyond
  * the core gate's taxonomy (actor-abstraction §6: "[decision] no lone sign" — not the bare gate's
  * default): a clean, unambiguous `True` winner backed by too few DISTINCT agents holds as
  * unconfirmed rather than signing.
  */
enum AbstainReason:
  case Blocked(reason: BlockReason)
  case Unconfirmed(backers: Int)

/** The gate's decision on the answer-slot at a prefix: sign the winning hypothesis, or abstain with
  * a reason. The signing counterpart of the frontend's `signableCandidate`.
  */
enum GateDecision:
  case Sign(answer: Answer)
  case Abstain(reason: AbstainReason)

/** The society's move for a round: commit to an answer, or ask another question. A round that has
  * not completed is ALWAYS `Abstain` — never `Sign` — so round attrition can never drive the
  * decision to a signature (actor-abstraction §9, the sharpest finding).
  */
enum Move:
  case Sign(answer: Answer)
  case Abstain

/** The pure game core of the reasoning-society backend — the fold/gate path, built with no `IO`, no
  * actors, and no LLM, so it is law- and property-testable in isolation (the effectful actor/LLM
  * transport is a separate slice above it).
  *
  * The society reasons about ONE answer-slot: a single `Testimony[Answer]` whose candidate values
  * are the competing hypotheses (actor-abstraction §7). Belief is the [[Ledger]] fold of the log's
  * belief-affecting events projected into `calculus.Evidence`; the four-state read and the gate
  * decision are recomputed-on-read off that fold, so nothing drifts.
  */
object GameCore:

  /** The no-lone-sign floor (actor-abstraction §6, matching the frontend `MIN_CORROBORATORS` and
    * the workbench `Panel.MinCorroboration`): a value may sign only if backed by at least this many
    * DISTINCT agents. A lone assertion — one cheap agent's possibly-hallucinated hypothesis — holds
    * as unconfirmed, closing the confidently-wrong-at-signature hole a bare gate would leave open.
    */
  val MinCorroboration: Int = 2

  /** The no-live-support retirement floor (hypothesis-lifecycle, the design of record): a
    * hypothesis retires only when ≥ this many DISTINCT agents hold a standing refutation of it. Set
    * equal to [[MinCorroboration]] deliberately — the two floors are symmetric: one lone (possibly-
    * hallucinated) refutation never retires a hypothesis, exactly as one lone assertion never signs
    * one. At `≥ 1` plant-or-fungus would misfire at e-19 (the skeptic alone); `≥ 2` fires it only
    * at e-20, once the driller self-withdraws and a second standing refuter exists. Note a
    * self-withdrawing author's own refutation counts among the standing refuters (a withdrawal is
    * itself a con signal), so a sole asserter's withdrawal plus one external refuter meets the
    * floor; a lone self-withdrawal with no other refuter (count 1) is held.
    */
  val MinRefuters: Int = MinCorroboration

  // On the reasoning-society path θ and verification are wired OFF (actor-abstraction §6): the gate's
  // `grade ≥ θ` and `verify` conjuncts are TRIVIALLY satisfied, so acceptance reduces to
  // `corner = True ∧ cardinality = 1` — plus the floor below. θ = ⊥ (every grade clears it),
  // ν = _ → ⊤, and the verifier is `trusting`. This MIRRORS `calculus.BelnapReader`'s own wired-off
  // parameters, so the GATE STEP of `decide` agrees with the four-state `belief` read. But `decide` is
  // strictly STRICTER than `belief`: it adds the no-lone-sign floor, so a lone-backed clean-True slot
  // reads `belief.status = Resolved` yet `decide = Abstain(Unconfirmed)`. The floor is a `decide`-only
  // narrowing; a viewer must never present `belief = Resolved` as a committed signature.
  private val WiredOffTheta: Lev = Lev.bottom
  private val WiredOffNu: Lineage => Lev = _ => Lev.top
  private def trusting[A]: Verifier[A] = _ => true

  /** Project the log into the belief-affecting evidence stream (actor-abstraction §7's corrected
    * table). Only the four hypothesis-moving events project; the question and gate events, and the
    * oracle's raw reply, move no belief and project to nothing.
    *
    *   - an agent ASSERTS/CORROBORATES `v` → `Asserted(leaf(v, …))` — pro-only support, so
    *     `corroborate` accumulates pro on `v`;
    *   - an agent (or the oracle seam) REFUTES a live `v` → `Asserted(single(v, ∅, …))` — con-only,
    *     so folded against a prior assertion of `v` the candidate carries `pro > 0 ∧ con > 0` and
    *     [[Testimony.corner]] reads `Belnap.Glut` (a real conflict), the §7 contradiction→glut
    *     path;
    *   - an agent STRIKES → `Withdrawn` — the operative is struck whole, no replacement (reads
    *     Superseded). In the single-slot model this strikes the slot operative, not one candidate.
    *
    * The pro/con lineage token is minted PER EVENT (keyed by `seq`), NOT per agent
    * (actor-abstraction §9: the agent is routing, not provenance — conflating them would let a
    * re-asserting agent double-count its own support through the counting semiring). The distinct-
    * agent tally the floor needs is read separately, from the events.
    */
  def project(events: Seq[Event]): List[Evidence[Answer]] =
    events.toList.flatMap {
      case Event.Assert(seq, _, _, v, _) => List(Evidence.Asserted(pro(v, seq)))
      case Event.Corroborate(seq, _, _, v, _) => List(Evidence.Asserted(pro(v, seq)))
      case Event.Refute(seq, _, _, v, _) => List(Evidence.Asserted(con(v, seq)))
      case _: Event.Strike => List(Evidence.Withdrawn[Answer]())
      case _: Event.QuestionProposed => Nil
      case _: Event.QuestionAsked => Nil
      case _: Event.AnswerGiven => Nil
      // The clarification pair is belief-inert (clarification-feature §4): a challenge and a
      // definition negotiate the shared vocabulary a grounded answer is grounded to — grounding
      // context, never a hypothesis — so neither moves the answer-slot. The fail-closed sign path
      // (Gate ∧ no-lone-sign floor ∧ glut-on-contradiction) is unchanged: definitions cannot sign.
      case _: Event.ClarificationRequested => Nil
      case _: Event.DefinitionGiven => Nil
      // A recalled definition (persistent memory seeded into a fresh game, two-tier-reset-design) is
      // belief-inert exactly as the clarification pair is: it grounds the vocabulary, never a
      // hypothesis. Dropping it here is invariant 1 (belief-inertness / seed-invariance) — a game
      // begins at `gap` regardless of how many definitions are seeded.
      case _: Event.DefinitionRemembered => Nil
      case _: Event.GateAbstain => Nil
      case _: Event.GateSign => Nil
      // The lifecycle markers are belief-inert (hypothesis-lifecycle §A/§B): they are the audit/UI
      // trace of a retirement the recomputed predicate authoritatively decides — masking is
      // `maskedProject`, never these markers — so they must move no belief. `project ≡
      // maskedProject(_, ∅)` (invariant 5(i)) depends on this: the no-retirement path stays
      // byte-identical to HEAD.
      case _: Event.Retired => Nil
      case _: Event.Resurrected => Nil
    }

  /** The folded answer-slot at a prefix — the single `Testimony[Answer]` whose candidate values are
    * the live hypotheses. On the reasoning-society wire the belief is ALWAYS a plain testimony (no
    * amend/supersede event exists to build a `Supersession`), so the fold's `Right` branch is
    * collapsed to its operative, fail-closed. `slot(log, upTo).cardinality` is the number of live
    * for-candidates.
    */
  def slot(log: Vector[Event], upTo: Int): Testimony[Answer] =
    val prefix = log.take(upTo)
    Ledger.belief(maskedProject(prefix, retiredCandidates(prefix))).fold(identity, _.operative)

  /** The four-state read at a prefix — the `Resolution` the frontend viewer mirrors. `upTo` is a
    * positional prefix count (`log.take`), so a negative or over-large value degrades cleanly to
    * the empty prefix or the whole log.
    */
  def belief(log: Vector[Event], upTo: Int): Resolution[Answer] =
    val prefix = log.take(upTo)
    Ledger.resolve(maskedProject(prefix, retiredCandidates(prefix)))

  /** The gate decision at a prefix: apply the shipped [[Gate]] (reduced to `corner = True ∧
    * cardinality = 1` on this path, θ/verify wired off), THEN the no-lone-sign floor. A clean,
    * unambiguous `True` winner signs only if backed by ≥ [[MinCorroboration]] DISTINCT agents,
    * counted FROM THE EVENTS (not the algebra's `Prov` lineage); a lone-agent winner abstains as
    * `Unconfirmed`. Every other belief (gap, glut, ambiguity, struck) abstains with the gate's own
    * [[BlockReason]].
    */
  def decide(log: Vector[Event], upTo: Int): GateDecision =
    val prefix = log.take(upTo)
    // Mask the defeated candidates before the gate reads the slot, so a *defeated* hypothesis's
    // jamming con can no longer hold a different well-supported live one hostage (the false-glut
    // jam the lifecycle fix removes). The winner is never a retired candidate — it was masked out —
    // so `distinctBackers(prefix, winner)` counts the same backers masked or not.
    val retired = retiredCandidates(prefix)
    Ledger.belief(maskedProject(prefix, retired)) match
      case Left(answerSlot) =>
        Gate.accept(answerSlot, WiredOffTheta, WiredOffNu, trusting[Answer]) match
          case Decision.Accepted(winner) =>
            val backers = distinctBackers(prefix, winner)
            if backers >= MinCorroboration then GateDecision.Sign(winner)
            else GateDecision.Abstain(AbstainReason.Unconfirmed(backers))
          case Decision.Blocked(reason) =>
            GateDecision.Abstain(AbstainReason.Blocked(reason))
      case Right(_) =>
        // A supersession pair is unreachable on this wire (no amend/supersede event type). Fail
        // closed — a struck/superseded belief is never a clean single sign.
        GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Refuted))

  /** The round decision — a PURE function of the log, the prefix, and whether the round completed.
    * An INCOMPLETE round is `Abstain`, NEVER `Sign`: it short-circuits WITHOUT consulting the gate,
    * so a round that lost its blocking rival to attrition (collapsing to `cardinality = 1`) can
    * never be signed on the survivor (actor-abstraction §9). A completed round signs iff [[decide]]
    * signs; otherwise it asks another question.
    */
  def nextMove(log: Vector[Event], upTo: Int, roundComplete: Boolean): Move =
    if !roundComplete then Move.Abstain
    else
      decide(log, upTo) match
        case GateDecision.Sign(winner) => Move.Sign(winner)
        case GateDecision.Abstain(_) => Move.Abstain

  /** The distinct agents backing `winner` with PRO evidence — the assert/corroborate events on that
    * hypothesis, deduplicated by agent. Read from the EVENTS (actor-abstraction §9), never from the
    * claim's `Prov` lineage; a refuting agent does not back, and the oracle/gate events carry no
    * hypothesis.
    */
  private[society] def distinctBackers(events: Seq[Event], winner: Answer): Int =
    events
      .collect {
        case Event.Assert(_, _, agent, `winner`, _) => agent
        case Event.Corroborate(_, _, agent, `winner`, _) => agent
      }
      .toSet
      .size

  // --- The channel-asymmetry retirement predicate (hypothesis-lifecycle §A) ---
  //
  // A hypothesis retires when its pro channel has NO LIVE support and its con channel has standing
  // refutation. "No live support" is read as SELF-WITHDRAWAL: every agent that asserted B has since
  // refuted it (its latest stance on B is against). This is chosen over channel-recency (max pro seq
  // < max con seq), which is a reachable confidently-wrong-at-signature: a real glut where the
  // asserter stays silent while others refute (pro live, never withdrawn) would be wrongly retired,
  // masking its con and letting a clean-but-wrong rival sign. Self-withdrawal holds that case
  // (the asserter still stands behind), and the asymmetry settles it: over-retirement enables a
  // wrong sign; under-retirement is merely cautious. The predicate is PURELY STRUCTURAL — it reads
  // only `seq`, `agent`, and `candidateId`, never a note/content string or the oracle's answer, so
  // it makes no generative judgment (hypothesis-lifecycle "what NOT to build").

  /** Per candidate, the latest `seq` at which each agent took a pro (Assert/Corroborate) or con
    * (Refute) stance on it — the fold the self-withdrawal predicate reads. Strike, the oracle
    * reply, the gate events, the clarification/definition pair, and the lifecycle markers are ALL
    * inert here: a strike is a whole-slot retraction (not a per-candidate con stance), and the rest
    * move no hypothesis. `math.max` guards order — the latest stance wins even on an out-of-order
    * log.
    */
  private[society] def stances(log: Seq[Event]): Map[Answer, Stance] =
    log.foldLeft(Map.empty[Answer, Stance]) { (acc, ev) =>
      ev match
        case Event.Assert(seq, _, agent, c, _) => bumpPro(acc, c, agent, seq)
        case Event.Corroborate(seq, _, agent, c, _) => bumpPro(acc, c, agent, seq)
        case Event.Refute(seq, _, agent, c, _) => bumpCon(acc, c, agent, seq)
        case _ =>
          acc // every other event is inert to the stance read (see project for the contract)
    }

  private def bumpPro(
      acc: Map[Answer, Stance],
      c: Answer,
      a: AgentId,
      seq: Int
  ): Map[Answer, Stance] =
    val st = acc.getOrElse(c, Stance.empty)
    acc.updated(c, st.copy(lastPro = recordLatest(st.lastPro, a, seq)))

  private def bumpCon(
      acc: Map[Answer, Stance],
      c: Answer,
      a: AgentId,
      seq: Int
  ): Map[Answer, Stance] =
    val st = acc.getOrElse(c, Stance.empty)
    acc.updated(c, st.copy(lastCon = recordLatest(st.lastCon, a, seq)))

  private def recordLatest(m: Map[AgentId, Int], a: AgentId, seq: Int): Map[AgentId, Int] =
    m.updatedWith(a) {
      case Some(prev) => Some(math.max(prev, seq))
      case None => Some(seq)
    }

  /** `a`'s latest stance on the candidate is PRO — still supporting it (has a pro entry whose `seq`
    * is not preceded by a later con). An agent with no pro entry never stands behind.
    */
  private[society] def standsBehind(s: Stance, a: AgentId): Boolean =
    s.lastPro.get(a).exists(p => s.lastCon.get(a).forall(c => p > c))

  /** `a`'s latest stance on the candidate is CON — a standing refuter (has a con entry whose `seq`
    * is not preceded by a later pro). Since one event is one stance, a pro and a con by one agent
    * never share a `seq`, so [[standsBehind]] and `standsAgainst` are mutually exclusive.
    */
  private[society] def standsAgainst(s: Stance, a: AgentId): Boolean =
    s.lastCon.get(a).exists(c => s.lastPro.get(a).forall(p => c > p))

  /** The channel-asymmetry predicate for one candidate's [[Stance]]: retire iff it has pro-authors,
    * EVERY pro-author self-withdrew (none [[standsBehind]]), and ≥ [[MinRefuters]] distinct
    * standing refuters. A single live backer (any pro-author still standing behind) HOLDS it — that
    * is a real glut, not a defeat.
    */
  private[society] def defeated(s: Stance): Boolean =
    val authors = s.lastPro.keySet
    val standingRefuters = s.lastCon.keySet.count(a => standsAgainst(s, a))
    authors.nonEmpty &&
    authors.forall(a => !standsBehind(s, a)) &&
    standingRefuters >= MinRefuters

  /** The candidates the channel-asymmetry predicate retires — the pure fold over the log,
    * recomputed on every read so there is no stored status to drift. `∅` for a log with no defeated
    * hypothesis (invariant 5(i): the no-retirement path is byte-identical to HEAD).
    */
  private[society] def retiredCandidates(log: Seq[Event]): Set[Answer] =
    stances(log).collect { case (c, s) if defeated(s) => c }.toSet

  /** [[project]] with the retired candidates' pro AND con events dropped first — masking a defeated
    * hypothesis off both channels before the existing projection. Only the per-candidate stance
    * events (Assert/Corroborate/Refute) are dropped; a whole-slot Strike, the oracle reply, and the
    * gate events are untouched. `maskedProject(events, ∅) ≡ project(events)` by construction (an
    * empty retired set drops nothing), which is invariant 5(i): masking removes a jamming con,
    * never adds pro and never lowers the sign floor.
    */
  private[society] def maskedProject(
      events: Seq[Event],
      retired: Set[Answer]
  ): List[Evidence[Answer]] =
    project(events.filterNot {
      case Event.Assert(_, _, _, c, _) => retired.contains(c)
      case Event.Corroborate(_, _, _, c, _) => retired.contains(c)
      case Event.Refute(_, _, _, c, _) => retired.contains(c)
      case _ => false
    })

  /** The set of candidates currently marked retired by the trace markers — a fold of `Retired(+)` /
    * `Resurrected(-)` over the log. This is the marker state, NOT the masking authority: masking is
    * [[retiredCandidates]] (the recomputed predicate). [[reconcileRetirements]] compares the two to
    * emit the deltas.
    */
  private[society] def markedRetired(log: Seq[Event]): Set[Answer] =
    log.foldLeft(Set.empty[Answer]) { (acc, ev) =>
      ev match
        case Event.Retired(_, _, c) => acc + c
        case Event.Resurrected(_, _, c) => acc - c
        case _ => acc
    }

  /** The marker events that bring the trace in line with the predicate — a `Retired` for each
    * candidate the predicate now defeats but the markers do not yet record, a `Resurrected` for
    * each the markers record but the predicate no longer defeats (recovery), with contiguous `seq`s
    * from `nextSeq` (retirements first, then resurrections; each ordered by candidate value so the
    * assignment is deterministic). Pure — it RETURNS the events for the single-writer LogActor to
    * append (slice 2); it appends nothing itself. The markers are trace only; a replay masks off
    * the recomputed predicate regardless of them, so an unemitted marker never changes belief.
    */
  private[society] def reconcileRetirements(
      log: Seq[Event],
      nextSeq: Int,
      timestamp: Long
  ): List[Event] =
    val want = retiredCandidates(log)
    val have = markedRetired(log)
    val toRetire = (want -- have).toList.sortBy(_.value)
    val toResurrect = (have -- want).toList.sortBy(_.value)
    val pending = toRetire.map(c => (c, true)) ++ toResurrect.map(c => (c, false))
    pending.zipWithIndex.map { case ((c, isRetire), i) =>
      val seq = nextSeq + i
      if isRetire then Event.Retired(seq, timestamp, c)
      else Event.Resurrected(seq, timestamp, c)
    }

  /** A leaf carrying pro-support cited by a per-event token (fail-closed: a blank token —
    * impossible here — yields no support, so the candidate normalizes away rather than signing
    * empty).
    */
  private def pro(v: Answer, seq: Int): Testimony[Answer] = Testimony.leaf(v, token(seq))

  /** A con-bearing candidate — no pro, con cited by a per-event token. Folded against a prior
    * assertion of the same value it produces the glut.
    */
  private def con(v: Answer, seq: Int): Testimony[Answer] =
    Testimony.single(v, Prov.zero, token(seq))

  /** A per-event provenance token — one independent derivation per event `seq`. */
  private def token(seq: Int): Prov =
    Lineage.from(s"ev-$seq").fold(Prov.zero)(Prov.single)

/** One candidate's stance summary: the latest `seq` at which each agent took a pro
  * ([[Event.Assert]] / [[Event.Corroborate]]) or con ([[Event.Refute]]) stance on it. The pure
  * input to the self-withdrawal predicate ([[GameCore.standsBehind]] / [[GameCore.standsAgainst]] /
  * [[GameCore.defeated]]); carries only `seq`s and agent identities, no prose, so retirement is
  * non-generative (hypothesis-lifecycle invariant 3).
  */
final private[society] case class Stance(lastPro: Map[AgentId, Int], lastCon: Map[AgentId, Int])

private[society] object Stance:
  val empty: Stance = Stance(Map.empty, Map.empty)
