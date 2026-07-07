import { useEffect, useRef, useState, type ReactElement } from 'react';

import './App.css';
import {
  answerSeqFor,
  buildGraph,
  candidatesTouchedBy,
  fold,
  memoryOf,
  societyOf,
} from './fold';
import { postAnswer, postStart, useLiveEvents } from './live';
import { AGENTS, agentName, MOCK_EVENTS } from './mock';
import type { AgentId, Answer } from './model';
import {
  BeliefStatePanel,
  EventStreamPanel,
  HeaderBar,
  HumanActionPanel,
  MemoryPanel,
  SocietyNavigatorPanel,
  TransportPanel,
} from './panels';

// The base replay cadence at 1× — one event roughly every this many ms; the speed control divides
// it. Deliberately unhurried so a human can watch the society reason (brief §8).
const BASE_INTERVAL_MS = 900;

// The society roster, in a stable order — the navigator lists every agent, silent ones included, in
// this order (fixed slots across scrubbing, build2-ui-design §5 DO).
const ROSTER: readonly AgentId[] = AGENTS.map((agent) => agent.id);

// Bind an agent's scattered contributions by a SECONDARY channel (a left-gutter texture), never hue
// (hue is reserved for the corner, §2). The gutter index is the agent's stable roster position.
function gutterOf(id: AgentId): number {
  const index = ROSTER.indexOf(id);
  return index < 0 ? 0 : index;
}

function resolveStance(id: AgentId): string {
  return AGENTS.find((agent) => agent.id === id)?.stance ?? '';
}

function clamp(value: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, value));
}

