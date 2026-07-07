import type { ChangeEvent, ReactElement } from 'react';

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
