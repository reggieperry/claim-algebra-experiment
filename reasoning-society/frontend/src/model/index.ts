// The model's public surface — the event log's vocabulary and the fold's result types. Import the
// model through this contract, never a deep path into a member file (ts-modules).
export type { AgentId, CandidateId, QuestionId, Term } from './ids';
export { agentId, candidateId, questionId, term } from './ids';

export type {
  Answer,
  DefinitionOrigin,
  EventType,
  ReasoningEvent,
} from './event';
export { ANSWERS } from './event';

export type { DefinitionClaim, DefinitionClaimOrigin } from './definition';

export type {
  BeliefState,
  BelnapCorner,
  Candidate,
  CurrentQuestion,
  GateDecision,
  PendingChallenge,
  Provenance,
} from './belief';
export { BELNAP_CORNERS } from './belief';