// The observatory root. It owns the single UI state — the `playhead` (WHEN) and the `selectedAgent`
// (WHO), the two global selectors (build2-ui-design §0), plus the transport controls and the memory
// accordion's expand set. The event log is now the LIVE stream from the backend (`useLiveEvents`); when
// that stream is down the App falls back to the scripted `MOCK_EVENTS` demo, so the UI never breaks with
// the backend off. Every panel is a pure reader of `fold(events, playhead)` and its sibling projections;
// nothing below mirrors the belief state (brief §1). Crucially the belief fold runs over the FULL log —
// the selected agent scopes only what the panels DISPLAY, never what is computed (the load-bearing safety
// rule). Live mode is the playhead pinned to the head of the growing log.
export function App(): ReactElement {
  const live = useLiveEvents();
  const offline = live.status === 'disconnected';
  // The single source of truth: the live log, or the scripted demo when the backend is unreachable.
  const events = offline ? MOCK_EVENTS : live.events;
  const total = events.length;

  const [playhead, setPlayhead] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [selectedAgent, setSelectedAgent] = useState<AgentId | null>(null);
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(
    new Set(['facts']),
  );
  // A transient error from the last answer POST — surfaced beside the oracle control, cleared on success.
  const [answerError, setAnswerError] = useState<string | null>(null);
  // The New game restart's in-flight flag (disables the button) and its transient failure. Derived UI
  // state only — the events array stays the single source of truth (ts-react: derive, don't store).
  const [newGamePending, setNewGamePending] = useState(false);
  const [newGameError, setNewGameError] = useState<string | null>(null);

  // The single source of truth and its pure projections — recomputed every render, stored nowhere.
  const belief = fold(events, playhead);
  const graph = buildGraph(events, playhead);
  const society = societyOf(events, playhead, belief, ROSTER, graph);
  const memory = memoryOf(events, playhead, belief, graph);
  const atHead = playhead >= total;

  // The agent filter is a VIEW derived from the same graph — the set of claims the selected agent
  // touched, over which belief dims the rest. It never re-runs the fold.
  const scopedCandidates =
    selectedAgent === null ? null : candidatesTouchedBy(graph, selectedAgent);

  // Live mode pins the playhead to the head of the growing log. As events stream in, follow the head so
  // long as the viewer was already there; if they scrubbed back to review, hold their position until they
  // return to the head. Offline (scripted demo) does not auto-advance — the transport drives it.
  const followedTotal = useRef(0);
  useEffect(() => {
    const previous = followedTotal.current;
    followedTotal.current = total;
    if (offline) {
      return;
    }
    setPlayhead((current) => (current >= previous ? total : current));
  }, [total, offline]);

  // The only wall-clock effect: advance the playhead while playing. At the head the interval tears
  // down (LIVE, pinned to head); scrub back and it resumes.
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

  // Esc clears the agent filter (build2-ui-design §2) — a keyboard sync with a symmetric teardown.
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        setSelectedAgent(null);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
    };
  }, []);

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

  // Offline (scripted demo): advance the log to reveal the recorded oracle answer for the open question
  // (brief §4: the control advances the log, it does not author the answer).
  const handleReveal = (): void => {
    const question = belief.currentQuestion;
    if (question === undefined || question.answer !== undefined) {
      return;
    }
    const seq = answerSeqFor(events, question.questionId);
    if (seq === undefined) {
      return;
    }
    setPlaying(false);
    setPlayhead(seq);
  };

  // The oracle's verdict. Live: POST it to the backend, which resolves the pending question and streams
  // the resulting events back. Offline: reveal the scripted answer instead (the observer gets no vote).
  const handleAnswer = (answer: Answer): void => {
    if (offline) {
      handleReveal();
      return;
    }
    const question = belief.currentQuestion;
    if (question === undefined || question.answer !== undefined) {
      return;
    }
    setAnswerError(null);
    void postAnswer(question.questionId, answer).catch(() => {
      setAnswerError('could not send your answer — please retry');
    });
  };

  // The New game gesture: ask the backend to restart (POST /start), and on success reconnect the SSE
  // stream so it catches up on the reset log — the fresh game only, from seq 1. The button spins while
  // in flight; a failure surfaces transiently and the operator can retry. A double-click is ignored
  // (the backend also serializes restarts, so a race cannot stack games).
  const handleNewGame = (): void => {
    if (newGamePending) {
      return;
    }
    setNewGameError(null);
    setNewGamePending(true);
    void postStart()
      .then(() => {
        live.reconnect();
      })
      .catch(() => {
        setNewGameError('could not start a new game — please retry');
      })
      .finally(() => {
        setNewGamePending(false);
      });
  };

  const handleToggleTier = (key: string): void => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  return (
    <div className="observatory">
      <HeaderBar
        gate={belief.gate}
        candidates={belief.candidates}
        playhead={playhead}
        total={total}
        connection={live.status}
        onNewGame={handleNewGame}
        newGamePending={newGamePending}
        newGameError={newGameError}
      />

      <aside className="observatory__nav">
        <SocietyNavigatorPanel
          society={society}
          selectedAgent={selectedAgent}
          onSelectAgent={setSelectedAgent}
          resolveAgent={agentName}
          resolveStance={resolveStance}
          gutterOf={gutterOf}
        />
      </aside>

      <main className="observatory__center">
        <div className="observatory__human">
          <HumanActionPanel
            question={belief.currentQuestion}
            resolveAgent={agentName}
            onAnswer={handleAnswer}
            live={!offline}
            error={answerError}
          />
        </div>

        <div className="observatory__belief">
          <BeliefStatePanel
            state={belief}
            scopedCandidates={scopedCandidates}
          />
        </div>

        <div className="observatory__stream">
          <EventStreamPanel
            events={events}
            playhead={playhead}
            resolveAgent={agentName}
            selectedAgent={selectedAgent}
            gutterOf={gutterOf}
          />
        </div>
      </main>

      <aside className="observatory__memory">
        <MemoryPanel
          memory={memory}
          selectedAgent={selectedAgent}
          expanded={expanded}
          onToggle={handleToggleTier}
          onSeek={handleSeek}
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
          events={events}
        />
      </footer>
    </div>
  );
}
