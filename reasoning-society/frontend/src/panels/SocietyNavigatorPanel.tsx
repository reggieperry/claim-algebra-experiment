import type { ReactElement } from 'react';

import type { AgentSummary, Society } from '../fold';
import type { AgentId } from '../model';
import { cornerGlyph } from '../view/corner';

interface SocietyNavigatorPanelProps {
  readonly society: Society;
  readonly selectedAgent: AgentId | null;
  readonly onSelectAgent: (id: AgentId | null) => void;
  readonly resolveAgent: (id: AgentId) => string;
  readonly resolveStance: (id: AgentId) => string;
  readonly gutterOf: (id: AgentId) => number;
}

// The society navigator (build2-ui-design §2): a dense k9s-style list, one row per agent — leading
// corner band, id/role, +/¬/⊘ counts, last-spoke — with the diversity scalar and the monoculture
// warning in the header. Click-to-filter is brushing-and-linking: selecting an agent scopes the other
// panels (the transport is left alone — WHO ⊥ WHEN). A PURE reader of the society projection; the
// selection it reports is App state, not its own.
export function SocietyNavigatorPanel({
  society,
  selectedAgent,
  onSelectAgent,
  resolveAgent,
  resolveStance,
  gutterOf,
}: SocietyNavigatorPanelProps): ReactElement {
  const scoped = selectedAgent !== null;

  return (
    <section className="panel panel--nav" aria-label="Society">
      <header className="panel__head">
        <h2 className="panel__title">Society</h2>
        <p className="panel__sub">
          <span className="nav-diversity">{society.diversity} live</span>
          {society.monoculture ? (
            <span className="nav-warn"> ⚠ monoculture</span>
          ) : null}
        </p>
      </header>

      {scoped ? (
        <button
          type="button"
          className="nav-chip"
          onClick={() => {
            onSelectAgent(null);
          }}
        >
          scoped to: {resolveAgent(selectedAgent)} ✕
        </button>
      ) : null}

      <ul className="nav-list">
        {society.agents.map((agent) => (
          <AgentRow
            key={agent.id}
            agent={agent}
            selected={agent.id === selectedAgent}
            dimmed={scoped && agent.id !== selectedAgent}
            gutter={gutterOf(agent.id)}
            name={resolveAgent(agent.id)}
            stance={resolveStance(agent.id)}
            onSelect={onSelectAgent}
          />
        ))}
      </ul>
    </section>
  );
}

interface AgentRowProps {
  readonly agent: AgentSummary;
  readonly selected: boolean;
  readonly dimmed: boolean;
  readonly gutter: number;
  readonly name: string;
  readonly stance: string;
  readonly onSelect: (id: AgentId | null) => void;
}

function AgentRow({
  agent,
  selected,
  dimmed,
  gutter,
  name,
  stance,
  onSelect,
}: AgentRowProps): ReactElement {
  const corner = agent.dominantCorner;
  const bandClass = `nav-row__band nav-row__band--${corner ?? 'silent'}`;
  const rowClass = [
    'nav-row',
    selected ? 'nav-row--selected' : '',
    dimmed ? 'is-dimmed' : '',
  ]
    .filter((part) => part.length > 0)
    .join(' ');

  return (
    <li>
      <button
        type="button"
        className={rowClass}
        aria-pressed={selected}
        data-gutter={gutter}
        onClick={() => {
          // Re-clicking the selected agent clears (click-empty semantics); the chip and Esc also clear.
          onSelect(selected ? null : agent.id);
        }}
      >
        <span className={bandClass} aria-hidden="true">
          {corner === undefined ? '·' : cornerGlyph(corner)}
        </span>
        <span className="nav-row__who">
          <span className="nav-row__name">{name}</span>
          <span className="nav-row__stance">{stance}</span>
        </span>
        <span className="nav-row__counts">
          <span className="nav-row__pro">+{agent.asserted}</span>
          <span className="nav-row__con">¬{agent.refuted}</span>
          <span className="nav-row__struck">⊘{agent.superseded}</span>
        </span>
        <span className="nav-row__last">
          {agent.lastSpokeSeq === undefined
            ? 'silent'
            : `@e-${agent.lastSpokeSeq.toString()}`}
        </span>
      </button>
    </li>
  );
}
