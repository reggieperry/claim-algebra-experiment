import type { ReactElement } from 'react';

import {
  ANSWERS,
  type AgentId,
  type Answer,
  type CurrentQuestion,
} from '../model';

interface HumanActionPanelProps {
  readonly question: CurrentQuestion | undefined;
  readonly resolveAgent: (id: AgentId) => string;
  readonly onReveal: () => void;
}

const ANSWER_LABELS: Readonly<Record<Answer, string>> = {
  yes: 'Yes',
  no: 'No',
  unknown: 'Unknown',
};

// The human's action, made prominent (brief §4): the current question, which agent proposed it, and
// the yes / no / unknown control. In Build 1 the oracle is scripted, so the control ADVANCES the log
// to reveal the recorded answer rather than authoring one — the observer gets no vote (brief §1). A
// pure view of the fold's `currentQuestion`.
export function HumanActionPanel({
  question,
  resolveAgent,
  onReveal,
}: HumanActionPanelProps): ReactElement {
  return (
    <section className="panel panel--human" aria-label="Oracle">
      <header className="panel__head">
        <h2 className="panel__title">The oracle</h2>
        <p className="panel__sub">scripted · answer to advance</p>
      </header>

      {question === undefined ? (
        <p className="panel__empty">No question on the floor yet.</p>
      ) : (
        <div className="human">
          <p className="human__q">{question.content}</p>
          <p className="human__by">
            proposed by {resolveAgent(question.proposedBy)}
          </p>
          <div className="human__answers" role="group" aria-label="Answer">
            {ANSWERS.map((answer) => {
              const recorded = question.answer === answer;
              return (
                <button
                  key={answer}
                  type="button"
                  className={`human__ans human__ans--${answer}${recorded ? ' human__ans--on' : ''}`}
                  aria-pressed={recorded}
                  disabled={question.answer !== undefined}
                  onClick={onReveal}
                >
                  {ANSWER_LABELS[answer]}
                </button>
              );
            })}
          </div>
          {question.answer !== undefined ? (
            <p className="human__recorded">
              oracle answered: {ANSWER_LABELS[question.answer]}
            </p>
          ) : null}
        </div>
      )}
    </section>
  );
}
