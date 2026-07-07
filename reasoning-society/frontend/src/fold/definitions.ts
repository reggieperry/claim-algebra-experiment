import type {
  DefinitionClaim,
  DefinitionClaimOrigin,
  DefinitionOrigin,
  ReasoningEvent,
} from '../model';

// The "definitions in scope this game" read (clarification-feature §5, two-tier-reset-design) — the
// society's shared vocabulary, folded PURELY from the log's `definition_given` AND `definition_remembered`
// events up to the playhead. A separate projection from the belief fold precisely because definitions are
// belief-inert (mirrors the Scala `Definitions` read). Like every panel it is a pure reader of
// `(events, playhead)`: a definition born at e-N is simply absent at any playhead before N, so the list
// re-derives as you scrub.

// The wire-`origin` of a recalled definition, translated to the claim's provenance. `gameId` is copied
// only when PRESENT so the claim honors `exactOptionalPropertyTypes` (absent ≠ present-and-undefined),
// mirroring the decoder that omits it for a not-yet-stamped origin.
function originClaimOf(origin: DefinitionOrigin): DefinitionClaimOrigin {
  const base: DefinitionClaimOrigin = {
    agent: origin.agentId,
    questionId: origin.questionId,
  };
  return origin.gameId === undefined
    ? base
    : { ...base, gameId: origin.gameId };
}

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
      // A this-game definition: its provenance is the current exchange, so `origin.gameId` is ABSENT
      // (not yet persisted) — no "recalled" badge renders for it.
      latest.set(event.term, {
        term: event.term,
        meaning: event.meaning,
        establishedSeq: event.seq,
        origin: { agent: event.agentId, questionId: event.questionId },
      });
    } else if (event.type === 'definition_remembered') {
      // A recalled definition (persistent memory, two-tier-reset-design) folds into the SAME
      // latest-wins read: its provenance is the ORIGIN (the game/agent/question that first established
      // the meaning — `origin.gameId` PRESENT, so the badge reads "recalled from game N"), and its seek
      // target is its birth index in THIS log. A this-game redefinition of the same term appears later
      // in the log and supersedes it in place — this-game-wins.
      latest.set(event.term, {
        term: event.term,
        meaning: event.meaning,
        establishedSeq: event.seq,
        origin: originClaimOf(event.origin),
      });
    }
  }
  return [...latest.values()];
}
