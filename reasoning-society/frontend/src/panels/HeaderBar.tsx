import type { ReactElement } from 'react';

import { BELNAP_CORNERS, type Candidate, type GateDecision } from '../model';
import { cornerGlyph, cornerLabel } from '../view/corner';

interface HeaderBarProps {
  readonly gate: GateDecision;
  readonly candidates: readonly Candidate[];
  readonly playhead: number;
  readonly total: number;
  readonly atHead: boolean;
}

// The header carries visibility-of-system-status (build2-ui-design §1, Nielsen #1): the persistent
// corner legend (recognition-not-recall), LIVE-vs-REPLAY, the last gate decision, and the `as-of
// e-N/M` fold caption. A pure reader of the fold at the playhead; it holds nothing.
export function HeaderBar({
  gate,
  candidates,
  playhead,
  total,
  atHead,
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

      <span
        className={`header-live${atHead ? ' header-live--on' : ''}`}
        aria-label={atHead ? 'Live, pinned to head' : 'Replay'}
      >
        {atHead ? '● LIVE' : '○ replay'}
      </span>
      <span className="header-gate">gate: {gateSummary(gate, candidates)}</span>
      <span className="header-asof">
        as of e-{playhead} / {total}
      </span>
    </header>
  );
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
