import type { DefinitionClaim } from './definition';
import type { Answer } from './event';
import type { AgentId, CandidateId, QuestionId, Term } from './ids';

// The four structural corners of the four-state read, mirrored from the algebra's `Status`
// (claimalgebra.calculus.Resolution): read STRUCTURALLY from a candidate's pro/con/struck evidence,
// never from a self-reported confidence.
//   resolved   — pro-only, uncontradicted           (Belnap True)
//   conflict   — pro AND con, a contradiction glut   (Belnap Glut)
//   superseded — struck                              (Belnap False)
//   missing    — no signable support                 (Belnap Gap)
export const BELNAP_CORNERS = [
  'resolved',
  'conflict',
  'superseded',
  'missing',
] as const;
export type BelnapCorner = (typeof BELNAP_CORNERS)[number];

// The lineage: which event seqs support (pro) and which oppose (con) a candidate. The grade is
// computed from this, never carried by an agent's own claim (brief §3).
export interface Provenance {
  readonly supporting: readonly number[];
  readonly opposing: readonly number[];
}

// A hypothesis as derived state — never stored, always a reading of the log at the playhead.
export interface Candidate {
  readonly id: CandidateId;
  readonly content: string;
  readonly provenance: Provenance;
  // Distinct agents on the pro channel — the no-lone-sign floor counts these, not raw pro events.
  readonly supportingAgents: readonly AgentId[];
  readonly corner: BelnapCorner;
  readonly grade: number; // in [0, 1]
}

// The gate's current decision, read from the latest gate event at the playhead. `watching` is the
// pre-decision state before any gate event. The gate signs only a lone `resolved` candidate that
// clears the grade threshold and the ≥2-corroborator floor; otherwise it abstains (brief §2).
export type GateDecision =
  | { readonly kind: 'watching' }
  | {
      readonly kind: 'abstained';
      readonly reason: string;
      readonly seq: number;
    }
  | {
      readonly kind: 'signed';
      readonly candidateId: CandidateId;
      readonly seq: number;
    };

// The question currently before the oracle: the latest asked question and its answer if one has
// been given at or before the playhead (`undefined` while it is still open). The two clarification
// reads below are derived from the same fold (clarification-feature §5): they surface the challenge
// exchange and drive the client-side ORDERING GATE that enforces challenge → definition → answer.
export interface CurrentQuestion {
  readonly questionId: QuestionId;
  readonly content: string;
  readonly proposedBy: AgentId;
  readonly answer: Answer | undefined;
  // Definitions established for THIS question's clarification exchanges, in seq order — rendered as
  // claims beside the question (§4). Empty when the question was never challenged.
  readonly definitions: readonly DefinitionClaim[];
  // An OPEN challenge: the human challenged a term and the asking agent has not defined it yet, so
  // answering is GATED until the definition arrives (the critical UX rule, §3). `undefined` when
  // no challenge is outstanding (never challenged, the latest challenge already answered by a
  // definition, or the question already answered). Derived, never stored.
  readonly pendingChallenge: PendingChallenge | undefined;
}

// The outstanding challenge on the current question — the term whose definition is being awaited.
export interface PendingChallenge {
  readonly term: Term;
}

// The librarian's non-convergence flag as DERIVED state (librarian-convergence-monitor): the two
// STRUCTURAL counts of the most recent `convergence_warning` in scope at the playhead that has not
// since been cleared by a `gate_sign`. It carries NO candidate and NO diagnosis — detect-not-diagnose:
// the librarian flags THAT the search is structurally stuck (no candidate consolidating / a persistent
// glut), never WHICH answer is wrong; the human, who holds the ground truth, reconsiders an earlier
// answer. Derived by the fold, stored nowhere — `undefined` when the search is not flagged as stuck
// (never warned, or a later sign cleared it), so scrubbing re-derives its presence at each playhead.
export interface ConvergenceStatus {
  readonly roundsWithoutConsolidation: number;
  readonly glutPersistence: number;
}

// The whole fold result at a playhead — what every panel reads. Nothing here is stored between
// frames; it is recomputed by the fold each render (brief §1).
export interface BeliefState {
  readonly playhead: number;
  readonly candidates: readonly Candidate[]; // sorted by grade desc, then first-seen
  readonly cardinality: number; // count of live for-candidates (corner === 'resolved')
  readonly gate: GateDecision;
  // The non-convergence flag in scope at the playhead, or `undefined` when the search is not flagged
  // as stuck (librarian-convergence-monitor). BELIEF-INERT — it never changes `candidates`/`gate`; it
  // is a separate structural read the fold tracks alongside the gate, cleared by a later `gate_sign`,
  // so replay shows it appear at the warning and clear on convergence (scrubbable).
  readonly convergence: ConvergenceStatus | undefined;
  readonly currentQuestion: CurrentQuestion | undefined;
}
