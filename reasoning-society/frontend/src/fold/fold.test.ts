import fc from 'fast-check';

import {
  agentId,
  candidateId,
  questionId,
  term,
  type Answer,
  type ReasoningEvent,
} from '../model';
import { fold } from './fold';

// A distributive omit so each generated spec keeps its own variant payload (see scenario.ts).
type EventSpec = ReasoningEvent extends infer E
  ? E extends ReasoningEvent
    ? Omit<E, 'seq' | 'timestamp'>
    : never
  : never;

// Stamp a list of specs into a well-ordered log (contiguous 1-based seqs), the log's serialization
// guarantee the fold assumes.
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
const c1 = candidateId('c1');
const c2 = candidateId('c2');
const c3 = candidateId('c3');
const qa = questionId('qa');
const qb = questionId('qb');

const genAgent = fc.constantFrom(a1, a2, a3);
const genCandidate = fc.constantFrom(c1, c2, c3);
const genQuestion = fc.constantFrom(qa, qb);
const genAnswer: fc.Arbitrary<Answer> = fc.constantFrom('yes', 'no', 'unknown');

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
    questionId: genQuestion,
    content: fc.string(),
  }),
  fc.record({
    type: fc.constant('answer_given' as const),
    questionId: genQuestion,
    answer: genAnswer,
  }),
  fc.record({
    type: fc.constant('gate_abstain' as const),
    reason: fc.string(),
  }),
  fc.record({
    type: fc.constant('gate_sign' as const),
    candidateId: genCandidate,
  }),
);

const genLog = fc.array(genSpec, { maxLength: 40 }).map(log);

describe('fold', () => {
  it('is a pure function of (events, playhead) — the same call gives the same state', () => {
    fc.assert(
      fc.property(genLog, fc.nat(45), (events, playhead) => {
        expect(fold(events, playhead)).toEqual(fold(events, playhead));
      }),
      { seed: 4242, numRuns: 400 },
    );
  });

  it('scrubbing away and back to seq N reproduces state N exactly (no hidden state)', () => {
    fc.assert(
      fc.property(genLog, fc.nat(45), fc.nat(45), (events, target, detour) => {
        const direct = fold(events, target);
        fold(events, detour); // a scrub elsewhere must not perturb anything
        const returned = fold(events, target);
        expect(returned).toEqual(direct);
      }),
      { seed: 4242, numRuns: 400 },
    );
  });

  it('reaches the same full state whether the playhead sits at or past the last event', () => {
    fc.assert(
      fc.property(genLog, (events) => {
        const atEnd = fold(events, events.length);
        const pastEnd = fold(events, events.length + 10);
        expect(pastEnd.candidates).toEqual(atEnd.candidates);
        expect(pastEnd.cardinality).toBe(atEnd.cardinality);
      }),
      { seed: 4242, numRuns: 300 },
    );
  });

  it('a refute on a live (asserted, unstruck) candidate yields the Conflict glut', () => {
    fc.assert(
      fc.property(
        genAgent,
        genAgent,
        fc.string(),
        (asserter, refuter, content) => {
          const events = log([
            { type: 'assert', agentId: asserter, candidateId: c1, content },
            { type: 'refute', agentId: refuter, candidateId: c1, note: 'no' },
          ]);
          const state = fold(events, events.length);
          const target = state.candidates.find(
            (candidate) => candidate.id === c1,
          );
          expect(target?.corner).toBe('conflict');
        },
      ),
      { seed: 4242, numRuns: 300 },
    );
  });

  it('asserting N distinct rivals raises cardinality to N', () => {
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
        expect(fold(events, events.length).cardinality).toBe(rivals.length);
      }),
      { seed: 4242, numRuns: 200 },
    );
  });

  it('an empty playhead is the empty belief — no candidates, gate watching, no question', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
    ]);
    const state = fold(events, 0);
    expect(state.candidates).toEqual([]);
    expect(state.cardinality).toBe(0);
    expect(state.gate).toEqual({ kind: 'watching' });
    expect(state.currentQuestion).toBeUndefined();
  });

  it('a strike supersedes a candidate and drops it from the live count', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'assert', agentId: a2, candidateId: c2, content: 'cat' },
      { type: 'strike', agentId: a3, candidateId: c2, note: 'out' },
    ]);
    const state = fold(events, events.length);
    const cat = state.candidates.find((candidate) => candidate.id === c2);
    expect(cat?.corner).toBe('superseded');
    expect(cat?.grade).toBe(0);
    expect(state.cardinality).toBe(1);
  });

  it('counts distinct supporting agents, not raw pro events', () => {
    const events = log([
      { type: 'assert', agentId: a1, candidateId: c1, content: 'dog' },
      { type: 'corroborate', agentId: a1, candidateId: c1, note: 'again' },
      { type: 'corroborate', agentId: a2, candidateId: c1, note: 'me too' },
    ]);
    const dog = fold(events, events.length).candidates.find(
      (candidate) => candidate.id === c1,
    );
    expect(dog?.supportingAgents).toEqual([a1, a2]);
    expect(dog?.provenance.supporting).toEqual([1, 2, 3]);
  });

  it('reads the current question and its answer from the latest asked question', () => {
    const events = log([
      {
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Alive?',
      },
      { type: 'answer_given', questionId: qa, answer: 'yes' },
    ]);
    expect(fold(events, 1).currentQuestion).toEqual({
      questionId: qa,
      content: 'Alive?',
      proposedBy: a1,
      answer: undefined,
      definitions: [],
      pendingChallenge: undefined,
    });
    expect(fold(events, 2).currentQuestion?.answer).toBe('yes');
  });
});

