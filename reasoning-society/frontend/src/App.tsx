import { useEffect, useState, type ReactElement } from 'react';

import './App.css';
import { answerSeqFor, fold } from './fold';
import { agentName, MOCK_EVENTS } from './mock';
import {
  BeliefStatePanel,
  EventStreamPanel,
  HumanActionPanel,
  TransportPanel,
} from './panels';

// The base replay cadence at 1× — one event roughly every this many ms; the speed control divides
// it. Deliberately unhurried so a human can watch the society reason (brief §8).
const BASE_INTERVAL_MS = 900;

function clamp(value: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, value));
}

// The observatory root. It owns the single source of truth for what is on screen — the `playhead`
// into the one event log — plus the transport's own controls (playing, speed). Every panel is a
// pure reader of `fold(MOCK_EVENTS, playhead)`; nothing below holds a mirror of the belief state
// (brief §1). Live mode is a special case of replay: the playhead pinned to the head.
export function App(): ReactElement {
  const total = MOCK_EVENTS.length;
  const [playhead, setPlayhead] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);

  const belief = fold(MOCK_EVENTS, playhead);
  const atHead = playhead >= total;

  // The only effect: synchronize the playhead with a wall-clock timer while playing. When it reaches
  // the head the interval tears down (LIVE, waiting for events that never come in Build 1); scrub
  // back and it resumes. This is the sole place the app touches an external system (the clock).
  useEffect(() => {
    if (!playing || atHead) {
      return undefined;
    }
    const id = window.setInterval(() => {
      setPlayhead((current) => Math.min(current + 1, total));
    }, BASE_INTERVAL_MS / speed);
    return () => {
      window.clearInterval(id);
    };
  }, [playing, atHead, speed, total]);

  const handleSeek = (next: number): void => {
    setPlaying(false);
    setPlayhead(clamp(next, 0, total));
  };

  const handleTogglePlay = (): void => {
    if (playing) {
      setPlaying(false);
      return;
    }
    if (atHead) {
      setPlayhead(0); // replay from the top when starting at the head
    }
    setPlaying(true);
  };

  const handleStep = (): void => {
    setPlaying(false);
    setPlayhead((current) => Math.min(current + 1, total));
  };

  const handleStepBack = (): void => {
    setPlaying(false);
    setPlayhead((current) => Math.max(current - 1, 0));
  };

  // Advance the log to reveal the scripted oracle answer for the open question (brief §4: the Build 1
  // control advances the log, it does not author the answer).
  const handleReveal = (): void => {
    const question = belief.currentQuestion;
    if (question === undefined || question.answer !== undefined) {
      return;
    }
    const seq = answerSeqFor(MOCK_EVENTS, question.questionId);
    if (seq === undefined) {
      return;
    }
    setPlaying(false);
    setPlayhead(seq);
  };

  return (
    <div className="observatory">
      <header className="observatory__head">
        <h1 className="observatory__title">Reasoning Society</h1>
        <span className="observatory__tag">
          twenty questions · observability
        </span>
        <span className="observatory__spacer" />
        <span className="observatory__hint">
          a society reasoning toward a guess — scrub, step, or play
        </span>
      </header>

      <div className="observatory__human">
        <HumanActionPanel
          question={belief.currentQuestion}
          resolveAgent={agentName}
          onReveal={handleReveal}
        />
      </div>

      <main className="observatory__belief">
        <BeliefStatePanel state={belief} />
      </main>

      <aside className="observatory__stream">
        <EventStreamPanel
          events={MOCK_EVENTS}
          playhead={playhead}
          resolveAgent={agentName}
        />
      </aside>

      <footer className="observatory__transport">
        <TransportPanel
          playhead={playhead}
          total={total}
          playing={playing}
          speed={speed}
          atHead={atHead}
          onSeek={handleSeek}
          onTogglePlay={handleTogglePlay}
          onStep={handleStep}
          onStepBack={handleStepBack}
          onSpeed={setSpeed}
        />
      </footer>
    </div>
  );
}
