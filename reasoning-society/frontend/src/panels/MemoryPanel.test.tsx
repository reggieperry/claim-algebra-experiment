import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { Memory } from '../fold';
import { agentId, candidateId, type AgentId } from '../model';
import { MemoryPanel } from './MemoryPanel';

const a1 = agentId('a1');
const dog = candidateId('dog');
const resolveAgent = (id: AgentId): string => (id === a1 ? 'Cartographer' : id);

const MEMORY: Memory = {
  asOf: 30,
  facts: [
    {
      key: 'answer:qa',
      statement: 'Is it alive? — YES',
      corner: 'resolved',
      grade: 1,
      establishedSeq: 3,
      reopened: false,
      agents: [],
    },
    {
      key: 'sign:dog',
      statement: "It's a dog.",
      corner: 'resolved',
      grade: 0.75,
      establishedSeq: 28,
      reopened: false,
      agents: [a1],
    },
  ],
  relationships: [
    {
      key: 'a1|asserts|dog',
      agentId: a1,
      relation: 'asserts',
      candidateId: dog,
      strength: 1,
      lastSeq: 4,
    },
  ],
  methods: [
    { key: 'method:gate-sign', method: 'gate sign', heldUp: 1, sample: 1 },
  ],
};

function renderMemory(
  overrides: Partial<{
    memory: Memory;
    selectedAgent: AgentId | null;
    expanded: ReadonlySet<string>;
    onToggle: (key: string) => void;
    onSeek: (seq: number) => void;
  }> = {},
) {
  const onToggle = overrides.onToggle ?? vi.fn();
  const onSeek = overrides.onSeek ?? vi.fn();
  render(
    <MemoryPanel
      memory={overrides.memory ?? MEMORY}
      selectedAgent={overrides.selectedAgent ?? null}
      expanded={overrides.expanded ?? new Set(['facts'])}
      onToggle={onToggle}
      onSeek={onSeek}
      resolveAgent={resolveAgent}
    />,
  );
  return { onToggle, onSeek };
}

describe('MemoryPanel', () => {
  it('shows three tiers with count badges and an as-of caption', () => {
    renderMemory();
    expect(screen.getByText(/as of e-30/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /facts/i })).toHaveTextContent(
      '2',
    );
    expect(
      screen.getByRole('button', { name: /relationships/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /methods/i }),
    ).toBeInTheDocument();
  });

  it('reveals a tier only when it is expanded, and reports a toggle', async () => {
    const user = userEvent.setup();
    const { onToggle } = renderMemory({ expanded: new Set(['facts']) });

    // Facts is open — its rows are visible; relationships is collapsed.
    expect(screen.getByText("It's a dog.")).toBeInTheDocument();
    expect(screen.queryByText(/—asserts→/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /relationships/i }));
    expect(onToggle).toHaveBeenCalledWith('relationships');
  });

  it('renders a relationship edge when its tier is expanded', () => {
    renderMemory({ expanded: new Set(['relationships']) });
    expect(screen.getByText(/—asserts→/)).toBeInTheDocument();
  });

  it('treats `established @e-N` as a seek target', async () => {
    const user = userEvent.setup();
    const { onSeek } = renderMemory({ expanded: new Set(['facts']) });
    await user.click(
      screen.getByRole('button', { name: /established @e-28/i }),
    );
    expect(onSeek).toHaveBeenCalledWith(28);
  });

  it('dims the facts a selected agent does not underwrite (highlight over hide)', () => {
    renderMemory({ selectedAgent: a1, expanded: new Set(['facts']) });
    // Both facts are still present — nothing is removed.
    const items = screen.getAllByRole('listitem');
    const oracleFact = items.find((li) => li.textContent.includes('alive'));
    const agentFact = items.find((li) => li.textContent.includes('dog'));
    // The oracle answer is not this agent's; the signed value is (it backs it).
    expect(oracleFact).toHaveClass('is-dimmed');
    expect(agentFact).not.toHaveClass('is-dimmed');
  });
});
