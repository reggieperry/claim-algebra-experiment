import fc from 'fast-check';

import {
  agentId,
  candidateId,
  questionId,
  type AgentId,
  type ReasoningEvent,
} from '../model';
import { fold } from './fold';
import { buildGraph } from './graph';
import { societyOf } from './society';

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
const a3 = agentId('a3');
const roster: readonly AgentId[] = [a1, a2, a3];
const c1 = candidateId('c1');
const c2 = candidateId('c2');
const c3 = candidateId('c3');
const qa = questionId('qa');

// Derive the society the way the App does — belief and graph from the same log prefix, then project.
function society(events: readonly ReasoningEvent[], playhead: number) {
  const belief = fold(events, playhead);
  const graph = buildGraph(events, playhead);
  return societyOf(events, playhead, belief, roster, graph);
}

describe('societyOf', () => {
  it('lists every roster agent in order, silent ones included', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
    ]);
    const result = society(events, events.length);
    expect(result.agents.map((agent) => agent.id)).toEqual([a1, a2, a3]);
    const silent = result.agents.find((agent) => agent.id === a3);
    expect(silent?.lastSpokeSeq).toBeUndefined();
    expect(silent?.dominantCorner).toBeUndefined();
  });

  it('counts asserts, refutes, and strikes per agent and its last-spoke seq', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'assert', agentId: a1, candidateId: c2, content: 'cat' },
      {
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Alive?',
      },
      { type: 'refute', agentId: a2, candidateId: c2, note: 'no' },
      { type: 'strike', agentId: a2, candidateId: c2, note: 'gone' },
    ]);
    const result = society(events, events.length);
    const first = result.agents.find((agent) => agent.id === a1);
    const second = result.agents.find((agent) => agent.id === a2);
    expect(first).toMatchObject({ asserted: 2, refuted: 0, superseded: 0 });
    // A question counts as speaking even though it is not a claim edge.
    expect(first?.lastSpokeSeq).toBe(3);
    expect(second).toMatchObject({ asserted: 0, refuted: 1, superseded: 1 });
    expect(second?.lastSpokeSeq).toBe(5);
  });

  it("reads an agent's dominant corner from its highest-grade backed candidate", () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'assert', agentId: a2, candidateId: c2, content: 'cat' },
      { type: 'refute', agentId: a3, candidateId: c2, note: 'no' },
    ]);
    const result = society(events, events.length);
    expect(result.agents.find((agent) => agent.id === a1)?.dominantCorner).toBe(
      'resolved',
    );
    expect(result.agents.find((agent) => agent.id === a2)?.dominantCorner).toBe(
      'conflict',
    );
  });

  it('counts distinct live candidate positions as the diversity scalar', () => {
    fc.assert(
      fc.property(fc.subarray([c1, c2, c3], { minLength: 1 }), (rivals) => {
        const events = log(
          rivals.map((id) => ({
            type: 'assert' as const,
            agentId: a1,
            candidateId: id,
            content: 'rival',
          })),
        );
        expect(society(events, events.length).diversity).toBe(rivals.length);
      }),
      { seed: 4242, numRuns: 200 },
    );
  });

  it('flags monoculture when ≥2 voices back one position and nobody has refuted', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
    ]);
    const result = society(events, events.length);
    expect(result.diversity).toBe(1);
    expect(result.monoculture).toBe(true);
  });

  it('does not flag monoculture on a single lone voice', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
    ]);
    expect(society(events, events.length).monoculture).toBe(false);
  });

  it('clears the monoculture flag once any refutation has been raised', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'agreed' },
      { type: 'refute', agentId: a3, candidateId: c1, note: 'wait' },
    ]);
    // One live position remains (the glut), but productive disagreement has occurred.
    expect(society(events, events.length).diversity).toBe(1);
    expect(society(events, events.length).monoculture).toBe(false);
  });

  it('is a pure function of its inputs, and no selection perturbs it (filter is a view, not a re-fold)', () => {
    const genSpec: fc.Arbitrary<EventSpec> = fc.oneof(
      fc.record({
        type: fc.constant('assert' as const),
        agentId: fc.constantFrom(a1, a2, a3),
        candidateId: fc.constantFrom(c1, c2, c3),
        content: fc.string(),
      }),
      fc.record({
        type: fc.constant('refute' as const),
        agentId: fc.constantFrom(a1, a2, a3),
        candidateId: fc.constantFrom(c1, c2, c3),
        note: fc.string(),
      }),
    );
    fc.assert(
      fc.property(
        fc.array(genSpec, { maxLength: 30 }).map(log),
        fc.nat(35),
        fc.nat(35),
        (events, target, detour) => {
          const direct = society(events, target);
          society(events, detour); // a scrub elsewhere must not perturb anything
          expect(society(events, target)).toEqual(direct);
        },
      ),
      { seed: 4242, numRuns: 300 },
    );
  });
});
