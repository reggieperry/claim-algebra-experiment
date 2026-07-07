import fc from 'fast-check';

import {
  agentId,
  candidateId,
  questionId,
  type ReasoningEvent,
} from '../model';
import { buildGraph, candidatesTouchedBy } from './graph';

// A distributive omit so each generated spec keeps its own variant payload (see scenario.ts).
type EventSpec = ReasoningEvent extends infer E
  ? E extends ReasoningEvent
    ? Omit<E, 'seq' | 'timestamp'>
    : never
  : never;

function log(specs: readonly EventSpec[]): readonly ReasoningEvent[] {
  return specs.map((spec, index) => ({
    ...spec,
    seq: index + 1,
    timestamp: 1_000 * (index + 1),
  }));
}

const a1 = agentId('a1');
const a2 = agentId('a2');
const c1 = candidateId('c1');
const c2 = candidateId('c2');
const qa = questionId('qa');

const genAgent = fc.constantFrom(a1, a2);
const genCandidate = fc.constantFrom(c1, c2);

// Only the four claim-touching variants become edges; questions/answers/gate events do not.
const genSpec: fc.Arbitrary<EventSpec> = fc.oneof(
  fc.record({
    type: fc.constant('assert' as const),
    agentId: genAgent,
    candidateId: genCandidate,
    content: fc.string(),
  }),
  fc.record({
    type: fc.constant('corroborate' as const),
    agentId: genAgent,
    candidateId: genCandidate,
    note: fc.string(),
  }),
  fc.record({
    type: fc.constant('refute' as const),
    agentId: genAgent,
    candidateId: genCandidate,
    note: fc.string(),
  }),
  fc.record({
    type: fc.constant('strike' as const),
    agentId: genAgent,
    candidateId: genCandidate,
    note: fc.string(),
  }),
  fc.record({
    type: fc.constant('question_asked' as const),
    agentId: genAgent,
    questionId: fc.constant(qa),
    content: fc.string(),
  }),
  fc.record({
    type: fc.constant('gate_abstain' as const),
    reason: fc.string(),
  }),
);

const genLog = fc.array(genSpec, { maxLength: 40 }).map(log);

function isClaimTouching(spec: EventSpec): boolean {
  return (
    spec.type === 'assert' ||
    spec.type === 'corroborate' ||
    spec.type === 'refute' ||
    spec.type === 'strike'
  );
}

describe('buildGraph', () => {
  it('maps each event verb to its relation and records the birth seq', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
      { type: 'refute', agentId: a2, candidateId: c2, note: 'no' },
      { type: 'strike', agentId: a1, candidateId: c2, note: 'gone' },
    ]);
    const graph = buildGraph(events, events.length);
    expect(graph.edges).toEqual([
      { agentId: a1, candidateId: c1, relation: 'asserts', seq: 1 },
      { agentId: a2, candidateId: c1, relation: 'corroborates', seq: 2 },
      { agentId: a2, candidateId: c2, relation: 'refutes', seq: 3 },
      { agentId: a1, candidateId: c2, relation: 'strikes', seq: 4 },
    ]);
    expect(graph.agents).toEqual([a1, a2]);
    expect(graph.claims).toEqual([c1, c2]);
  });

  it('emits exactly one edge per claim-touching event at or before the playhead', () => {
    fc.assert(
      fc.property(genLog, fc.nat(45), (events, playhead) => {
        const expected = events.filter(
          (event) => event.seq <= playhead && isClaimTouching(event),
        ).length;
        expect(buildGraph(events, playhead).edges).toHaveLength(expected);
      }),
      { seed: 4242, numRuns: 300 },
    );
  });

  it('is a pure function of (events, playhead) — same call, same graph', () => {
    fc.assert(
      fc.property(genLog, fc.nat(45), (events, playhead) => {
        expect(buildGraph(events, playhead)).toEqual(
          buildGraph(events, playhead),
        );
      }),
      { seed: 4242, numRuns: 300 },
    );
  });
});

describe('candidatesTouchedBy', () => {
  it('returns exactly the claims an agent has touched, in any relation', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'refute', agentId: a1, candidateId: c2, note: 'no' },
      { type: 'assert', agentId: a2, candidateId: c2, content: 'cat' },
    ]);
    const graph = buildGraph(events, events.length);
    expect([...candidatesTouchedBy(graph, a1)].sort()).toEqual([c1, c2]);
    expect([...candidatesTouchedBy(graph, a2)]).toEqual([c2]);
  });
});
