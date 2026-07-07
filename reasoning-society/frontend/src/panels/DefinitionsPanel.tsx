import type { ReactElement } from 'react';

import type { AgentId, DefinitionClaim } from '../model';

interface DefinitionsPanelProps {
  readonly definitions: readonly DefinitionClaim[];
  // The selected agent dims definitions it did not author (highlight-over-hide) — display only, it
  // never changes what is derived (mirrors the memory panel's scoping).
  readonly selectedAgent: AgentId | null;
  readonly onSeek: (seq: number) => void;
  readonly resolveAgent: (id: AgentId) => string;
}

// The "definitions established this game" list (clarification-feature §5) — the seed of the memory
// panel. A still, keyed, browse-not-tail list (mirrors `MemoryPanel`): each row is `term → meaning
// [by agent] [@e-N]`, keyed by term so a re-definition updates in place rather than appending. A PURE
// reader of the `definitionsOf` projection — a definition born at e-N is absent at any playhead
// before N, so the list re-derives as you scrub, and `established @e-N` is a seek target.
export function DefinitionsPanel({
  definitions,
  selectedAgent,
  onSeek,
  resolveAgent,
}: DefinitionsPanelProps): ReactElement {
  return (
    <section className="panel panel--defs" aria-label="Definitions">
      <header className="panel__head">
        <h2 className="panel__title">Definitions</h2>
        <p className="panel__sub">{definitions.length} this game</p>
      </header>

      {definitions.length === 0 ? (
        <p className="panel__empty">No term has been defined yet.</p>
      ) : (
        <ul className="defs-list">
          {definitions.map((definition) => (
            <DefinitionRow
              key={definition.term}
              definition={definition}
              dimmed={
                selectedAgent !== null &&
                definition.origin.agent !== selectedAgent
              }
              onSeek={onSeek}
              resolveAgent={resolveAgent}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

interface DefinitionRowProps {
  readonly definition: DefinitionClaim;
  readonly dimmed: boolean;
  readonly onSeek: (seq: number) => void;
  readonly resolveAgent: (id: AgentId) => string;
}

function DefinitionRow({
  definition,
  dimmed,
  onSeek,
  resolveAgent,
}: DefinitionRowProps): ReactElement {
  // The §5 audit surface: a RECALLED definition (`origin.gameId` present) shows where it came from —
  // "recalled from game N". Rendered from `origin.gameId` (the ORIGIN game), NEVER the recalled event's
  // own `seq`, so the human sees the true cross-game provenance of the current vocabulary.
  const recalledGame = definition.origin.gameId;
  return (
    <li className={`def-row${dimmed ? ' is-dimmed' : ''}`}>
      <div className="def-row__head">
        <span className="def-row__term">{definition.term}</span>
        {recalledGame !== undefined ? (
          <span className="def-row__recalled">
            recalled from game {recalledGame}
          </span>
        ) : null}
        <span className="def-row__by">
          by {resolveAgent(definition.origin.agent)}
        </span>
      </div>
      <p className="def-row__meaning">{definition.meaning}</p>
      <button
        type="button"
        className="def-row__seek"
        onClick={() => {
          onSeek(definition.establishedSeq);
        }}
      >
        established @e-{definition.establishedSeq}
      </button>
    </li>
  );
}
