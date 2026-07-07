import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import {
  agentId,
  questionId,
  term,
  type AgentId,
  type DefinitionClaim,
} from '../model';
import { DefinitionsPanel } from './DefinitionsPanel';

const a1 = agentId('a1');
const a2 = agentId('a2');
const qAlive = questionId('q-alive');
const resolveAgent = (id: AgentId): string =>
  id === a1 ? 'Cartographer' : id === a2 ? 'Skeptic' : id;

const definitions: readonly DefinitionClaim[] = [
  {
    term: term('alive'),
    meaning: 'a living creature currently alive',
    establishedSeq: 21,
    origin: { agent: a1, questionId: qAlive },
  },
  {
    term: term('pet'),
    meaning: 'kept as a companion',
    establishedSeq: 27,
    origin: { agent: a2, questionId: qAlive },
  },
];

describe('DefinitionsPanel', () => {
  it('renders each definition as term → meaning [by agent] with a count', () => {
    render(
      <DefinitionsPanel
        definitions={definitions}
        selectedAgent={null}
        onSeek={vi.fn()}
        resolveAgent={resolveAgent}
      />,
    );
    expect(screen.getByText(/2 this game/i)).toBeInTheDocument();
    expect(screen.getByText('alive')).toBeInTheDocument();
    expect(
      screen.getByText(/a living creature currently alive/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/by Cartographer/i)).toBeInTheDocument();
  });

  it('shows an empty state when nothing has been defined yet', () => {
    render(
      <DefinitionsPanel
        definitions={[]}
        selectedAgent={null}
        onSeek={vi.fn()}
        resolveAgent={resolveAgent}
      />,
    );
    expect(
      screen.getByText(/no term has been defined yet/i),
    ).toBeInTheDocument();
  });

  it('treats `established @e-N` as a seek target', async () => {
    const user = userEvent.setup();
    const onSeek = vi.fn();
    render(
      <DefinitionsPanel
        definitions={definitions}
        selectedAgent={null}
        onSeek={onSeek}
        resolveAgent={resolveAgent}
      />,
    );
    await user.click(
      screen.getByRole('button', { name: /established @e-21/i }),
    );
    expect(onSeek).toHaveBeenCalledWith(21);
  });

  it('dims a definition a selected agent did not author (highlight over hide)', () => {
    render(
      <DefinitionsPanel
        definitions={definitions}
        selectedAgent={a1}
        onSeek={vi.fn()}
        resolveAgent={resolveAgent}
      />,
    );
    // Both rows remain — nothing is removed; only the non-authored one dims.
    const items = screen.getAllByRole('listitem');
    const alive = items.find((li) => li.textContent.includes('alive'));
    const pet = items.find((li) => li.textContent.includes('pet'));
    expect(alive).not.toHaveClass('is-dimmed');
    expect(pet).toHaveClass('is-dimmed');
  });

  it('renders a "recalled from game N" badge for a RECALLED definition (origin.gameId), from the game not the seq', () => {
    // A recalled definition carries `origin.gameId` (the ORIGIN game 2), and its establishedSeq (1) is a
    // DIFFERENT number — the badge must read the game, never the seq (§5 audit surface).
    const recalled: readonly DefinitionClaim[] = [
      {
        term: term('alive'),
        meaning: 'a living creature currently alive',
        establishedSeq: 1,
        origin: { agent: a1, questionId: qAlive, gameId: 2 },
      },
    ];
    render(
      <DefinitionsPanel
        definitions={recalled}
        selectedAgent={null}
        onSeek={vi.fn()}
        resolveAgent={resolveAgent}
      />,
    );
    expect(screen.getByText(/recalled from game 2/i)).toBeInTheDocument();
    // The seek target is still the this-log birth index — the badge did not borrow it.
    expect(
      screen.getByRole('button', { name: /established @e-1/i }),
    ).toBeInTheDocument();
  });

  it('shows no recalled badge for a this-game definition (origin.gameId absent) — only "by <agent>"', () => {
    render(
      <DefinitionsPanel
        definitions={definitions}
        selectedAgent={null}
        onSeek={vi.fn()}
        resolveAgent={resolveAgent}
      />,
    );
    expect(screen.queryByText(/recalled from game/i)).not.toBeInTheDocument();
    expect(screen.getByText(/by Cartographer/i)).toBeInTheDocument();
  });
});
