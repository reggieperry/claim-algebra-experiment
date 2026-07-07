import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { App } from './App';
import { MOCK_EVENTS } from './mock';

function transport(): HTMLElement {
  return screen.getByRole('region', { name: /transport/i });
}

function oracle(): HTMLElement {
  return screen.getByRole('region', { name: /oracle/i });
}

function belief(): HTMLElement {
  return screen.getByRole('region', { name: /belief state/i });
}

describe('App', () => {
  it('renders the Reasoning Society heading and an empty opening board', () => {
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /reasoning society/i, level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/no hypotheses on the board yet/i),
    ).toBeInTheDocument();
    expect(within(transport()).getByText('0 / 33')).toBeInTheDocument();
  });

  it('advances the scripted log when the oracle answers the open question', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Step to the first asked-but-unanswered question.
    await user.click(screen.getByRole('button', { name: /next/i }));
    await user.click(screen.getByRole('button', { name: /next/i }));
    expect(within(oracle()).getByText(/is it alive\?/i)).toBeInTheDocument();

    // Answering advances the log to reveal the scripted answer (the observer gets no vote).
    await user.click(within(oracle()).getByRole('button', { name: /^yes$/i }));
    expect(
      within(oracle()).getByText(/oracle answered: yes/i),
    ).toBeInTheDocument();
  });

  it('steps through the whole log to the gate signing its guess', async () => {
    const user = userEvent.setup();
    render(<App />);

    let stepped = 0;
    while (stepped < MOCK_EVENTS.length) {
      await user.click(screen.getByRole('button', { name: /next/i }));
      stepped += 1;
    }

    expect(screen.getByRole('status')).toHaveTextContent(/signs/i);
    expect(screen.getByText('SIGNED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('auto-advances the playhead over time once play is pressed', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: /play/i }));

    // The replay interval advances the playhead off the start; wait on that observable outcome
    // rather than sleeping (ts-testing).
    await waitFor(
      () => {
        expect(
          within(transport()).queryByText('0 / 33'),
        ).not.toBeInTheDocument();
      },
      { timeout: 3_000 },
    );
    expect(screen.getByRole('button', { name: /pause/i })).toBeInTheDocument();
  });

  // Advance the playhead to a frame with a live field (dog + cat resolved, eagle + wolf struck).
  async function stepTo(user: ReturnType<typeof userEvent.setup>, n: number) {
    for (let i = 0; i < n; i += 1) {
      await user.click(screen.getByRole('button', { name: /next/i }));
    }
  }

  it('scopes the view when an agent is selected — a filter, never a re-fold', async () => {
    const user = userEvent.setup();
    render(<App />);
    await stepTo(user, 18);

    // Before selection: the full belief field is on the board ("It's a dog." also appears in the
    // event log, so scope the query to the belief region).
    expect(within(belief()).getByText("It's a dog.")).toBeInTheDocument();
    expect(screen.getByText(/2 live hypotheses/i)).toBeInTheDocument();

    // Select the Skeptic (who backed cat/eagle, not dog).
    await user.click(screen.getByRole('button', { name: /^skeptic/i }));
    expect(screen.getByText(/scoped to:\s*skeptic/i)).toBeInTheDocument();

    // The load-bearing safety rule: the belief fold ran over the FULL log, so the candidate the
    // Skeptic never touched is still on the board (dimmed, not deleted) and the cardinality is
    // unchanged. Muting an agent changes display, never the computed beliefs.
    expect(within(belief()).getByText("It's a dog.")).toBeInTheDocument();
    expect(screen.getByText(/2 live hypotheses/i)).toBeInTheDocument();

    // Dims the other agents in the navigator.
    expect(screen.getByRole('button', { name: /^cartographer/i })).toHaveClass(
      'is-dimmed',
    );
  });

  it('keeps the agent selection across a scrub and clears it on Escape', async () => {
    const user = userEvent.setup();
    render(<App />);
    await stepTo(user, 18);

    await user.click(screen.getByRole('button', { name: /^skeptic/i }));
    expect(screen.getByText(/scoped to:\s*skeptic/i)).toBeInTheDocument();

    // Selection survives scrubbing (WHO ⊥ WHEN).
    await user.click(screen.getByRole('button', { name: /prev/i }));
    expect(screen.getByText(/scoped to:\s*skeptic/i)).toBeInTheDocument();

    // Esc clears the filter.
    await user.keyboard('{Escape}');
    expect(screen.queryByText(/scoped to:/i)).not.toBeInTheDocument();
  });
});
