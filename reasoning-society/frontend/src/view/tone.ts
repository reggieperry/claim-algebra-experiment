import type { Candidate } from '../model';

// The visual state of a candidate row — the Belnap corner refined by whether a resolved candidate is
// still rivalled. A lone resolved candidate is the leader (green); a resolved candidate with rivals
// is part of a NARROWING field (neutral), distinct from a contradiction glut (amber). This is the
// brief's "distinguish RivalCandidates (narrowing) from a Contradicted glut" (§4).
export type Tone = 'resolved' | 'rival' | 'conflict' | 'superseded' | 'missing';

function assertNever(x: never): never {
  throw new Error(`unhandled corner: ${JSON.stringify(x)}`);
}

export function toneOf(candidate: Candidate, cardinality: number): Tone {
  switch (candidate.corner) {
    case 'superseded':
      return 'superseded';
    case 'conflict':
      return 'conflict';
    case 'missing':
      return 'missing';
    case 'resolved':
      return cardinality > 1 ? 'rival' : 'resolved';
    default:
      return assertNever(candidate.corner);
  }
}

// A short, screen-reader-friendly label for a tone.
export function toneLabel(tone: Tone): string {
  switch (tone) {
    case 'resolved':
      return 'Resolved';
    case 'rival':
      return 'Rival — narrowing';
    case 'conflict':
      return 'Conflict — glut';
    case 'superseded':
      return 'Superseded';
    case 'missing':
      return 'Missing';
    default:
      return assertNever(tone);
  }
}

export function formatPercent(grade: number): string {
  return `${Math.round(grade * 100).toString()}%`;
}
