import { describe, expect, it } from 'vitest';

import { agentId, questionId, type ReasoningEvent } from '../model';
import { answeredQuestions } from './fold';

const splitter = agentId('splitter');
const qa = questionId('qa');
const qb = questionId('qb');

// A game: qa asked → answered no, then qb asked → answered yes.
const events: readonly ReasoningEvent[] = [
  {
    seq: 1,
    timestamp: 1,
    type: 'question_asked',
    agentId: splitter,
    questionId: qa,
    content: 'Is it alive?',
  },
  { seq: 2, timestamp: 2, type: 'answer_given', questionId: qa, answer: 'no' },
  {
    seq: 3,
    timestamp: 3,
    type: 'question_asked',
    agentId: splitter,
    questionId: qb,
    content: 'Is it a fruit?',
  },
  { seq: 4, timestamp: 4, type: 'answer_given', questionId: qb, answer: 'yes' },
];

describe('answeredQuestions', () => {
  it('surfaces the human answers in order, each with its question text and the seq to rewind to', () => {
    expect(answeredQuestions(events, events.length)).toEqual([
      { seq: 2, content: 'Is it alive?', answer: 'no' },
      { seq: 4, content: 'Is it a fruit?', answer: 'yes' },
    ]);
  });

  it('respects the playhead — only answers in the prefix are surfaced (scrubbable)', () => {
    expect(answeredQuestions(events, 2)).toEqual([
      { seq: 2, content: 'Is it alive?', answer: 'no' },
    ]);
  });

  it('skips an answer whose question was never asked in the prefix (no re-askable boundary)', () => {
    const orphan: readonly ReasoningEvent[] = [
      {
        seq: 1,
        timestamp: 1,
        type: 'answer_given',
        questionId: qa,
        answer: 'no',
      },
    ];
    expect(answeredQuestions(orphan, 1)).toEqual([]);
  });
});
