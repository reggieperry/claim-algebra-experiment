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
      case _: Event.GateAbstain => Nil
      case _: Event.GateSign => Nil
    }

  /** The folded answer-slot at a prefix — the single `Testimony[Answer]` whose candidate values are
    * the live hypotheses. On the reasoning-society wire the belief is ALWAYS a plain testimony (no
    * amend/supersede event exists to build a `Supersession`), so the fold's `Right` branch is
    * collapsed to its operative, fail-closed. `slot(log, upTo).cardinality` is the number of live
    * for-candidates.
    */
  def slot(log: Vector[Event], upTo: Int): Testimony[Answer] =
    Ledger.belief(project(log.take(upTo))).fold(identity, _.operative)

  /** The four-state read at a prefix — the `Resolution` the frontend viewer mirrors. `upTo` is a
    * positional prefix count (`log.take`), so a negative or over-large value degrades cleanly to
    * the empty prefix or the whole log.
    */
  def belief(log: Vector[Event], upTo: Int): Resolution[Answer] =
    Ledger.resolve(project(log.take(upTo)))

  /** The gate decision at a prefix: apply the shipped [[Gate]] (reduced to `corner = True ∧
    * cardinality = 1` on this path, θ/verify wired off), THEN the no-lone-sign floor. A clean,
    * unambiguous `True` winner signs only if backed by ≥ [[MinCorroboration]] DISTINCT agents,
    * counted FROM THE EVENTS (not the algebra's `Prov` lineage); a lone-agent winner abstains as
    * `Unconfirmed`. Every other belief (gap, glut, ambiguity, struck) abstains with the gate's own
    * [[BlockReason]].
    */
  def decide(log: Vector[Event], upTo: Int): GateDecision =
    val prefix = log.take(upTo)
    Ledger.belief(project(prefix)) match
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
