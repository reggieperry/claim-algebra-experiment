// The fold's public surface — the pure reducer, the gate's read, the grade function (brief §1), and
// the Build 2 projections layered over the same log: the agent→claim graph, the society summary, and
// the memory store. Each is a pure reader of `(events, playhead)`; none takes the selection.
export {
  fold,
  answerSeqFor,
  signableCandidate,
  SIGN_THRESHOLD,
  MIN_CORROBORATORS,
} from './fold';
export { gradeOf } from './grade';

export { buildGraph, candidatesTouchedBy, RELATIONS } from './graph';
export type { ClaimEdge, Relation, SocietyGraph } from './graph';

export { societyOf } from './society';
export type { AgentSummary, Society } from './society';

export { memoryOf } from './memory';
export type { Fact, Memory, MethodCalibration, Relationship } from './memory';
