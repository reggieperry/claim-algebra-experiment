import type { ReactElement } from 'react';

import type { AgentId, ReasoningEvent } from '../model';

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
// of truth the fold reads, and holds no state.
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

  return (
    <section className="panel panel--stream" aria-label="Event stream">
      <header className="panel__head">
        <h2 className="panel__title">Event log</h2>
        <p className="panel__sub">
          {visible.length} / {events.length}
        </p>
      </header>

      {ordered.length === 0 ? (
        <p className="panel__empty">The log is at the start.</p>
      ) : (
        <ol className="event-list" aria-label="Posted events, newest first">
          {ordered.map((event) => {
            const line = describe(event, resolveAgent);
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

// The agent an event is attributed to, or `undefined` for the oracle's answer and the gate's
// decisions. Exhaustive over the event union.
function agentOf(event: ReasoningEvent): AgentId | undefined {
  switch (event.type) {
    case 'assert':
    case 'corroborate':
    case 'refute':
    case 'strike':
    case 'question_proposed':
    case 'question_asked':
      return event.agentId;
    case 'answer_given':
    case 'gate_abstain':
    case 'gate_sign':
      return undefined;
  }
}

interface EventLine {
  readonly actor: string;
  readonly verb: string;
  readonly detail: string;
}

function describe(
  event: ReasoningEvent,
  resolveAgent: (id: AgentId) => string,
): EventLine {
  switch (event.type) {
    case 'assert':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'asserts',
        detail: event.content,
      };
    case 'corroborate':
      return {
        actor: resolveAgent(event.agentId),
        verb: `corroborates ${event.candidateId}`,
        detail: event.note,
      };
    case 'refute':
      return {
        actor: resolveAgent(event.agentId),
        verb: `refutes ${event.candidateId}`,
        detail: event.note,
      };
    case 'strike':
      return {
        actor: resolveAgent(event.agentId),
        verb: `strikes ${event.candidateId}`,
        detail: event.note,
      };
    case 'question_proposed':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'proposes',
        detail: event.content,
      };
    case 'question_asked':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'asks',
        detail: event.content,
      };
    case 'answer_given':
      return {
        actor: 'Oracle',
        verb: 'answers',
        detail: event.answer.toUpperCase(),
      };
    case 'gate_abstain':
      return { actor: 'Gate', verb: 'abstains', detail: event.reason };
    case 'gate_sign':
      return { actor: 'Gate', verb: 'signs', detail: event.candidateId };
    default:
      return assertNever(event);
  }
}

function assertNever(x: never): never {
  throw new Error(`unhandled event: ${JSON.stringify(x)}`);
}
