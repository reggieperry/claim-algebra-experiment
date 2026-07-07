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
    });

export type EventType = ReasoningEvent['type'];
