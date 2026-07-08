import { type ReactElement } from 'react';

import type { AnsweredQuestion } from '../fold';
import type { Answer } from '../model';

interface RewindPanelProps {
  // The human's answered questions up to the playhead, in order — the positional list to reconsider.
  readonly answers: readonly AnsweredQuestion[];
  // Whether the convergence monitor has flagged the search as stuck (belief.convergence !== undefined).
  // The panel is INERT until it fires — a rewind is offered only when the search is actually stuck.
  readonly flagged: boolean;
  // Live only: a rewind re-forks the backend game. Offline (the scripted demo) has no game to rewind.
  readonly live: boolean;
  // The human picks ONE answer to flip; the App POSTs its seq to /rewind and reconnects the stream.
  readonly onRewind: (seq: number) => void;
  // A transient rewind-send error surfaced in the panel (null when the last send was clean).
  readonly error: string | null;
}

const ANSWER_LABELS: Readonly<Record<Answer, string>> = {
  yes: 'Yes',
  no: 'No',
  unknown: 'Unknown',
};

// The recovery affordance (B2, recovery-and-endgame). When the convergence monitor flags the search as
// STUCK — no candidate consolidating, or a persistent glut — an early answer may have poisoned a whole
// region (the "apple bug": a mis-grounded "not a living organism" fences off the fruit region where the
// answer lived). The panel is deliberately NON-GENERATIVE: it never says WHICH answer is wrong (the
// librarian cannot judge that — detect, not diagnose), it surfaces the human's OWN recent answers and
// lets them, holding the ground truth, pick one to reconsider. A rewind truncates the log to before that
// answer and re-asks it. Inert until the flag fires, and only in live mode (a rewind re-forks the game).
export function RewindPanel({
  answers,
  flagged,
  live,
  onRewind,
  error,
}: RewindPanelProps): ReactElement | null {
  if (!flagged || !live || answers.length === 0) {
    return null;
  }
  return (
    <section
      className="panel panel--rewind"
      aria-label="Reconsider an earlier answer"
    >
      <header className="panel__head">
        <h2 className="panel__title">Reconsider an earlier answer</h2>
        <p className="panel__sub">
          the search is stuck — an early answer may have ruled out the truth.
          pick one to rethink; the game rewinds to before it and re-asks it.
        </p>
      </header>
      <ul className="rewind__list">
        {answers.map((answer) => (
          <li key={answer.seq} className="rewind__item">
            <span className="rewind__question">{answer.content}</span>
            <span className="rewind__answer">
              {ANSWER_LABELS[answer.answer]}
            </span>
            <button
              type="button"
              className="rewind__button"
              aria-label={`Rewind to before "${answer.content}"`}
              onClick={() => {
                onRewind(answer.seq);
              }}
            >
              Rewind to here
            </button>
          </li>
        ))}
      </ul>
      {error !== null && (
        <p className="rewind__error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
