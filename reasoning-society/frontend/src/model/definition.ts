import type { AgentId, QuestionId, Term } from './ids';

// A definition claim established this game (clarification-feature §3): a `term`, its agreed
// `meaning`, and its PROVENANCE — the asking agent that gave it and the clarification exchange
// (the challenged question) that established it, anchored to the `definition_given` event's `seq`.
// This is what makes a definition a CLAIM rather than an ephemeral chat line: the meaning is
// attributable and time-stamped, so replay reconstructs it and a later slice can supersede it.
//
// Belief-inert by construction — the definition grounds the vocabulary the society reasons WITH; it
// is never a hypothesis about the answer, so it never enters the belief fold (mirrors the Scala
// `Definition`, read off the log by `Definitions`, not projected into `Ledger`).
export interface DefinitionClaim {
  readonly term: Term;
  readonly meaning: string;
  // The asking agent that supplied the meaning — the claim's provenance/source.
  readonly agent: AgentId;
  // The challenged question whose exchange established this definition.
  readonly questionId: QuestionId;
  // The `definition_given` event's seq — its birth index, rendered as a seek target.
  readonly establishedSeq: number;
}
