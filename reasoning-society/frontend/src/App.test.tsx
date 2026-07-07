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
});
