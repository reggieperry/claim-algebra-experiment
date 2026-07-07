import fc from 'fast-check';

import {
  agentId,
  candidateId,
  questionId,
  type Answer,
  type ReasoningEvent,
} from '../model';
import { fold } from './fold';
import { buildGraph } from './graph';
import { memoryOf } from './memory';

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
const qb = questionId('qb');

function memory(events: readonly ReasoningEvent[], playhead: number) {
  const belief = fold(events, playhead);
  const graph = buildGraph(events, playhead);
  return memoryOf(events, playhead, belief, graph);
}

describe('memoryOf — Tier 1 facts', () => {
  it('records one fact per grounded answer, with its establishing seq', () => {
    const events = log([
      {
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Alive?',
      },
      { type: 'answer_given', questionId: qa, answer: 'yes' },
    ]);
    const facts = memory(events, events.length).facts;
    expect(facts).toHaveLength(1);
    expect(facts[0]).toMatchObject({
      statement: 'Alive? — YES',
      corner: 'resolved',
      establishedSeq: 2,
      reopened: false,
    });
  });

  it('records the gate-signed value as a fact carrying its supporting agents', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
      { type: 'gate_sign', candidateId: c1 },
    ]);
    const signed = memory(events, events.length).facts.find(
      (fact) => fact.key === `sign:${c1}`,
    );
    expect(signed?.statement).toBe('dog');
    expect(signed?.corner).toBe('resolved');
    expect([...(signed?.agents ?? [])].sort()).toEqual([a1, a2]);
  });

  it('flags a signed value that has since fallen into conflict as reopened', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
      { type: 'gate_sign', candidateId: c1 },
      { type: 'refute', agentId: a2, candidateId: c1, note: 'actually no' },
    ]);
    const signed = memory(events, events.length).facts.find(
      (fact) => fact.key === `sign:${c1}`,
    );
    expect(signed?.corner).toBe('conflict');
    expect(signed?.reopened).toBe(true);
  });

  it('never surfaces a fact before its establishing seq (it re-derives on scrub)', () => {
    const genSpec: fc.Arbitrary<EventSpec> = fc.oneof(
      fc.record({
        type: fc.constant('answer_given' as const),
        questionId: fc.constantFrom(qa, qb),
        answer: fc.constantFrom<Answer>('yes', 'no', 'unknown'),
      }),
      fc.record({
        type: fc.constant('gate_sign' as const),
        candidateId: fc.constantFrom(c1, c2),
      }),
      fc.record({
        type: fc.constant('assert' as const),
        agentId: fc.constantFrom(a1, a2),
        candidateId: fc.constantFrom(c1, c2),
        content: fc.string(),
      }),
    );
    fc.assert(
      fc.property(
        fc.array(genSpec, { maxLength: 30 }).map(log),
        fc.nat(35),
        (events, playhead) => {
          for (const fact of memory(events, playhead).facts) {
            expect(fact.establishedSeq).toBeLessThanOrEqual(playhead);
          }
        },
      ),
      { seed: 4242, numRuns: 300 },
    );
  });

  it('accumulates facts monotonically as the playhead advances', () => {
    const events = log([
      {
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Alive?',
      },
      { type: 'answer_given', questionId: qa, answer: 'yes' },
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'gate_sign', candidateId: c1 },
    ]);
    fc.assert(
      fc.property(fc.nat(6), fc.nat(6), (p, q) => {
        const lo = Math.min(p, q);
        const hi = Math.max(p, q);
        expect(memory(events, lo).facts.length).toBeLessThanOrEqual(
          memory(events, hi).facts.length,
        );
      }),
      { seed: 4242, numRuns: 200 },
    );
  });
});

describe('memoryOf — Tier 2 relationships and Tier 3 methods', () => {
  it('rolls the agent→claim edges into strength-counted relationships', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a1, candidateId: c1, note: 'again' },
    ]);
    const relationships = memory(events, events.length).relationships;
    const asserts = relationships.find(
      (relationship) => relationship.relation === 'asserts',
    );
    const corroborates = relationships.find(
      (relationship) => relationship.relation === 'corroborates',
    );
    expect(asserts).toMatchObject({
      agentId: a1,
      candidateId: c1,
      strength: 1,
    });
    expect(corroborates?.lastSeq).toBe(2);
  });

  it('reports each method with its sample size beside the rate', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
      { type: 'gate_sign', candidateId: c1 },
    ]);
    const methods = memory(events, events.length).methods;
    const sign = methods.find((method) => method.method === 'gate sign');
    expect(sign).toMatchObject({ heldUp: 1, sample: 1 });
  });

  it('is a pure function of (events, playhead) — same call, same memory', () => {
    const events = log([
      { type: 'answer_given', questionId: qa, answer: 'yes' },
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'gate_sign', candidateId: c1 },
    ]);
    fc.assert(
      fc.property(fc.nat(5), (playhead) => {
        expect(memory(events, playhead)).toEqual(memory(events, playhead));
      }),
      { seed: 4242, numRuns: 100 },
    );
  });
});
