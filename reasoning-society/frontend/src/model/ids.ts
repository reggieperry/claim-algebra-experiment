import type { Brand } from './brand';

// The routed identifiers the log carries. Each is an OPEN set — in Build 3 agents mint fresh
// hypotheses and questions at runtime — so each is a branded `string`, not a closed union. The
// brand keeps a CandidateId from being passed where an AgentId is wanted (both are `string`).
export type AgentId = Brand<string, 'AgentId'>;
export type CandidateId = Brand<string, 'CandidateId'>;
export type QuestionId = Brand<string, 'QuestionId'>;

// A challenged/defined term — the vocabulary the clarification exchange grounds (clarification
// feature §1–§3). Lowercase-normalized ON THE WIRE by the backend, so the brand only validates
// non-blankness here; it keeps a `Term` from being passed where a `QuestionId` is wanted.
export type Term = Brand<string, 'Term'>;

// The one sanctioned place the brand cast runs, after the guard. An empty identifier is a
// programmer error in the log construction, not an expected-invalid runtime input, so it fails
// fast at construction (ts-errors: throw for a broken invariant the program cannot continue past).
function requireId(raw: string, kind: string): string {
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    throw new Error(`${kind} must be a non-empty identifier`);
  }
  return trimmed;
}

export function agentId(raw: string): AgentId {
  return requireId(raw, 'AgentId') as AgentId;
}

export function candidateId(raw: string): CandidateId {
  return requireId(raw, 'CandidateId') as CandidateId;
}

export function questionId(raw: string): QuestionId {
  return requireId(raw, 'QuestionId') as QuestionId;
}

export function term(raw: string): Term {
  return requireId(raw, 'Term') as Term;
}
