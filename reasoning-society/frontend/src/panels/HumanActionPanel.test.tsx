import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import {
  agentId,
  questionId,
  term,
  type AgentId,
  type CurrentQuestion,
  type DefinitionClaim,
} from '../model';
import { HumanActionPanel } from './HumanActionPanel';

const proposer = agentId('a1');
const qAlive = questionId('q-alive');
const resolveAgent = (id: AgentId): string =>
  id === proposer ? 'Cartographer' : id;

const aliveDefinition: DefinitionClaim = {
  term: term('alive'),
  meaning: 'a living creature currently alive',
  establishedSeq: 21,
  origin: { agent: proposer, questionId: qAlive },
};

// A current question with no clarification outstanding — the baseline open turn.
function openQuestion(
  overrides: Partial<CurrentQuestion> = {},
): CurrentQuestion {
  return {
    questionId: qAlive,
    content: 'Is it alive?',
    proposedBy: proposer,
    answer: undefined,
    definitions: [],
    pendingChallenge: undefined,
    ...overrides,
  };
}

function renderPanel(
  overrides: Partial<{
    question: CurrentQuestion | undefined;
    onAnswer: (answer: 'yes' | 'no' | 'unknown') => void;
    onChallenge: (challenged: string) => void;
    live: boolean;
    error: string | null;
    challengeError: string | null;
  }> = {},
) {
  const onAnswer = overrides.onAnswer ?? vi.fn();
  const onChallenge = overrides.onChallenge ?? vi.fn();
  render(
    <HumanActionPanel
      question={'question' in overrides ? overrides.question : openQuestion()}
      resolveAgent={resolveAgent}
      onAnswer={onAnswer}
      onChallenge={onChallenge}
      live={overrides.live ?? true}
      error={overrides.error ?? null}
      challengeError={overrides.challengeError ?? null}
    />,
  );
  return { onAnswer, onChallenge };
}

describe('HumanActionPanel', () => {
  it('offers the answer control on an open question (live, unchallenged)', () => {
    renderPanel();
    const answers = screen.getByRole('group', { name: /answer/i });
    expect(
      within(answers).getByRole('button', { name: /^yes$/i }),
    ).toBeEnabled();
    expect(
      screen.getByRole('textbox', { name: /challenge a term/i }),
    ).toBeInTheDocument();
  });

  it('POSTs a challenge with the typed term through onChallenge', async () => {
    const user = userEvent.setup();
    const { onChallenge } = renderPanel();

    await user.type(
      screen.getByRole('textbox', { name: /challenge a term/i }),
      'alive',
    );
    await user.click(screen.getByRole('button', { name: /^challenge$/i }));

    expect(onChallenge).toHaveBeenCalledTimes(1);
    expect(onChallenge).toHaveBeenCalledWith('alive');
  });

  it('does not send a blank challenge (the send button is disabled until a term is typed)', async () => {
    const user = userEvent.setup();
    const { onChallenge } = renderPanel();

    expect(screen.getByRole('button', { name: /^challenge$/i })).toBeDisabled();
    // Whitespace alone is still blank — no send.
    await user.type(
      screen.getByRole('textbox', { name: /challenge a term/i }),
      '   ',
    );
    expect(screen.getByRole('button', { name: /^challenge$/i })).toBeDisabled();
    expect(onChallenge).not.toHaveBeenCalled();
  });

  it('GATES answering while a challenge is open, showing the waiting state', () => {
    renderPanel({
      question: openQuestion({ pendingChallenge: { term: term('alive') } }),
    });

    // Every answer button is disabled until the definition arrives (the ordering gate).
    const answers = screen.getByRole('group', { name: /answer/i });
    for (const label of [/^yes$/i, /^no$/i, /^unknown$/i]) {
      expect(
        within(answers).getByRole('button', { name: label }),
      ).toBeDisabled();
    }
    // The waiting state names the asking agent and the challenged term.
    expect(screen.getByRole('status')).toHaveTextContent(
      /waiting for Cartographer.+definition.+alive/i,
    );
    // The challenge control is withdrawn while a challenge is already outstanding.
    expect(
      screen.queryByRole('textbox', { name: /challenge a term/i }),
    ).not.toBeInTheDocument();
  });

  it('RE-ENABLES answering once the definition arrives, and renders it as a claim', () => {
    renderPanel({
      question: openQuestion({
        definitions: [aliveDefinition],
        pendingChallenge: undefined,
      }),
    });

    // Answering is open again.
    const answers = screen.getByRole('group', { name: /answer/i });
    expect(
      within(answers).getByRole('button', { name: /^yes$/i }),
    ).toBeEnabled();
    // The definition shows as a claim — term, meaning, and its source agent (provenance).
    const defs = screen.getByRole('list', { name: /definitions given/i });
    expect(within(defs).getByText('alive')).toBeInTheDocument();
    expect(
      within(defs).getByText(/a living creature currently alive/i),
    ).toBeInTheDocument();
    expect(within(defs).getByText(/by Cartographer/i)).toBeInTheDocument();
  });

  it('hides the challenge control in offline mode (no scripted clarification)', () => {
    renderPanel({ live: false });
    expect(
      screen.queryByRole('textbox', { name: /challenge a term/i }),
    ).not.toBeInTheDocument();
  });

  it('disables answering once the question is answered', () => {
    renderPanel({ question: openQuestion({ answer: 'no' }) });
    const answers = screen.getByRole('group', { name: /answer/i });
    expect(
      within(answers).getByRole('button', { name: /^no$/i }),
    ).toBeDisabled();
    expect(screen.getByText(/oracle answered: no/i)).toBeInTheDocument();
  });
});
