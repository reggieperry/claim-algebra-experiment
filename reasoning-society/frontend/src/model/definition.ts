import type { AgentId, QuestionId, Term } from './ids';

// The PROVENANCE of an established definition — WHERE the meaning came from (two-tier-reset-design,
// §Frontend). `agent` and `questionId` name the exchange that first established the meaning; `gameId`
// is the cross-game audit surface: PRESENT when the definition was RECALLED from a prior game's
// persistent memory (its value is that game N — the badge reads "recalled from game N"), ABSENT for a
// this-game definition not yet persisted. Mirrors the Scala `DefinitionProvenance`. `gameId` is the
// origin GAME, never the recalled event's own `seq` — the badge must render from here.
export interface DefinitionClaimOrigin {
  readonly agent: AgentId;
  readonly questionId: QuestionId;
  readonly gameId?: number;
}

// A definition claim in scope this game (clarification-feature §3, two-tier-reset-design): a `term`,
// its agreed `meaning`, its birth index in THIS game's log (`establishedSeq`), and its `origin` — the
// provenance that makes it a CLAIM rather than an ephemeral chat line. The meaning is attributable and
// time-stamped, so replay reconstructs it and a later definition of the same term supersedes it.
//
// Belief-inert by construction — the definition grounds the vocabulary the society reasons WITH; it
// is never a hypothesis about the answer, so it never enters the belief fold (mirrors the Scala
// `Definition`, read off the log by `Definitions`, not projected into `Ledger`).
export interface DefinitionClaim {
  readonly term: Term;
  readonly meaning: string;
  // The `definition_given` / `definition_remembered` event's seq in THIS game's log — its birth index,
  // rendered as a seek target. Distinct from `origin` (which game/agent/question first established it).
  readonly establishedSeq: number;
  // Where the meaning came from — the badge's source (a recalled definition carries `origin.gameId`).
  readonly origin: DefinitionClaimOrigin;
}
