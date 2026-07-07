// The model's public surface — the event log's vocabulary and the fold's result types. Import the
// model through this contract, never a deep path into a member file (ts-modules).
export type { AgentId, CandidateId, QuestionId } from './ids';
export { agentId, candidateId, questionId } from './ids';

export type { Answer, EventType, ReasoningEvent } from './event';
export { ANSWERS } from './event';

export type {
  BeliefState,
  BelnapCorner,
  Candidate,
  CurrentQuestion,
  GateDecision,
  Provenance,
} from './belief';
export { BELNAP_CORNERS } from './belief';
