package claimalgebra.society

/** The provenance of a [[Definition]] claim (clarification-feature §3): WHICH agent gave the
  * meaning and WHICH clarification exchange — the challenged `questionId` — established it,
  * anchored to the `DefinitionGiven` event's `seq` for the audit trail. This is what makes a
  * definition a CLAIM rather than a key-value entry: the meaning is attributable, so a later slice
  * can challenge and supersede it, the trace retained.
  *
  * `gameId` is the cross-game persistence tag (two-tier-reset-design §Types): `None` means the
  * CURRENT, not-yet-persisted game; it is filled `Some(g)` at the persistence boundary
  * ([[DefinitionMemory.remember]]) and, once carried, never drifts across generations. It is the
  * LAST field and defaults to `None` so every existing construction (`DefinitionProvenance(agent,
  * questionId, seq)`) is unchanged — the empty-memory path and every pre-persistence test stay
  * byte-identical.
  */
final case class DefinitionProvenance(
    agent: AgentId,
    questionId: QuestionId,
    seq: Int,
    gameId: Option[GameId] = None
)

/** A definition claim established in the game: a `term`, its agreed `meaning`, and its
  * [[DefinitionProvenance]]. It is a CLAIM, not a dictionary entry — it carries who said it and
  * which exchange established it, so every future use of the term grounds to the same agreed
  * meaning and the society reasons with shared vocabulary (clarification-feature §3).
  *
  * Belief-inert by construction: a definition grounds the vocabulary the society reasons WITH; it
  * is never itself a hypothesis about the answer, so it does NOT enter the belief
  * [[claimalgebra.calculus.Ledger]] fold ([[GameCore.project]] drops `DefinitionGiven`). This is a
  * separate read, [[Definitions]], off the same log.
  */
final case class Definition(term: Term, meaning: String, provenance: DefinitionProvenance)

object Definition:
  /** Validate the asking agent's structured definition reply (untrusted model output, scala-llm.md
    * / scala-security.md: re-validate every parsed field before it reaches the log): a non-blank,
    * trimmed `meaning`, else `None`. `None` means the agent posts no definition — fail-closed,
    * never a fabricated meaning and never a blank `DefinitionGiven` a `governing` reference could
    * cite.
    */
  def meaningOf(dto: DefinitionDto): Option[String] =
    Option(dto.meaning).map(_.trim).filter(_.nonEmpty)

/** The definitions read — the society's shared vocabulary, folded PURELY from the log's
  * `DefinitionGiven` events. A separate projection from the belief fold precisely because
  * definitions are belief-inert; it feeds the frontend "definitions established this game" list and
  * the [[GameView]] an agent grounds against.
  */
object Definitions:

  /** Every definition claim in log order, one per `DefinitionGiven` event — the raw audit trail. A
    * later definition of a term appears as its own entry here, so the full chain (what the earlier
    * meaning was, who changed it) is always recoverable; [[established]] is the collapsed view.
    * Replay reconstructs each claim at the playhead where its event sits.
    */
  def from(log: Vector[Event]): List[Definition] =
    log.toList.collect {
      case Event.DefinitionGiven(seq, _, agent, questionId, term, meaning) =>
        Definition(term, meaning, DefinitionProvenance(agent, questionId, seq))
      // A recalled definition (persistent memory replayed into a fresh game) carries its ORIGIN
      // provenance verbatim (which game/agent/question first established it) — a claim, not a
      // re-derivation. It reads into the same vocabulary projection as a this-game definition; the
      // belief fold drops it ([[GameCore.project]]), so it never moves a hypothesis.
      case Event.DefinitionRemembered(_, _, term, meaning, origin) =>
        Definition(term, meaning, origin)
    }

  /** The ESTABLISHED meaning per term — the latest definition of each term wins, in first-seen term
    * order. Accumulate/latest for now: a later definition of the same term REPLACES the earlier in
    * this read (`toMap` retains the last value for a duplicate key). True
    * supersession-as-recorded-chain — the prior struck and retained as a challengeable trace — is a
    * later slice; until then the full chain stays recoverable from [[from]].
    *
    * Because a recalled definition (`DefinitionRemembered`) is seeded at the HEAD of a fresh game's
    * log and a this-game challenge redefines a term LATER, latest-wins makes the merge
    * this-game-wins: the recalled meaning holds from question one, and a this-game redefinition
    * supersedes it at the recalled term's (first-seen) position — the two-tier-reset supersession
    * path, kept at the definitions-read level (never routed through refute/strike, so definitions
    * stay belief-inert).
    */
  def established(log: Vector[Event]): List[Definition] =
    val all = from(log)
    val latest: Map[Term, Definition] = all.map(d => d.term -> d).toMap
    all.map(_.term).distinct.flatMap(latest.get)
