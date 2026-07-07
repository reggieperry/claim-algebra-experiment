import { useState, type ReactElement } from 'react';

import type { LiveStatus } from '../live';
import { BELNAP_CORNERS, type Candidate, type GateDecision } from '../model';
import { cornerGlyph, cornerLabel } from '../view/corner';

interface HeaderBarProps {
  readonly gate: GateDecision;
  readonly candidates: readonly Candidate[];
  readonly playhead: number;
  readonly total: number;
  // The backend connection state — LIVE when the SSE stream is flowing, offline when it falls back to the
  // scripted demo. Distinct from the transport's own at-head/replay indicator (WHERE the playhead is).
  readonly connection: LiveStatus;
  // Restart the game: POST /start on the backend, then reconnect the stream. The App owns the flow; the
  // header just reports the gesture, whether it is in flight, and a transient failure.
  readonly onNewGame: () => void;
  readonly newGamePending: boolean;
  readonly newGameError: string | null;
  // Full Reset: POST /reset on the backend (clears learned definitions too), then reconnect. The App
  // owns the flow; the header takes the destructive gesture behind a brief inline confirm.
  readonly onFullReset: () => void;
  readonly fullResetPending: boolean;
  readonly fullResetError: string | null;
}

// The header carries visibility-of-system-status (build2-ui-design §1, Nielsen #1): the persistent corner
// legend (recognition-not-recall), the backend connection state, the last gate decision, and the `as-of
// e-N/M` fold caption. A pure reader of the fold at the playhead; it stores no fold state — the only
// local state below is the Full Reset confirm-armed toggle, an ephemeral interaction flag.
export function HeaderBar({
  gate,
  candidates,
  playhead,
  total,
  connection,
  onNewGame,
  newGamePending,
  newGameError,
  onFullReset,
  fullResetPending,
  fullResetError,
}: HeaderBarProps): ReactElement {
  return (
    <header className="observatory__head">
      <h1 className="observatory__title">Reasoning Society</h1>
      <span className="observatory__tag">twenty questions · observability</span>

      <ul className="legend" aria-label="Corner legend">
        {BELNAP_CORNERS.map((corner) => (
          <li key={corner} className={`legend__item legend__item--${corner}`}>
            <span className="legend__glyph" aria-hidden="true">
              {cornerGlyph(corner)}
            </span>
            {cornerLabel(corner)}
          </li>
        ))}
      </ul>

      <span className="observatory__spacer" />

      <button
        type="button"
        className="header-newgame"
        onClick={onNewGame}
        disabled={newGamePending}
        aria-busy={newGamePending}
        aria-label="New game"
      >
        {newGamePending ? '↻ starting…' : '↻ New game'}
      </button>
      {newGameError !== null ? (
        <span className="header-newgame-error" role="alert">
          {newGameError}
        </span>
      ) : null}

      <FullResetControl
        offline={connection === 'disconnected'}
        onFullReset={onFullReset}
        pending={fullResetPending}
        error={fullResetError}
      />

      <span
        className={`header-conn header-conn--${connection}`}
        aria-label={`Backend ${connectionLabel(connection)}`}
      >
        {connectionText(connection)}
      </span>
      <span className="header-gate">gate: {gateSummary(gate, candidates)}</span>
      <span className="header-asof">
        as of e-{playhead} / {total}
      </span>
    </header>
  );
}

interface FullResetControlProps {
  readonly offline: boolean;
  readonly onFullReset: () => void;
  readonly pending: boolean;
  readonly error: string | null;
}

// Full Reset discards learned definitions too (POST /reset), so — unlike the instant New Game — it takes
// a brief inline confirm: the first click ARMS it (revealing Confirm / Cancel), only the second commits.
// A cancelled confirm posts nothing. Offline (scripted demo) has no backend to reset, so it hides. The
// `armed` toggle is this control's only state — an ephemeral interaction flag (like the challenge field's
// controlled input), not derived fold data (ts-react).
function FullResetControl({
  offline,
  onFullReset,
  pending,
  error,
}: FullResetControlProps): ReactElement | null {
  const [armed, setArmed] = useState(false);

  if (offline) {
    return null;
  }

  const errorNode =
    error !== null ? (
      <span className="header-newgame-error" role="alert">
        {error}
      </span>
    ) : null;

  if (armed) {
    return (
      <span className="header-fullreset">
        <button
          type="button"
          className="header-fullreset__confirm"
          onClick={() => {
            setArmed(false);
            onFullReset();
          }}
          disabled={pending}
          aria-busy={pending}
        >
          {pending ? 'resetting…' : 'Confirm reset'}
        </button>
        <button
          type="button"
          className="header-fullreset__cancel"
          onClick={() => {
            setArmed(false);
          }}
          disabled={pending}
        >
          Cancel
        </button>
        {errorNode}
      </span>
    );
  }

  return (
    <span className="header-fullreset">
      <button
        type="button"
        className="header-fullreset__arm"
        onClick={() => {
          setArmed(true);
        }}
        disabled={pending}
        aria-label="Full reset"
        title="also clears learned definitions"
      >
        ⟲ Full reset
      </button>
      {errorNode}
    </span>
  );
}

// The connection indicator's visible text — the task's `● LIVE` / `demo (offline)`, plus a connecting tick.
function connectionText(status: LiveStatus): string {
  switch (status) {
    case 'live':
      return '● LIVE';
    case 'connecting':
      return '◌ connecting…';
    case 'disconnected':
      return 'demo (offline)';
  }
}

function connectionLabel(status: LiveStatus): string {
  switch (status) {
    case 'live':
      return 'live';
    case 'connecting':
      return 'connecting';
    case 'disconnected':
      return 'offline (scripted demo)';
  }
}

function gateSummary(
  gate: GateDecision,
  candidates: readonly Candidate[],
): string {
  switch (gate.kind) {
    case 'watching':
      return 'watching';
    case 'abstained':
      return '∅ abstained';
    case 'signed': {
      const signed = candidates.find(
        (candidate) => candidate.id === gate.candidateId,
      );
      return `✓ signed ${signed?.content ?? gate.candidateId}`;
    }
  }
}
