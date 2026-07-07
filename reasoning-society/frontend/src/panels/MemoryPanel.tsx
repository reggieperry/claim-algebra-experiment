import type { ReactElement, ReactNode } from 'react';

import type { Fact, Memory, MethodCalibration, Relationship } from '../fold';
import type { AgentId } from '../model';
import { cornerGlyph } from '../view/corner';

// Low-n calibration greys out so a 1/1 does not masquerade as certainty (build2-ui-design §3).
const RELIABLE_SAMPLE = 3;

interface MemoryPanelProps {
  readonly memory: Memory;
  readonly selectedAgent: AgentId | null;
  readonly expanded: ReadonlySet<string>;
  readonly onToggle: (key: string) => void;
  readonly onSeek: (seq: number) => void;
  readonly resolveAgent: (id: AgentId) => string;
}

// The memory panel (build2-ui-design §3): a still, browsable three-tier accordion — FACTS /
// RELATIONSHIPS / METHODS — each independently collapsible with a count badge, keyed and updating in
// place (never a second scrolling log). An `as of e-N` caption ties it to the transport, and
// `established @e-N` is a seek target. A PURE reader of the memory projection; the expand state is App
// state passed in.
export function MemoryPanel({
  memory,
  selectedAgent,
  expanded,
  onToggle,
  onSeek,
  resolveAgent,
}: MemoryPanelProps): ReactElement {
  return (
    <section className="panel panel--memory" aria-label="Memory">
      <header className="panel__head">
        <h2 className="panel__title">Memory</h2>
        <p className="panel__sub">as of e-{memory.asOf}</p>
      </header>

      <div className="memory">
        <Tier
          tierKey="facts"
          title="Facts"
          count={memory.facts.length}
          expanded={expanded.has('facts')}
          onToggle={onToggle}
        >
          {memory.facts.length === 0 ? (
            <p className="memory-empty">Nothing established yet.</p>
          ) : (
            <ul className="memory-list">
              {memory.facts.map((fact) => (
                <FactRow
                  key={fact.key}
                  fact={fact}
                  dimmed={factDimmed(fact, selectedAgent)}
                  onSeek={onSeek}
                />
              ))}
            </ul>
          )}
        </Tier>

        <Tier
          tierKey="relationships"
          title="Relationships"
          count={memory.relationships.length}
          expanded={expanded.has('relationships')}
          onToggle={onToggle}
        >
          {memory.relationships.length === 0 ? (
            <p className="memory-empty">No relationships yet.</p>
          ) : (
            <ul className="memory-list">
              {memory.relationships.map((relationship) => (
                <RelationshipRow
                  key={relationship.key}
                  relationship={relationship}
                  dimmed={
                    selectedAgent !== null &&
                    relationship.agentId !== selectedAgent
                  }
                  resolveAgent={resolveAgent}
                  onSeek={onSeek}
                />
              ))}
            </ul>
          )}
        </Tier>

        <Tier
          tierKey="methods"
          title="Methods"
          count={memory.methods.length}
          expanded={expanded.has('methods')}
          onToggle={onToggle}
        >
          {memory.methods.length === 0 ? (
            <p className="memory-empty">No method has been exercised yet.</p>
          ) : (
            <ul className="memory-list">
              {memory.methods.map((method) => (
                <MethodRow key={method.key} method={method} />
              ))}
            </ul>
          )}
        </Tier>
      </div>
    </section>
  );
}

interface TierProps {
  readonly tierKey: string;
  readonly title: string;
  readonly count: number;
  readonly expanded: boolean;
  readonly onToggle: (key: string) => void;
  readonly children: ReactNode;
}

function Tier({
  tierKey,
  title,
  count,
  expanded,
  onToggle,
  children,
}: TierProps): ReactElement {
  return (
    <section className="memory-tier">
      <button
        type="button"
        className="memory-tier__head"
        aria-expanded={expanded}
        onClick={() => {
          onToggle(tierKey);
        }}
      >
        <span className="memory-tier__caret" aria-hidden="true">
          {expanded ? '▾' : '▸'}
        </span>
        <span className="memory-tier__title">{title}</span>
        <span className="memory-tier__count">{count}</span>
      </button>
      {expanded ? <div className="memory-tier__body">{children}</div> : null}
    </section>
  );
}

interface FactRowProps {
  readonly fact: Fact;
  readonly dimmed: boolean;
  readonly onSeek: (seq: number) => void;
}

function FactRow({ fact, dimmed, onSeek }: FactRowProps): ReactElement {
  const rowClass = [
    'fact-row',
    `fact-row--${fact.corner}`,
    dimmed ? 'is-dimmed' : '',
    fact.reopened ? 'fact-row--reopened' : '',
  ]
    .filter((part) => part.length > 0)
    .join(' ');
  const width = Math.max(0, Math.min(100, fact.grade * 100));

  return (
    <li className={rowClass}>
      <div className="fact-row__head">
        <span className="fact-row__glyph" aria-hidden="true">
          {cornerGlyph(fact.corner)}
        </span>
        <span className="fact-row__statement">{fact.statement}</span>
        {fact.reopened ? (
          <span className="fact-row__reopened">reopened</span>
        ) : null}
      </div>
      <div className="fact-row__bar">
        <span
          className="fact-row__fill"
          style={{ width: `${width.toString()}%` }}
        />
      </div>
      <button
        type="button"
        className="fact-row__seek"
        onClick={() => {
          onSeek(fact.establishedSeq);
        }}
      >
        established @e-{fact.establishedSeq}
      </button>
    </li>
  );
}

interface RelationshipRowProps {
  readonly relationship: Relationship;
  readonly dimmed: boolean;
  readonly resolveAgent: (id: AgentId) => string;
  readonly onSeek: (seq: number) => void;
}

function RelationshipRow({
  relationship,
  dimmed,
  resolveAgent,
  onSeek,
}: RelationshipRowProps): ReactElement {
  return (
    <li className={`rel-row${dimmed ? ' is-dimmed' : ''}`}>
      <span className="rel-row__edge">
        {resolveAgent(relationship.agentId)} —{relationship.relation}→{' '}
        {relationship.candidateId}
      </span>
      <span className="rel-row__strength">×{relationship.strength}</span>
      <button
        type="button"
        className="rel-row__age"
        onClick={() => {
          onSeek(relationship.lastSeq);
        }}
      >
        @e-{relationship.lastSeq}
      </button>
    </li>
  );
}

interface MethodRowProps {
  readonly method: MethodCalibration;
}

function MethodRow({ method }: MethodRowProps): ReactElement {
  const lowN = method.sample < RELIABLE_SAMPLE;
  const width = method.sample === 0 ? 0 : (method.heldUp / method.sample) * 100;
  return (
    <li className={`method-row${lowN ? ' method-row--low' : ''}`}>
      <span className="method-row__name">{method.method}</span>
      <div className="method-row__bar">
        <span
          className="method-row__fill"
          style={{ width: `${width.toString()}%` }}
        />
      </div>
      <span className="method-row__rate">
        {method.heldUp}/{method.sample} held
      </span>
      <span className="method-row__n">n = {method.sample}</span>
    </li>
  );
}

// A fact is scoped OUT (dimmed) when an agent is selected and this fact is not one that agent's
// support underwrites — an oracle answer (no agents) dims under any selection. Display only; the fact
// itself is unchanged.
function factDimmed(fact: Fact, selectedAgent: AgentId | null): boolean {
  if (selectedAgent === null) {
    return false;
  }
  return !fact.agents.includes(selectedAgent);
}
