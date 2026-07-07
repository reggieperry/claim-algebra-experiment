import type { ReactElement } from 'react';

import type { AgentId, ReasoningEvent } from '../model';

interface EventStreamPanelProps {
  readonly events: readonly ReasoningEvent[];
  readonly playhead: number;
  readonly resolveAgent: (id: AgentId) => string;
}

// The play-by-play (brief §4): the claims as posted, colour-coded by type, newest first. A pure view
// of the log prefix at the playhead — it derives its rows from `(events, playhead)`, the same source
// of truth the fold reads, and holds no state.
export function EventStreamPanel({
  events,
  playhead,
  resolveAgent,
}: EventStreamPanelProps): ReactElement {
  const visible = events.filter((event) => event.seq <= playhead);
  const ordered = [...visible].reverse();

  return (
    <section className="panel panel--stream" aria-label="Event stream">
      <header className="panel__head">
        <h2 className="panel__title">Event stream</h2>
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
            return (
              <li key={event.seq} className={`event event--${event.type}`}>
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
