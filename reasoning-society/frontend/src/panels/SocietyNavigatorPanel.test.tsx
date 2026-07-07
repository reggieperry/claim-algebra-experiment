import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Society } from '../fold';
import { agentId, type AgentId } from '../model';
import { SocietyNavigatorPanel } from './SocietyNavigatorPanel';

const a1 = agentId('a1');
const a2 = agentId('a2');
const a3 = agentId('a3');
const roster = [a1, a2, a3];

const NAMES: Readonly<Record<string, string>> = {
  a1: 'Cartographer',
  a2: 'Prospector',
  a3: 'Skeptic',
};
const resolveAgent = (id: AgentId): string => NAMES[id] ?? id;
const resolveStance = (): string => 'a diverse strategy';
const gutterOf = (id: AgentId): number => roster.indexOf(id);

const SOCIETY: Society = {
  diversity: 2,
  monoculture: false,
  agents: [
    {
      id: a1,
      asserted: 2,
      refuted: 0,
      superseded: 0,
      lastSpokeSeq: 5,
      dominantCorner: 'resolved',
    },
    {
      id: a2,
      asserted: 1,
      refuted: 1,
      superseded: 0,
      lastSpokeSeq: 8,
      dominantCorner: 'conflict',
    },
    {
      id: a3,
      asserted: 0,
      refuted: 0,
      superseded: 0,
      lastSpokeSeq: undefined,
      dominantCorner: undefined,
    },
  ],
};

function renderNav(
  overrides: Partial<{
    society: Society;
    selectedAgent: AgentId | null;
    onSelectAgent: (id: AgentId | null) => void;
  }> = {},
) {
  const onSelectAgent = overrides.onSelectAgent ?? vi.fn();
  render(
    <SocietyNavigatorPanel
      society={overrides.society ?? SOCIETY}
      selectedAgent={overrides.selectedAgent ?? null}
      onSelectAgent={onSelectAgent}
      resolveAgent={resolveAgent}
      resolveStance={resolveStance}
      gutterOf={gutterOf}
    />,
  );
  return { onSelectAgent };
}

describe('SocietyNavigatorPanel', () => {
  it('renders one dense row per roster agent, with counts and a silent marker', () => {
    renderNav();
    expect(
      screen.getByRole('button', { name: /cartographer/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /prospector/i }),
    ).toBeInTheDocument();
    expect(screen.getByText('+2')).toBeInTheDocument();
    // The Skeptic has not spoken — visible at a glance.
    expect(screen.getByText('silent')).toBeInTheDocument();
  });

  it('shows the diversity scalar and warns only when a monoculture is present', () => {
    renderNav();
    expect(screen.getByText(/2 live/i)).toBeInTheDocument();
    expect(screen.queryByText(/monoculture/i)).not.toBeInTheDocument();

    renderNav({ society: { ...SOCIETY, diversity: 1, monoculture: true } });
    expect(screen.getAllByText(/monoculture/i).length).toBeGreaterThan(0);
  });

  it('reports the clicked agent as the new selection', async () => {
    const user = userEvent.setup();
    const { onSelectAgent } = renderNav();
    await user.click(screen.getByRole('button', { name: /prospector/i }));
    expect(onSelectAgent).toHaveBeenCalledWith(a2);
  });

  it('dims the other agents, marks the selected row, and shows a clearable chip', async () => {
    const user = userEvent.setup();
    const { onSelectAgent } = renderNav({ selectedAgent: a1 });

    // Anchor to the row (its name starts with the agent), not the chip (`scoped to: …`).
    const selected = screen.getByRole('button', { name: /^cartographer/i });
    const other = screen.getByRole('button', { name: /^prospector/i });
    // Highlight over hide: the selected agent stands out, the rest dim (never removed).
    expect(selected).toHaveAttribute('aria-pressed', 'true');
    expect(selected).not.toHaveClass('is-dimmed');
    expect(other).toHaveClass('is-dimmed');

    // The chip prevents linked-view amnesia and clears the filter on click.
    const chip = screen.getByRole('button', {
      name: /scoped to:\s*cartographer/i,
    });
    await user.click(chip);
    expect(onSelectAgent).toHaveBeenCalledWith(null);
  });

  it('clears the filter when the already-selected agent is re-clicked', async () => {
    const user = userEvent.setup();
    const { onSelectAgent } = renderNav({ selectedAgent: a2 });
    await user.click(screen.getByRole('button', { name: /^prospector/i }));
    expect(onSelectAgent).toHaveBeenCalledWith(null);
  });
});
