import { describe, expect, it } from 'vitest';

import {
  agentId,
  candidateId,
  questionId,
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
  ];

  it.each(malformed)('rejects $name as null', ({ input }) => {
    expect(decodeEvent(input)).toBeNull();
  });

  it('rejects the string that is not JSON at all (parsed to a primitive)', () => {
    expect(decodeEvent(JSON.parse('"just a string"'))).toBeNull();
  });
});
