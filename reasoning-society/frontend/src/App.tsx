import type { ReactElement } from 'react';

import './App.css';

// Scaffold placeholder. The observability shell — event log + pure fold + the belief-state /
// event-stream / transport panels driven by a hand-written mock scenario — lands in Build 1
// (brief §10). This is an instrument, not a marketing page: dark, dense, data-first.
export function App(): ReactElement {
  return (
    <main className="observatory">
      <header className="observatory__head">
        <h1>Reasoning Society</h1>
        <span className="observatory__tag">observatory · scaffold</span>
      </header>
      <p className="observatory__note">
        Instrument shell not yet wired. Build 1 brings the event log, the pure
        fold, and the belief-state / event-stream / transport panels — a society
        visibly reasoning toward a guess, scrubbable from the first run.
      </p>
    </main>
  );
}
