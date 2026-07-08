import type { AgentId, CandidateId, QuestionId, Term } from './ids';

// The oracle's verdict on a question. `unknown` is a real, distinct answer (the human may not know),
// never a stand-in for "unanswered" — an unanswered question simply has no `answer_given` event yet.
export const ANSWERS = ['yes', 'no', 'unknown'] as const;
export type Answer = (typeof ANSWERS)[number];

// Fields every event carries. `seq` is 1-based and contiguous — it is the global serialization
// point (brief §1.5): the log assigns the order the fold consumes. `timestamp` is display-only.
interface EventBase {
  readonly seq: number;
  readonly timestamp: number;
}

// The provenance of a RECALLED definition (two-tier-reset-design) — which game / agent / question
// first established the meaning, mirroring the Scala `DefinitionProvenance` nested in the wire's
// `origin` object. `gameId` is OPTIONAL: absent when the provenance is not yet stamped (the current,
// not-yet-persisted game); present as the game the meaning was first established in. It is the audit
// surface's "recalled from game N" — read from `origin`, never the recalled event's own `seq`.
export interface DefinitionOrigin {
  readonly gameId?: number;
  readonly agentId: AgentId;
  readonly questionId: QuestionId;
  readonly seq: number;
}

// The single ordered stream the fold folds. A discriminated union on `type`: each variant carries
// exactly its own payload, so an impossible combination (a `gate_sign` with a `questionId`) cannot
// be constructed and a `switch` over `type` is exhaustively checked (ts-types).
//
// Two layers of claim (brief §2): `answer_given` is EVIDENCE (the oracle, ground truth);
// `assert` / `corroborate` / `refute` / `strike` move HYPOTHESES (what the answer is). The gate,
// question, and clarification events are the society's control flow, logged so replay and the graph
// stay rich (§3) but moving no belief. The clarification pair — `clarification_requested` (the
// human's challenge) and `definition_given` (the asking agent's meaning) — negotiates the shared
// vocabulary a grounded answer is grounded to (clarification-feature §4): also belief-inert.
export type ReasoningEvent =
  | (EventBase & {
      readonly type: 'assert';
      readonly agentId: AgentId;
      readonly candidateId: CandidateId;
      readonly content: string;
    })
  | (EventBase & {
      readonly type: 'corroborate';
      readonly agentId: AgentId;
      readonly candidateId: CandidateId;
      readonly note: string;
    })
  | (EventBase & {
      readonly type: 'refute';
      readonly agentId: AgentId;
      readonly candidateId: CandidateId;
      readonly note: string;
    })
  | (EventBase & {
      readonly type: 'strike';
      readonly agentId: AgentId;
      readonly candidateId: CandidateId;
      readonly note: string;
    })
  | (EventBase & {
      readonly type: 'question_proposed';
      readonly agentId: AgentId;
      readonly questionId: QuestionId;
      readonly content: string;
    })
  | (EventBase & {
      readonly type: 'question_asked';
      readonly agentId: AgentId;
      readonly questionId: QuestionId;
      readonly content: string;
    })
  | (EventBase & {
      readonly type: 'clarification_requested';
      // The HUMAN's move — no `agentId`; the human/oracle seam sits outside the actor graph, exactly
      // like `answer_given` (mirrors the Scala `ClarificationRequested`).
      readonly questionId: QuestionId;
      readonly term: Term;
    })
  | (EventBase & {
      readonly type: 'definition_given';
      // The ASKING agent supplies the meaning — carries an `agentId` (the proposer / provenance).
      readonly agentId: AgentId;
      readonly questionId: QuestionId;
      readonly term: Term;
      readonly meaning: string;
    })
  | (EventBase & {
      readonly type: 'definition_remembered';
      // A definition RECALLED from persistent memory into a fresh game (two-tier-reset-design): the
      // session's established vocabulary, replayed at the head of the log. Belief-inert like the
      // clarification pair — a DISTINCT variant, NOT a re-emitted `definition_given`, because the
      // cross-game questionId collision is guaranteed (both games mint `q1`), so it must be
      // structurally excluded from the current-question / ordering-gate read. It carries NO top-level
      // `agentId` (its author spoke in a prior game); its provenance is the nested `origin`.
      readonly term: Term;
      readonly meaning: string;
      readonly origin: DefinitionOrigin;
    })
  | (EventBase & {
      readonly type: 'answer_given';
      readonly questionId: QuestionId;
      readonly answer: Answer;
      // The governing definition(s) a clarified answer was grounded to (clarification-feature §4) —
      // OMITTED on the wire when empty, so a non-clarified answer decodes to no `governing` key
      // (exactOptionalPropertyTypes: absent ≠ present-and-empty). Decoration only; belief-inert.
      readonly governing?: readonly Term[];
    })
  | (EventBase & { readonly type: 'gate_abstain'; readonly reason: string })
  | (EventBase & {
      readonly type: 'gate_sign';
      readonly candidateId: CandidateId;
    })
  | (EventBase & {
      readonly type: 'retired';
      // The librarian RETIRED a defeated hypothesis to trace (hypothesis-lifecycle §A/§B): its pro
      // channel lost all live support (every pro-author self-withdrew) and its con channel carries
      // ≥2 standing refutations. Off the live board, kept as citable trace. NO `agentId` — the
      // librarian, not an agent, emits it (mirrors the Scala `Event.Retired`). Belief-inert: it is
      // the audit/UI TRACE of a retirement the recomputed predicate authoritatively decides, so it
      // moves no belief — masking is driven by the predicate, never by this marker.
      readonly candidateId: CandidateId;
    })
  | (EventBase & {
      readonly type: 'resurrected';
      // The librarian RESURRECTED a previously-retired hypothesis (hypothesis-lifecycle §B,
      // recovery): fresh live support arrived above the latest refutation, so the predicate no
      // longer defeats it and it returns to the live board. NO `agentId`, belief-inert exactly as
      // `retired` — a re-fold consequence, never an un-delete (mirrors the Scala `Event.Resurrected`).
      readonly candidateId: CandidateId;
    })
  | (EventBase & {
      readonly type: 'convergence_warning';
      // The librarian's NON-CONVERGENCE flag (librarian-convergence-monitor): a purely STRUCTURAL
      // detection that the search is not converging — no candidate consolidating, a persistent glut,
      // the round budget spent without a signable candidate. It is DETECT-not-DIAGNOSE, so it carries
      // the two structural COUNTS only and NEVER a `candidateId` or a reason string — the librarian is
      // non-generative and cannot judge WHICH premise is wrong; the human, who holds the ground truth,
      // diagnoses. Belief-inert like the lifecycle markers: it moves no hypothesis, changes no gate;
      // it is a request for the human's help ("reconsider an earlier answer"), never a permission to
      // guess. NO `agentId` — the librarian emits it, not an agent (mirrors `Event.ConvergenceWarning`).
      readonly roundsWithoutConsolidation: number;
      readonly glutPersistence: number;
    })
  | (EventBase & {
      readonly type: 'guess_answered';
      // The society POSED A GUESS to the oracle — "is it <candidateId>?" — and got `answer` (B1,
      // recovery-and-endgame). The endgame move: when the search stalls on a lone, unconfirmed
      // candidate, the society asks the oracle directly rather than dying blank. Belief-inert — it
      // moves no hypothesis itself; the WORK is done by the fold's masking (a `no` drops the
      // candidate off the board) and the gate's floor relaxation (a `yes` confirms it). NO `agentId`
      // — the oracle is not an agent; the guess is posed by the society from the gate's own clean
      // winner (mirrors the Scala `Event.GuessAnswered`).
      readonly candidateId: CandidateId;
      readonly answer: Answer;
    });

export type EventType = ReasoningEvent['type'];
