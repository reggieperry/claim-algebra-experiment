import { describe, expect, it } from 'vitest';

import {
  agentId,
  candidateId,
  questionId,
  term,
  type ReasoningEvent,
} from '../model';
import { decodeEvent } from './decodeEvent';

// The golden frames — byte-for-byte the wire contract in the task brief and in
// `backend/.../Wire.scala`'s scaladoc, the backend's own tested output. Decoding each JSON STRING (as it
// arrives off the SSE wire) and asserting the resulting typed event pins the decoder to the backend: if
// either side changes a field name or shape, this round-trip breaks.
const GOLDEN: readonly {
  readonly name: string;
  readonly json: string;
  readonly want: ReasoningEvent;
}[] = [
  {
    name: 'assert',
    json: '{"seq":1,"timestamp":2,"type":"assert","agentId":"a1","candidateId":"dog","content":"hi"}',
    want: {
      seq: 1,
      timestamp: 2,
      type: 'assert',
      agentId: agentId('a1'),
      candidateId: candidateId('dog'),
      content: 'hi',
    },
  },
  {
    name: 'corroborate',
    json: '{"seq":3,"timestamp":4,"type":"corroborate","agentId":"a2","candidateId":"dog","note":"seconded"}',
    want: {
      seq: 3,
      timestamp: 4,
      type: 'corroborate',
      agentId: agentId('a2'),
      candidateId: candidateId('dog'),
      note: 'seconded',
    },
  },
  {
    name: 'refute',
    json: '{"seq":5,"timestamp":6,"type":"refute","agentId":"a3","candidateId":"dog","note":"no"}',
    want: {
      seq: 5,
      timestamp: 6,
      type: 'refute',
      agentId: agentId('a3'),
      candidateId: candidateId('dog'),
      note: 'no',
    },
  },
  {
    name: 'strike',
    json: '{"seq":7,"timestamp":8,"type":"strike","agentId":"a1","candidateId":"dog","note":"struck"}',
    want: {
      seq: 7,
      timestamp: 8,
      type: 'strike',
      agentId: agentId('a1'),
      candidateId: candidateId('dog'),
      note: 'struck',
    },
  },
  {
    name: 'question_proposed',
    json: '{"seq":9,"timestamp":10,"type":"question_proposed","agentId":"a2","questionId":"q1","content":"Is it an animal?"}',
    want: {
      seq: 9,
      timestamp: 10,
      type: 'question_proposed',
      agentId: agentId('a2'),
      questionId: questionId('q1'),
      content: 'Is it an animal?',
    },
  },
  {
    name: 'question_asked',
    json: '{"seq":11,"timestamp":12,"type":"question_asked","agentId":"a2","questionId":"q1","content":"Is it an animal?"}',
    want: {
      seq: 11,
      timestamp: 12,
      type: 'question_asked',
      agentId: agentId('a2'),
      questionId: questionId('q1'),
      content: 'Is it an animal?',
    },
  },
  {
    name: 'answer_given',
    json: '{"seq":13,"timestamp":14,"type":"answer_given","questionId":"q1","answer":"yes"}',
    want: {
      seq: 13,
      timestamp: 14,
      type: 'answer_given',
      questionId: questionId('q1'),
      answer: 'yes',
    },
  },
  {
    name: 'clarification_requested',
    json: '{"seq":19,"timestamp":20,"type":"clarification_requested","questionId":"q1","term":"alive"}',
    want: {
      seq: 19,
      timestamp: 20,
      type: 'clarification_requested',
      questionId: questionId('q1'),
      term: term('alive'),
    },
  },
  {
    name: 'definition_given',
    json: '{"seq":21,"timestamp":22,"type":"definition_given","agentId":"a2","questionId":"q1","term":"alive","meaning":"a living creature currently alive"}',
    want: {
      seq: 21,
      timestamp: 22,
      type: 'definition_given',
      agentId: agentId('a2'),
      questionId: questionId('q1'),
      term: term('alive'),
      meaning: 'a living creature currently alive',
    },
  },
  {
    name: 'definition_remembered (nested origin, stamped gameId)',
    json: '{"seq":1,"timestamp":2,"type":"definition_remembered","term":"alive","meaning":"a living creature currently alive","origin":{"gameId":1,"agentId":"a2","questionId":"q1","seq":21}}',
    want: {
      seq: 1,
      timestamp: 2,
      type: 'definition_remembered',
      term: term('alive'),
      meaning: 'a living creature currently alive',
      origin: {
        gameId: 1,
        agentId: agentId('a2'),
        questionId: questionId('q1'),
        seq: 21,
      },
    },
  },
  {
    name: 'answer_given with governing (a clarified answer)',
    json: '{"seq":23,"timestamp":24,"type":"answer_given","questionId":"q1","answer":"no","governing":["alive"]}',
    want: {
      seq: 23,
      timestamp: 24,
      type: 'answer_given',
      questionId: questionId('q1'),
      answer: 'no',
      governing: [term('alive')],
    },
  },
  {
    name: 'gate_abstain',
    json: '{"seq":15,"timestamp":16,"type":"gate_abstain","reason":"watching"}',
    want: { seq: 15, timestamp: 16, type: 'gate_abstain', reason: 'watching' },
  },
  {
    name: 'gate_sign',
    json: '{"seq":17,"timestamp":18,"type":"gate_sign","candidateId":"dog"}',
    want: {
      seq: 17,
      timestamp: 18,
      type: 'gate_sign',
      candidateId: candidateId('dog'),
    },
  },
];

