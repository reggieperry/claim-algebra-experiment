import type { Answer } from './event';
import type { AgentId, CandidateId, QuestionId } from './ids';

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
// been given at or before the playhead (`undefined` while it is still open).
export interface CurrentQuestion {
  readonly questionId: QuestionId;
  readonly content: string;
  readonly proposedBy: AgentId;
  readonly answer: Answer | undefined;
}

// The whole fold result at a playhead — what every panel reads. Nothing here is stored between
// frames; it is recomputed by the fold each render (brief §1).
export interface BeliefState {
  readonly playhead: number;
  readonly candidates: readonly Candidate[]; // sorted by grade desc, then first-seen
  readonly cardinality: number; // count of live for-candidates (corner === 'resolved')
  readonly gate: GateDecision;
  readonly currentQuestion: CurrentQuestion | undefined;
}
