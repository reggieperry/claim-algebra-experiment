import { describe, expect, it } from 'vitest';

import { agentId, questionId, term, type ReasoningEvent } from '../model';
import { definitionsOf } from './definitions';

// A distributive omit so each spec keeps its own variant payload (see scenario.ts / fold.test.ts).
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
const qa = questionId('qa');

describe('definitionsOf', () => {
  it('derives one established definition per definition_given event', () => {
    const events = log([
      {
        type: 'definition_given',
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
    ]);
    expect(definitionsOf(events, events.length)).toEqual([
      {
        term: term('alive'),
        meaning: 'a living creature currently alive',
        establishedSeq: 1,
        origin: { agent: a1, questionId: qa },
      },
    ]);
  });

  it('is empty before a definition is born, and re-derives as the playhead crosses it (scrub)', () => {
    const events = log([
      {
        type: 'question_asked',
        agentId: a1,
        questionId: qa,
        content: 'Is it alive?',
      },
      { type: 'clarification_requested', questionId: qa, term: term('alive') },
      {
        type: 'definition_given',
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
    ]);
    // Born at e-3: absent at any playhead before 3, present at and after it.
    expect(definitionsOf(events, 2)).toEqual([]);
    expect(definitionsOf(events, 3)).toHaveLength(1);
    // Scrubbing back to 2 re-derives the empty list — no hidden state.
    expect(definitionsOf(events, 2)).toEqual([]);
  });

  it('collapses a re-defined term to its latest meaning, in first-seen order, in place', () => {
    const events = log([
      {
        type: 'definition_given',
        agentId: a1,
        questionId: qa,
        term: term('alive'),
        meaning: 'first meaning',
      },
      {
        type: 'definition_given',
        agentId: a2,
        questionId: qa,
        term: term('pet'),
        meaning: 'kept as a companion',
      },
      {
        type: 'definition_given',
        agentId: a2,
        questionId: qa,
        term: term('alive'),
        meaning: 'a living creature currently alive',
      },
    ]);
    const derived = definitionsOf(events, events.length);
    // One row per term (not per event), first-seen term order: alive then pet.
    expect(derived.map((definition) => definition.term)).toEqual([
      term('alive'),
      term('pet'),
    ]);
    // The 'alive' row carries the LATEST meaning and its latest provenance (agent a2, seq 3).
    expect(derived[0]).toEqual({
      term: term('alive'),
      meaning: 'a living creature currently alive',
      establishedSeq: 3,
      origin: { agent: a2, questionId: qa },
    });
  });

  it('folds a RECALLED definition into the read, using its origin provenance', () => {
    const events = log([
      {
        type: 'definition_remembered',
        term: term('alive'),
        meaning: 'a living creature currently alive',
        origin: { gameId: 1, agentId: a1, questionId: qa, seq: 9 },
      },
    ]);
    // A recalled definition (persistent memory) shows in the definitions list; its provenance is the
    // ORIGIN (the game/agent/question that first established it — gameId present), its seek target its
    // birth index here.
    expect(definitionsOf(events, events.length)).toEqual([
      {
        term: term('alive'),
        meaning: 'a living creature currently alive',
        establishedSeq: 1,
        origin: { agent: a1, questionId: qa, gameId: 1 },
      },
    ]);
  });

  it('this-game-wins: a this-game redefinition supersedes a recalled definition in place', () => {
    const events = log([
      {
        type: 'definition_remembered',
        term: term('alive'),
        meaning: 'recalled: any living tissue',
        origin: { gameId: 1, agentId: a1, questionId: qa, seq: 9 },
      },
      {
        type: 'definition_given',
        agentId: a2,
        questionId: qa,
        term: term('alive'),
        meaning: 'this game: a living creature currently alive',
      },
    ]);
    const derived = definitionsOf(events, events.length);
    // One row, at the recalled term's first-seen position, carrying the THIS-GAME meaning + provenance
    // (gameId ABSENT — the current game's exchange supersedes the carried one).
    expect(derived).toEqual([
      {
        term: term('alive'),
        meaning: 'this game: a living creature currently alive',
        establishedSeq: 2,
        origin: { agent: a2, questionId: qa },
      },
    ]);
  });

  it('challenge-a-remembered: the this-game meaning is current, the recalled origin stays attributable by scrub', () => {
    // The challenge-a-remembered-definition payoff (two-tier-reset-design §Challenging): a fresh game
    // seeds a recalled def; the human challenges the term; the proposer redefines it this game.
    const events = log([
      {
        type: 'definition_remembered',
        term: term('alive'),
        meaning: 'recalled: any living tissue',
        origin: { gameId: 1, agentId: a1, questionId: qa, seq: 9 },
      },
      {
        type: 'definition_given',
        agentId: a2,
        questionId: qa,
        term: term('alive'),
        meaning: 'this game: a living creature currently alive',
      },
    ]);
    // At the head: the CURRENT meaning is the this-game one (latest-wins), attributed to this game
    // (gameId absent) — no stale "recalled" badge over a superseded definition.
    expect(definitionsOf(events, events.length)).toEqual([
      {
        term: term('alive'),
        meaning: 'this game: a living creature currently alive',
        establishedSeq: 2,
        origin: { agent: a2, questionId: qa },
      },
    ]);
    // The recalled origin STAYS ATTRIBUTABLE: scrub back to before the redefinition and the recalled
    // definition — with its game-1 provenance intact — is what the panel shows (the trace is retained).
    expect(definitionsOf(events, 1)).toEqual([
      {
        term: term('alive'),
        meaning: 'recalled: any living tissue',
        establishedSeq: 1,
        origin: { agent: a1, questionId: qa, gameId: 1 },
      },
    ]);
  });
});
