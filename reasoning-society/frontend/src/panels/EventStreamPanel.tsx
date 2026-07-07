import type { ReactElement } from 'react';

import { downloadTextFile, toJson, toTranscript } from '../export';
import type { AgentId, ReasoningEvent } from '../model';
import { agentOf, describeEvent } from '../view/describeEvent';

interface EventStreamPanelProps {
  readonly events: readonly ReasoningEvent[];
  readonly playhead: number;
  readonly resolveAgent: (id: AgentId) => string;
  // The agent filter (build2-ui-design §4): when an agent is selected, rows not attributed to it dim
  // (highlight-over-hide) — no row is removed, and this scopes DISPLAY only, never the fold.
  readonly selectedAgent?: AgentId | null;
  // Binds an agent's scattered utterances by a SECONDARY channel — a left-gutter texture, not hue
  // (hue is reserved for the corner, §2). Omitted = no gutter.
  readonly gutterOf?: (id: AgentId) => number;
}

// The play-by-play (brief §4): the claims as posted, colour-coded by type, newest first. A pure view
// of the log prefix at the playhead — it derives its rows from `(events, playhead)`, the same source
// of truth the fold reads, and holds no state. The header carries a Download log affordance (Transcript
// / JSON), which reads the FULL current-game log (not the playhead prefix); the download itself is an
// event handler — the panel stays a pure reader, the effect lives in the click (ts-react).
export function EventStreamPanel({
  events,
  playhead,
  resolveAgent,
  selectedAgent,
  gutterOf,
}: EventStreamPanelProps): ReactElement {
  const visible = events.filter((event) => event.seq <= playhead);
  const ordered = [...visible].reverse();
  const selected = selectedAgent ?? null;
  const canDownload = events.length > 0;

  const handleDownloadTranscript = (): void => {
    downloadTextFile(
      toTranscript(events, resolveAgent, new Date()),
      'reasoning-society-log.md',
      'text/markdown',
    );
  };

  const handleDownloadJson = (): void => {
    downloadTextFile(
      toJson(events),
      'reasoning-society-log.json',
      'application/json',
    );
  };

  return (
    <section className="panel panel--stream" aria-label="Event stream">
      <header className="panel__head">
        <h2 className="panel__title">Event log</h2>
        <div className="panel__head-right">
          <p className="panel__sub">
            {visible.length} / {events.length}
          </p>
          <div className="log-download" aria-label="Download log">
            <span className="log-download__label">Download log</span>
            <button
              type="button"
              className="log-download__btn"
              onClick={handleDownloadTranscript}
              disabled={!canDownload}
              aria-label="Download log as transcript"
            >
              Transcript
            </button>
            <button
              type="button"
              className="log-download__btn"
              onClick={handleDownloadJson}
              disabled={!canDownload}
              aria-label="Download log as JSON"
            >
              JSON
            </button>
          </div>
        </div>
      </header>

      {ordered.length === 0 ? (
        <p className="panel__empty">The log is at the start.</p>
      ) : (
        <ol className="event-list" aria-label="Posted events, newest first">
          {ordered.map((event) => {
            const line = describeEvent(event, resolveAgent);
            const agent = agentOf(event);
            const dimmed = selected !== null && agent !== selected;
            const gutter =
              agent !== undefined && gutterOf !== undefined
                ? gutterOf(agent)
                : undefined;
            const rowClass = `event event--${event.type}${dimmed ? ' is-dimmed' : ''}`;
            return (
              <li key={event.seq} className={rowClass} data-gutter={gutter}>
                <span className="event__seq">{event.seq}</span>
                <span className="event__actor">{line.actor}</span>
                <span className="event__verb">{line.verb}</span>
                <span className="event__detail">{line.detail}</span>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
