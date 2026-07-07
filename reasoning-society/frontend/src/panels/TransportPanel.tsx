import type { ChangeEvent, ReactElement } from 'react';

import type { ReasoningEvent } from '../model';

// The speed multipliers the transport offers. Derived-from-`as const` so the option list and the
// values never drift (ts-types).
const SPEEDS = [0.5, 1, 2, 4] as const;

interface TransportPanelProps {
  readonly playhead: number;
  readonly total: number;
  readonly playing: boolean;
  readonly speed: number;
  readonly atHead: boolean;
  readonly onSeek: (playhead: number) => void;
  readonly onTogglePlay: () => void;
  readonly onStep: () => void;
  readonly onStepBack: () => void;
  readonly onSpeed: (speed: number) => void;
  // The timeline histogram, stacked by epistemic corner (build2-ui-design §4): one tick per event,
  // coloured by the corner it moves, click to seek. Omitted = no histogram (the control still works).
  readonly events?: readonly ReasoningEvent[];
}

// The DVR (brief §4): scrub, play/pause, step one event, speed, playhead readout. It holds NO belief
// — it only reports control gestures up to the App, which owns the single playhead. Replay is just
// re-folding at the playhead the scrubber sets; LIVE is the playhead pinned to the head.
export function TransportPanel({
  playhead,
  total,
  playing,
  speed,
  atHead,
  onSeek,
  onTogglePlay,
  onStep,
  onStepBack,
  onSpeed,
  events,
}: TransportPanelProps): ReactElement {
  const onScrub = (event: ChangeEvent<HTMLInputElement>): void => {
    onSeek(Number(event.currentTarget.value));
  };
  const onSpeedChange = (event: ChangeEvent<HTMLSelectElement>): void => {
    onSpeed(Number(event.currentTarget.value));
  };

  return (
    <section className="panel panel--transport" aria-label="Transport">
      <div className="transport__controls">
        <button
          type="button"
          className="transport__btn"
          onClick={onStepBack}
          disabled={playhead <= 0}
        >
          ⏮ prev
        </button>
        <button
          type="button"
          className="transport__btn transport__btn--play"
          onClick={onTogglePlay}
          aria-pressed={playing}
        >
          {playing ? '❚❚ pause' : '► play'}
        </button>
        <button
          type="button"
          className="transport__btn"
          onClick={onStep}
          disabled={atHead}
        >
          next ⏭
        </button>

        <label className="transport__speed-label" htmlFor="transport-speed">
          Speed
        </label>
        <select
          id="transport-speed"
          className="transport__speed"
          value={speed}
          onChange={onSpeedChange}
        >
          {SPEEDS.map((option) => (
            <option key={option} value={option}>
              {option}×
            </option>
          ))}
        </select>

        <span
          className={`transport__live${atHead ? ' transport__live--on' : ''}`}
        >
          {atHead ? '● LIVE' : '○ replay'}
        </span>
        <span className="transport__readout">
          {playhead} / {total}
        </span>
      </div>

      {events !== undefined ? (
        <div className="histo" aria-label="Event timeline by corner">
          {events.map((event) => {
            const future = event.seq > playhead;
            const here = event.seq === playhead;
            const barClass = [
              'histo__bar',
              `histo__bar--${histoTone(event)}`,
              future ? 'histo__bar--future' : '',
              here ? 'histo__bar--here' : '',
            ]
              .filter((part) => part.length > 0)
              .join(' ');
            return (
              <button
                key={event.seq}
                type="button"
                className={barClass}
                aria-label={`Seek to event ${event.seq.toString()}`}
                onClick={() => {
                  onSeek(event.seq);
                }}
              />
            );
          })}
        </div>
      ) : null}

      <label className="transport__scrub-label" htmlFor="transport-scrub">
        Timeline
      </label>
      <input
        id="transport-scrub"
        className="transport__scrub"
        type="range"
        min={0}
        max={total}
        step={1}
        value={playhead}
        onChange={onScrub}
      />
    </section>
  );
}

// The epistemic corner an event moves, mapped to the histogram's colour band — Conflict bursts and
// gate-abstains become coloured spikes (build2-ui-design §4). Exhaustive over the event union.
function histoTone(event: ReasoningEvent): string {
  switch (event.type) {
    case 'assert':
    case 'corroborate':
    case 'gate_sign':
      return 'resolved';
    case 'refute':
      return 'conflict';
    case 'strike':
      return 'superseded';
    case 'answer_given':
      return 'answer';
    case 'gate_abstain':
      return 'missing';
    // The clarification pair, a recalled definition, and the librarian's retire/resurrect markers are
    // vocabulary/lifecycle control flow — same band as the question events, belief-inert (never a live
    // conflict or superseded spike on the histogram).
    case 'question_proposed':
    case 'question_asked':
    case 'clarification_requested':
    case 'definition_given':
    case 'definition_remembered':
    case 'retired':
    case 'resurrected':
      return 'question';
  }
}
