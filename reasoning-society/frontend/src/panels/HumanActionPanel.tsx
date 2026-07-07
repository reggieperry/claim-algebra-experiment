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
  // The human's verdict on the current question. In LIVE mode the App POSTs it to the backend oracle; in
  // offline/demo mode the App advances the scripted log to reveal the recorded answer. The panel does not
  // know which — it just reports the gesture (a pure view of the fold's `currentQuestion`).
  readonly onAnswer: (answer: Answer) => void;
  // Live: the human IS the oracle. Offline: the oracle is scripted and the buttons advance the replay.
  readonly live: boolean;
  // A transient send error surfaced beside the control (null when the last send was clean).
  readonly error: string | null;
}

const ANSWER_LABELS: Readonly<Record<Answer, string>> = {
  yes: 'Yes',
  no: 'No',
  unknown: 'Unknown',
};

// The human's action, made prominent (brief §4): the current question, which agent proposed it, and the
// yes / no / unknown control. In LIVE mode a click sends the oracle's real answer over `POST /answer`; in
// offline mode it advances the scripted log to the recorded answer (the observer gets no vote there).
export function HumanActionPanel({
  question,
  resolveAgent,
  onAnswer,
  live,
  error,
}: HumanActionPanelProps): ReactElement {
  return (
    <section className="panel panel--human" aria-label="Oracle">
      <header className="panel__head">
        <h2 className="panel__title">The oracle</h2>
        <p className="panel__sub">
          {live
            ? 'you are the oracle · answer to send'
            : 'scripted · answer to advance'}
        </p>
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
                  onClick={() => {
                    onAnswer(answer);
                  }}
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
          {error !== null ? (
            <p className="human__error" role="alert">
              {error}
            </p>
          ) : null}
        </div>
      )}
    </section>
  );
}
