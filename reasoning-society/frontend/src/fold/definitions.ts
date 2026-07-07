import type { DefinitionClaim, ReasoningEvent } from '../model';

// The "definitions established this game" read (clarification-feature §5) — the society's shared
// vocabulary, folded PURELY from the log's `definition_given` events up to the playhead. A separate
// projection from the belief fold precisely because definitions are belief-inert (mirrors the Scala
// `Definitions` read). Like every panel it is a pure reader of `(events, playhead)`: a definition
// born at e-N is simply absent at any playhead before N, so the list re-derives as you scrub.

// The ESTABLISHED meaning per term — the latest definition of each term wins, in first-seen term
// order (mirrors `Definitions.established`). Accumulate/latest for now: a later definition of the
// same term REPLACES the earlier in this read; the full chain stays recoverable from the raw log.
export function definitionsOf(
  events: readonly ReasoningEvent[],
  playhead: number,
): readonly DefinitionClaim[] {
  // Keyed by term, updating in place — a re-definition stays one durable row, never a second append
  // (the still, browse-not-tail affordance the memory panel uses). A Map preserves first-seen order.
  const latest = new Map<string, DefinitionClaim>();
  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (event.type === 'definition_given') {
      latest.set(event.term, {
        term: event.term,
        meaning: event.meaning,
        agent: event.agentId,
        questionId: event.questionId,
        establishedSeq: event.seq,
      });
    } else if (event.type === 'definition_remembered') {
      // A recalled definition (persistent memory, two-tier-reset-design) folds into the SAME
      // latest-wins read: its provenance is the ORIGIN (the agent / question that first established
      // the meaning), and its seek target is its birth index in THIS log. A this-game redefinition
      // of the same term appears later in the log and supersedes it in place — this-game-wins.
      latest.set(event.term, {
        term: event.term,
        meaning: event.meaning,
        agent: event.origin.agentId,
        questionId: event.origin.questionId,
        establishedSeq: event.seq,
      });
    }
  }
  return [...latest.values()];
}
