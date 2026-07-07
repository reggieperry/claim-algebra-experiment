package claimalgebra.society

/** The provenance of a [[Definition]] claim (clarification-feature §3): WHICH agent gave the
  * meaning and WHICH clarification exchange — the challenged `questionId` — established it,
  * anchored to the `DefinitionGiven` event's `seq` for the audit trail. This is what makes a
  * definition a CLAIM rather than a key-value entry: the meaning is attributable, so a later slice
  * can challenge and supersede it, the trace retained.
  *
  * NOTE: provenance gains a `game_id` when cross-game persistence lands (the NEXT pass) —
  * definitions live only in the current game for now (clarification-feature §6). Adding it here is
  * the one field this record is designed to grow.
  */
final case class DefinitionProvenance(agent: AgentId, questionId: QuestionId, seq: Int)

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
    log.toList.collect { case Event.DefinitionGiven(seq, _, agent, questionId, term, meaning) =>
      Definition(term, meaning, DefinitionProvenance(agent, questionId, seq))
    }

  /** The ESTABLISHED meaning per term — the latest definition of each term wins, in first-seen term
    * order. Accumulate/latest for now: a later definition of the same term REPLACES the earlier in
    * this read (`toMap` retains the last value for a duplicate key). True
    * supersession-as-recorded-chain — the prior struck and retained as a challengeable trace — is a
    * later slice; until then the full chain stays recoverable from [[from]].
    */
  def established(log: Vector[Event]): List[Definition] =
    val all = from(log)
    val latest: Map[Term, Definition] = all.map(d => d.term -> d).toMap
    all.map(_.term).distinct.flatMap(latest.get)