describe('decodeEvent', () => {
  it.each(GOLDEN)(
    'decodes the golden $name frame to its typed event',
    ({ json, want }) => {
      const decoded = decodeEvent(JSON.parse(json));
      expect(decoded).toEqual(want);
    },
  );

  it('accepts every yes / no / unknown oracle token', () => {
    for (const answer of ['yes', 'no', 'unknown'] as const) {
      const decoded = decodeEvent({
        seq: 1,
        timestamp: 1,
        type: 'answer_given',
        questionId: 'q1',
        answer,
      });
      expect(decoded).toEqual({
        seq: 1,
        timestamp: 1,
        type: 'answer_given',
        questionId: questionId('q1'),
        answer,
      });
    }
  });

  const malformed: readonly {
    readonly name: string;
    readonly input: unknown;
  }[] = [
    { name: 'null', input: null },
    { name: 'a bare string', input: 'assert' },
    { name: 'a number', input: 42 },
    { name: 'an object missing type', input: { seq: 1, timestamp: 2 } },
    {
      name: 'an unknown discriminator',
      input: { seq: 1, timestamp: 2, type: 'gate_veto' },
    },
    {
      name: 'a non-numeric seq',
      input: { seq: '1', timestamp: 2, type: 'gate_sign', candidateId: 'dog' },
    },
    {
      name: 'a non-finite seq',
      input: {
        seq: Number.NaN,
        timestamp: 2,
        type: 'gate_sign',
        candidateId: 'dog',
      },
    },
    {
      name: 'an assert missing content',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'assert',
        agentId: 'a1',
        candidateId: 'dog',
      },
    },
    {
      name: 'an assert with a blank id',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'assert',
        agentId: '  ',
        candidateId: 'dog',
        content: 'x',
      },
    },
    {
      name: 'an answer outside the closed set',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'answer_given',
        questionId: 'q1',
        answer: 'maybe',
      },
    },
    {
      name: 'a gate_sign missing candidateId',
      input: { seq: 1, timestamp: 2, type: 'gate_sign' },
    },
    {
      name: 'a clarification_requested with a blank term',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'clarification_requested',
        questionId: 'q1',
        term: '  ',
      },
    },
    {
      name: 'a clarification_requested missing term',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'clarification_requested',
        questionId: 'q1',
      },
    },
    {
      name: 'a definition_given missing meaning',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_given',
        agentId: 'a2',
        questionId: 'q1',
        term: 'alive',
      },
    },
    {
      name: 'a definition_given missing agentId',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_given',
        questionId: 'q1',
        term: 'alive',
        meaning: 'a living creature',
      },
    },
    {
      name: 'an answer_given whose governing is not an array',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'answer_given',
        questionId: 'q1',
        answer: 'no',
        governing: 'alive',
      },
    },
    {
      name: 'an answer_given whose governing holds a blank term',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'answer_given',
        questionId: 'q1',
        answer: 'no',
        governing: ['alive', '  '],
      },
    },
    {
      name: 'an answer_given whose governing holds a non-string',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'answer_given',
        questionId: 'q1',
        answer: 'no',
        governing: [7],
      },
    },
    {
      name: 'a definition_remembered missing term',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered missing meaning',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        origin: { agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered missing origin',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
      },
    },
    {
      name: 'a definition_remembered whose origin is not an object',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: 'a2',
      },
    },
    {
      name: 'a definition_remembered whose origin misses agentId',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered whose origin misses seq',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1' },
      },
    },
    {
      name: 'a definition_remembered whose origin gameId is not a number',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { gameId: '1', agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered whose origin seq is non-finite',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1', seq: Number.NaN },
      },
    },
    {
      name: 'a definition_remembered whose origin seq is zero (≤ 0)',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1', seq: 0 },
      },
    },
    {
      name: 'a definition_remembered whose origin seq is negative',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1', seq: -3 },
      },
    },
    {
      name: 'a definition_remembered whose origin seq is a non-integer',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { agentId: 'a2', questionId: 'q1', seq: 1.5 },
      },
    },
    {
      name: 'a definition_remembered whose origin gameId is zero (≤ 0)',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { gameId: 0, agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered whose origin gameId is negative',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { gameId: -1, agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
    {
      name: 'a definition_remembered whose origin gameId is a non-integer',
      input: {
        seq: 1,
        timestamp: 2,
        type: 'definition_remembered',
        term: 'alive',
        meaning: 'm',
        origin: { gameId: 2.5, agentId: 'a2', questionId: 'q1', seq: 21 },
      },
    },
  ];

  it.each(malformed)('rejects $name as null', ({ input }) => {
    expect(decodeEvent(input)).toBeNull();
  });

  it('rejects the string that is not JSON at all (parsed to a primitive)', () => {
    expect(decodeEvent(JSON.parse('"just a string"'))).toBeNull();
  });

  it('OMITS governing on a non-clarified answer (present ≠ absent-and-empty)', () => {
    const decoded = decodeEvent(
      JSON.parse(
        '{"seq":13,"timestamp":14,"type":"answer_given","questionId":"q1","answer":"yes"}',
      ),
    );
    // The field is absent, not present-and-undefined — the pre-clarification shape is byte-identical.
    expect(decoded).not.toBeNull();
    expect(decoded && 'governing' in decoded).toBe(false);
  });

  it('decodes a recalled definition whose origin OMITS gameId (not yet stamped)', () => {
    const decoded = decodeEvent(
      JSON.parse(
        '{"seq":1,"timestamp":2,"type":"definition_remembered","term":"alive","meaning":"m","origin":{"agentId":"a2","questionId":"q1","seq":21}}',
      ),
    );
    // gameId absent, not present-and-undefined — mirrors the backend omitting it for a None origin.
    expect(decoded).toEqual({
      seq: 1,
      timestamp: 2,
      type: 'definition_remembered',
      term: term('alive'),
      meaning: 'm',
      origin: {
        agentId: agentId('a2'),
        questionId: questionId('q1'),
        seq: 21,
      },
    });
  });

  it('accepts an empty governing array (a vacuously valid clarified answer)', () => {
    const decoded = decodeEvent({
      seq: 23,
      timestamp: 24,
      type: 'answer_given',
      questionId: 'q1',
      answer: 'no',
      governing: [],
    });
    expect(decoded).toEqual({
      seq: 23,
      timestamp: 24,
      type: 'answer_given',
      questionId: questionId('q1'),
      answer: 'no',
      governing: [],
    });
  });
});
