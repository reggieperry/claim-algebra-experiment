import type { ReactElement } from 'react';

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
}

// The header carries visibility-of-system-status (build2-ui-design §1, Nielsen #1): the persistent corner
// legend (recognition-not-recall), the backend connection state, the last gate decision, and the `as-of
// e-N/M` fold caption. A pure reader of the fold at the playhead; it holds nothing.
export function HeaderBar({
  gate,
  candidates,
  playhead,
  total,
  connection,
  onNewGame,
  newGamePending,
  newGameError,
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
