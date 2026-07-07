import { useEffect, useState, type ReactElement } from 'react';

import './App.css';
import {
  answerSeqFor,
  buildGraph,
  candidatesTouchedBy,
  fold,
  memoryOf,
  societyOf,
} from './fold';
import { AGENTS, agentName, MOCK_EVENTS } from './mock';
import type { AgentId } from './model';
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
// accordion's expand set. Every panel is a pure reader of `fold(MOCK_EVENTS, playhead)` and its
// sibling projections; nothing below mirrors the belief state (brief §1). Crucially the belief fold
// runs over the FULL log — the selected agent scopes only what the panels DISPLAY, never what is
// computed (the load-bearing safety rule). Live mode is replay with the playhead pinned to the head.
export function App(): ReactElement {
  const total = MOCK_EVENTS.length;
  const [playhead, setPlayhead] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [selectedAgent, setSelectedAgent] = useState<AgentId | null>(null);
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(
    new Set(['facts']),
  );

  // The single source of truth and its pure projections — recomputed every render, stored nowhere.
  const belief = fold(MOCK_EVENTS, playhead);
  const graph = buildGraph(MOCK_EVENTS, playhead);
  const society = societyOf(MOCK_EVENTS, playhead, belief, ROSTER, graph);
  const memory = memoryOf(MOCK_EVENTS, playhead, belief, graph);
  const atHead = playhead >= total;

  // The agent filter is a VIEW derived from the same graph — the set of claims the selected agent
  // touched, over which belief dims the rest. It never re-runs the fold.
  const scopedCandidates =
    selectedAgent === null ? null : candidatesTouchedBy(graph, selectedAgent);

  // The only wall-clock effect: advance the playhead while playing. At the head the interval tears
  // down (LIVE, waiting for events that never come in Build 1); scrub back and it resumes.
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
        atHead={atHead}
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
            onReveal={handleReveal}
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
            events={MOCK_EVENTS}
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
          events={MOCK_EVENTS}
        />
      </footer>
    </div>
  );
}
