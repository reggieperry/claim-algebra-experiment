import type { AgentId, CandidateId, QuestionId } from './ids';

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
// `assert` / `corroborate` / `refute` / `strike` move HYPOTHESES (what the answer is). The gate and
// question events are the society's control flow, logged so replay and the graph stay rich (§3).
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
      readonly type: 'answer_given';
      readonly questionId: QuestionId;
      readonly answer: Answer;
    })
  | (EventBase & { readonly type: 'gate_abstain'; readonly reason: string })
  | (EventBase & {
      readonly type: 'gate_sign';
      readonly candidateId: CandidateId;
    });

export type EventType = ReasoningEvent['type'];
