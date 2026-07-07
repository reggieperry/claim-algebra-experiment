import { useState, type ReactElement } from 'react';

import {
  ANSWERS,
  type AgentId,
  type Answer,
  type CurrentQuestion,
  type DefinitionClaim,
} from '../model';

interface HumanActionPanelProps {
  readonly question: CurrentQuestion | undefined;
  readonly resolveAgent: (id: AgentId) => string;
  // The human's verdict on the current question. In LIVE mode the App POSTs it to the backend oracle; in
  // offline/demo mode the App advances the scripted log to reveal the recorded answer. The panel does not
  // know which — it just reports the gesture (a pure view of the fold's `currentQuestion`).
  readonly onAnswer: (answer: Answer) => void;
  // The human's CHALLENGE of a term ("define '<term>'", clarification-feature §1), asked before
  // answering. Live only — the App POSTs it to `POST /challenge`; the offline demo has no scripted
  // clarification, so the challenge control is hidden there.
  readonly onChallenge: (term: string) => void;
  // Live: the human IS the oracle. Offline: the oracle is scripted and the buttons advance the replay.
  readonly live: boolean;
  // A transient answer-send error surfaced beside the control (null when the last send was clean).
  readonly error: string | null;
  // A transient challenge-send error (null when the last challenge was clean).
  readonly challengeError: string | null;
}

const ANSWER_LABELS: Readonly<Record<Answer, string>> = {
  yes: 'Yes',
  no: 'No',
  unknown: 'Unknown',
};

// The human's action, made prominent (brief §4): the current question, which agent proposed it, and the
// yes / no / unknown control — plus the CHALLENGE control and the ORDERING GATE (clarification-feature
// §5). When the human challenges a term, answering is HELD until the asking agent defines it; the
// definition then renders as a claim and answering re-opens. In LIVE mode a click sends the oracle's
// real answer over `POST /answer`; in offline mode it advances the scripted log to the recorded answer.
export function HumanActionPanel({
  question,
  resolveAgent,
  onAnswer,
  onChallenge,
  live,
  error,
  challengeError,
}: HumanActionPanelProps): ReactElement {
  return (
    <section className="panel panel--human" aria-label="Oracle">
      <header className="panel__head">
        <h2 className="panel__title">The oracle</h2>
        <p className="panel__sub">
          {live
            ? 'you are the oracle · answer or challenge'
            : 'scripted · answer to advance'}
        </p>
      </header>

      {question === undefined ? (
        <p className="panel__empty">No question on the floor yet.</p>
      ) : (
        <QuestionTurn
          question={question}
          resolveAgent={resolveAgent}
          onAnswer={onAnswer}
          onChallenge={onChallenge}
          live={live}
          error={error}
          challengeError={challengeError}
        />
      )}
    </section>
  );
}

interface QuestionTurnProps {
  readonly question: CurrentQuestion;
  readonly resolveAgent: (id: AgentId) => string;
  readonly onAnswer: (answer: Answer) => void;
  readonly onChallenge: (term: string) => void;
  readonly live: boolean;
  readonly error: string | null;
  readonly challengeError: string | null;
}

function QuestionTurn({
  question,
  resolveAgent,
  onAnswer,
  onChallenge,
  live,
  error,
  challengeError,
}: QuestionTurnProps): ReactElement {
  // The ordering gate (clarification-feature §3): answering is HELD while a challenge is open (the
  // asking agent has not defined the term yet) and once the question is already answered.
  const pending = question.pendingChallenge;
  const gated = question.answer !== undefined || pending !== undefined;
  // Challenge only when live, unanswered, and no challenge is already outstanding — after a definition
  // arrives the human may challenge again (offline has no scripted clarification, so it is hidden).
  const canChallenge =
    live && question.answer === undefined && pending === undefined;

  return (
    <div className="human">
      <p className="human__q">{question.content}</p>
      <p className="human__by">
        proposed by {resolveAgent(question.proposedBy)}
      </p>

      {question.definitions.length > 0 ? (
        <DefinitionClaims
          definitions={question.definitions}
          resolveAgent={resolveAgent}
        />
      ) : null}

      {pending !== undefined ? (
        <p className="human__waiting" role="status">
          waiting for {resolveAgent(question.proposedBy)}&rsquo;s definition of
          &ldquo;{pending.term}&rdquo;&hellip;
        </p>
      ) : null}

      <div className="human__answers" role="group" aria-label="Answer">
        {ANSWERS.map((answer) => {
          const recorded = question.answer === answer;
          return (
            <button
              key={answer}
              type="button"
              className={`human__ans human__ans--${answer}${recorded ? ' human__ans--on' : ''}`}
              aria-pressed={recorded}
              disabled={gated}
              onClick={() => {
                onAnswer(answer);
              }}
            >
              {ANSWER_LABELS[answer]}
            </button>
          );
        })}
      </div>

      {canChallenge ? <ChallengeControl onChallenge={onChallenge} /> : null}

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
      {challengeError !== null ? (
        <p className="human__error" role="alert">
          {challengeError}
        </p>
      ) : null}
    </div>
  );
}

interface DefinitionClaimsProps {
  readonly definitions: readonly DefinitionClaim[];
  readonly resolveAgent: (id: AgentId) => string;
}

// The definition rendered as a CLAIM (clarification-feature §4) — term, meaning, and its source agent
// (the proposer) as provenance — not ephemeral chat. The society now shares this vocabulary.
function DefinitionClaims({
  definitions,
  resolveAgent,
}: DefinitionClaimsProps): ReactElement {
  return (
    <ul className="human__defs" aria-label="Definitions given">
      {definitions.map((definition) => (
        <li
          key={`${definition.term}@${definition.establishedSeq.toString()}`}
          className="human__def"
        >
          <span className="human__def-term">{definition.term}</span>
          <span className="human__def-meaning">{definition.meaning}</span>
          <span className="human__def-by">
            by {resolveAgent(definition.agent)}
          </span>
        </li>
      ))}
    </ul>
  );
}

interface ChallengeControlProps {
  readonly onChallenge: (term: string) => void;
}

// The Challenge affordance (clarification-feature §1): the human types the term to challenge and
// submits "define '<term>'". A controlled input (ts-react: React state is the single source of truth
// for the field); a blank term is not sent. On submit the field clears, ready for another challenge.
function ChallengeControl({
  onChallenge,
}: ChallengeControlProps): ReactElement {
  const [termText, setTermText] = useState('');
  const trimmed = termText.trim();

  return (
    <form
      className="human__challenge"
      onSubmit={(submit) => {
        submit.preventDefault();
        if (trimmed.length === 0) {
          return;
        }
        onChallenge(trimmed);
        setTermText('');
      }}
    >
      <label className="human__challenge-label" htmlFor="challenge-term">
        Challenge a term
      </label>
      <input
        id="challenge-term"
        className="human__challenge-input"
        type="text"
        placeholder="term to define"
        value={termText}
        onChange={(change) => {
          setTermText(change.target.value);
        }}
      />
      <button
        type="submit"
        className="human__challenge-send"
        disabled={trimmed.length === 0}
      >
        Challenge
      </button>
    </form>
  );
}