// The clarification pair (clarification-feature §4) moves NO belief — it only decorates the current
// question. Mirrors the Scala `GameCore.project` dropping `ClarificationRequested`/`DefinitionGiven`.
describe('fold — clarification events are belief-inert', () => {
  // Two well-formed inert events appended at the END of a base log (higher seqs, so the base events'
  // seqs — and therefore their provenance — are untouched).
  function inertTail(afterSeq: number): readonly ReasoningEvent[] {
    return [
      {
        seq: afterSeq + 1,
        timestamp: 0,
        type: 'clarification_requested',
        questionId: qa,
        term: term('alive'),
      },
      {
        seq: afterSeq + 2,
        timestamp: 0,
        type: 'definition_given',
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
    ];
  }

  it('appending the clarification pair leaves candidates, cardinality, and gate unchanged', () => {
    fc.assert(
      fc.property(genLog, (base) => {
        const extended = [...base, ...inertTail(base.length)];
        const baseState = fold(base, base.length);
        const extendedState = fold(extended, extended.length);
        expect(extendedState.candidates).toEqual(baseState.candidates);
        expect(extendedState.cardinality).toBe(baseState.cardinality);
        expect(extendedState.gate).toEqual(baseState.gate);
      }),
      { seed: 4242, numRuns: 200 },
    );
  });
});

// The ORDERING GATE derivation (clarification-feature §3): a challenge holds answering until the
// asking agent defines the term. Everything is read from the fold — no separate flag.
describe('fold — the current question tracks the clarification exchange', () => {
  const asked: ReasoningEvent[] = [
    {
      seq: 1,
      timestamp: 1,
      type: 'question_asked',
      agentId: a1,
      questionId: qa,
      content: 'Is it alive?',
    },
  ];

  it('an open challenge (no definition yet) is a pending challenge that gates answering', () => {
    const events = [
      ...asked,
      {
        seq: 2,
        timestamp: 2,
        type: 'clarification_requested' as const,
        questionId: qa,
        term: term('alive'),
      },
    ];
    const cq = fold(events, events.length).currentQuestion;
    expect(cq?.pendingChallenge).toEqual({ term: term('alive') });
    expect(cq?.definitions).toEqual([]);
    expect(cq?.answer).toBeUndefined();
  });

  it('a definition after the challenge clears the pending state and records the claim', () => {
    const events = [
      ...asked,
      {
        seq: 2,
        timestamp: 2,
        type: 'clarification_requested' as const,
        questionId: qa,
        term: term('alive'),
      },
      {
        seq: 3,
        timestamp: 3,
        type: 'definition_given' as const,
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
    ];
    // Before the definition (playhead 2) the challenge is open; after it (playhead 3) it is cleared —
    // and scrubbing re-derives each, no hidden state.
    expect(fold(events, 2).currentQuestion?.pendingChallenge).toEqual({
      term: term('alive'),
    });
    const cq = fold(events, 3).currentQuestion;
    expect(cq?.pendingChallenge).toBeUndefined();
    expect(cq?.definitions).toHaveLength(1);
    expect(cq?.definitions[0]?.meaning).toBe(
      'a living creature currently alive',
    );
    expect(fold(events, 2).currentQuestion?.pendingChallenge).toEqual({
      term: term('alive'),
    });
  });

  it('a second challenge after a definition re-opens the gate', () => {
    const events: ReasoningEvent[] = [
      ...asked,
      {
        seq: 2,
        timestamp: 2,
        type: 'clarification_requested',
        questionId: qa,
        term: term('alive'),
      },
      {
        seq: 3,
        timestamp: 3,
        type: 'definition_given',
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
      {
        seq: 4,
        timestamp: 4,
        type: 'clarification_requested',
        questionId: qa,
        term: term('creature'),
      },
    ];
    expect(fold(events, 3).currentQuestion?.pendingChallenge).toBeUndefined();
    expect(fold(events, 4).currentQuestion?.pendingChallenge).toEqual({
      term: term('creature'),
    });
  });

  it('an answer closes the exchange — no challenge stays outstanding even if undefined', () => {
    const events: ReasoningEvent[] = [
      ...asked,
      {
        seq: 2,
        timestamp: 2,
        type: 'clarification_requested',
        questionId: qa,
        term: term('alive'),
      },
      {
        seq: 3,
        timestamp: 3,
        type: 'answer_given',
        questionId: qa,
        answer: 'yes',
      },
    ];
    const cq = fold(events, events.length).currentQuestion;
    expect(cq?.answer).toBe('yes');
    expect(cq?.pendingChallenge).toBeUndefined();
  });
});

// A RECALLED definition (persistent memory seeded into a fresh game, two-tier-reset-design) is
// belief-inert like the clarification pair AND must not perturb the ordering gate on a colliding qid
// (invariant 7). Mirrors the Scala `GameCore.project` dropping `DefinitionRemembered`.
describe('fold — a recalled definition is belief-inert', () => {
  function rememberedTail(afterSeq: number): readonly ReasoningEvent[] {
    return [
      {
        seq: afterSeq + 1,
        timestamp: 0,
        type: 'definition_remembered',
        term: term('alive'),
        meaning: 'a living creature currently alive',
        // The origin's questionId is `qa` — a qid that can COLLIDE with a base-log question, exactly
        // the cross-game collision the distinct variant exists to survive.
        origin: { gameId: 1, agentId: a1, questionId: qa, seq: 9 },
      },
    ];
  }

  it('appending a recalled definition leaves candidates, cardinality, gate, and the question unchanged', () => {
    fc.assert(
      fc.property(genLog, (base) => {
        const extended = [...base, ...rememberedTail(base.length)];
        const baseState = fold(base, base.length);
        const extendedState = fold(extended, extended.length);
        expect(extendedState.candidates).toEqual(baseState.candidates);
        expect(extendedState.cardinality).toBe(baseState.cardinality);
        expect(extendedState.gate).toEqual(baseState.gate);
        // The current-question read (and its ordering gate) is untouched — the recalled definition
        // belongs to no this-game question.
        expect(extendedState.currentQuestion).toEqual(
          baseState.currentQuestion,
        );
      }),
      { seed: 4242, numRuns: 200 },
    );
  });

  it('a recalled definition on a COLLIDING qid does NOT clear an open challenge (invariant 7)', () => {
    const events: ReasoningEvent[] = [
      {
        seq: 1,
        timestamp: 1,
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Is it alive?',
      },
      {
        seq: 2,
        timestamp: 2,
        type: 'clarification_requested',
        questionId: qa,
        term: term('alive'),
      },
      {
        seq: 3,
        timestamp: 3,
        type: 'definition_remembered',
        term: term('alive'),
        meaning: 'a living creature currently alive',
        // origin.questionId === qa — the SAME qid the open challenge is on.
        origin: { gameId: 1, agentId: a1, questionId: qa, seq: 9 },
      },
    ];
    // A this-game `definition_given` on qa WOULD clear the challenge; a recalled one must NOT — it
    // never writes `definitionsByQuestion`, so the ordering gate stays open.
    expect(fold(events, 3).currentQuestion?.pendingChallenge).toEqual({
      term: term('alive'),
    });
    // And it records no definition against this-game's question.
    expect(fold(events, 3).currentQuestion?.definitions).toEqual([]);
  });
});
