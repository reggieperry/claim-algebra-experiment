import type { AgentId, CandidateId, ReasoningEvent } from '../model';
import { describeEvent } from '../view/describeEvent';

// The current game's event log, formatted for a human to download. Two total, PURE functions over the
// log — no clock read inside them (the export time is injected), no I/O — so the render is deterministic
// and unit-testable; the Blob/anchor download that carries the result to disk is the thin effectful shell
// (see `downloadFile.ts`). The transcript reuses the on-screen event vocabulary (`describeEvent`), so it
// reads as the same words the event stream shows rather than a second, export-only dialect.

// A plain-text / Markdown transcript: a short header (event count + export time), then every event on its
// own line in seq order rendered as `e-N  Actor verb: detail`, then — when the log ends on a gate decision
// — a closing outcome line. `resolveAgent` maps an agent id to its display name (the same resolver the UI
// uses); `exportedAt` is passed in so the header timestamp is deterministic and never an ambient clock read.
export function toTranscript(
  events: readonly ReasoningEvent[],
  resolveAgent: (id: AgentId) => string,
  exportedAt: Date,
): string {
  const ordered = [...events].sort((a, b) => a.seq - b.seq);
  const count = events.length;
  const header = [
    '# Reasoning Society — event log',
    `${String(count)} event${count === 1 ? '' : 's'} · exported ${exportedAt.toISOString()}`,
  ];
  const lines = ordered.map((event) => transcriptLine(event, resolveAgent));
  const outcome = outcomeLine(ordered);
  const body = outcome === undefined ? lines : [...lines, '', outcome];
  return `${[...header, '', ...body].join('\n')}\n`;
}

// The decoded events as pretty-printed (2-space) JSON — faithful and re-importable: `JSON.parse` of this
// deep-equals the input, so a saved log round-trips back into the same event array.
export function toJson(events: readonly ReasoningEvent[]): string {
  return JSON.stringify(events, null, 2);
}

function transcriptLine(
  event: ReasoningEvent,
  resolveAgent: (id: AgentId) => string,
): string {
  const { actor, verb, detail } = describeEvent(event, resolveAgent);
  const head = `e-${String(event.seq)}  ${actor} ${verb}`;
  return detail.length > 0 ? `${head}: ${detail}` : head;
}

// A closing outcome line when the log terminates on a gate decision — the "how did this game end" summary.
// Undefined while the game is still in flight (any other terminal event), so no misleading outcome is shown.
function outcomeLine(ordered: readonly ReasoningEvent[]): string | undefined {
  const last = ordered.at(-1);
  if (last === undefined) {
    return undefined;
  }
  if (last.type === 'gate_sign') {
    return `— outcome: gate SIGNED “${signedLabel(ordered, last.candidateId)}”`;
  }
  if (last.type === 'gate_abstain') {
    return `— outcome: gate abstained — ${last.reason}`;
  }
  return undefined;
}

// The human label for a signed candidate — the content of the assert that first put it on the board, so
// the outcome reads `SIGNED “It's a dog.”` rather than a bare id. Falls back to the id if never asserted.
function signedLabel(
  ordered: readonly ReasoningEvent[],
  candidate: CandidateId,
): string {
  for (const event of ordered) {
    if (event.type === 'assert' && event.candidateId === candidate) {
      return event.content;
    }
  }
  return candidate;
}
