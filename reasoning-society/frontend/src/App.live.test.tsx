import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { MockEventSource } from './test/mockEventSource';

// The App wired to the LIVE backend: EventSource is stubbed so the hook connects, and fetch is stubbed so
// the answer POST is observed without a real network (ts-testing: deterministic, mock the seams we own).
// (The other App suite runs OFFLINE, since jsdom has no EventSource, exercising the mock-fallback path.)

const questionFrame = (): string =>
  JSON.stringify({
    seq: 1,
    timestamp: 1,
    type: 'question_asked',
    agentId: 'a1',
    questionId: 'q1',
    content: 'Is it alive?',
  });

describe('App (live backend)', () => {
  beforeEach(() => {
    vi.stubGlobal('EventSource', MockEventSource);
    MockEventSource.reset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('shows the LIVE indicator and POSTs the human answer to /answer', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<App />);

    const source = MockEventSource.instances[0];
    act(() => {
      source?.emitOpen();
    });
    act(() => {
      source?.emit(questionFrame());
    });

    // The header's connection indicator reports the live backend. (The transport also shows a `● LIVE`
    // at-head marker, so target the header's indicator by its unique aria-label.)
    expect(screen.getByLabelText(/backend live/i)).toHaveTextContent('● LIVE');
    // The streamed question is on the floor (scope to the oracle region — it also appears in the log).
    const oracle = screen.getByRole('region', { name: /oracle/i });
    expect(within(oracle).getByText(/is it alive\?/i)).toBeInTheDocument();

    // Answering sends the oracle's verdict to the backend with the question's id.
    await user.click(screen.getByRole('button', { name: /^yes$/i }));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/answer');
    expect(init).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ questionId: 'q1', answer: 'yes' }),
    });
  });

  it('New game POSTs /start, then reconnects the stream (log cleared, a fresh EventSource)', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<App />);

    const first = MockEventSource.instances[0];
    act(() => {
      first?.emitOpen();
    });
    act(() => {
      first?.emit(questionFrame());
    });
    // The first game's question is on the floor.
    expect(
      within(screen.getByRole('region', { name: /oracle/i })).getByText(
        /is it alive\?/i,
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /new game/i }));

    // The restart was requested over POST /start.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/start');
    expect(init).toMatchObject({ method: 'POST' });

    // On success the stream reconnected: a fresh EventSource opened and the old one closed.
    await waitFor(() => {
      expect(MockEventSource.instances).toHaveLength(2);
    });
    expect(first?.closed).toBe(true);

    // The old game's log was dropped — its question is gone until the fresh stream refills the board.
    expect(
      within(screen.getByRole('region', { name: /oracle/i })).queryByText(
        /is it alive\?/i,
      ),
    ).not.toBeInTheDocument();
  });

  it('disables the New game button while the start request is in flight', async () => {
    let resolveFetch: ((response: Response) => void) | undefined;
    const pending = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });
    const fetchMock = vi.fn<typeof fetch>().mockReturnValue(pending);
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<App />);
    act(() => {
      MockEventSource.instances[0]?.emitOpen();
    });

    await user.click(screen.getByRole('button', { name: /new game/i }));

    // While the POST is unresolved the button is disabled (aria-busy) — a double-click cannot fire.
    expect(screen.getByRole('button', { name: /new game/i })).toBeDisabled();

    // Resolving the request re-enables the button.
    act(() => {
      resolveFetch?.({ ok: true, status: 200 } as unknown as Response);
    });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /new game/i })).toBeEnabled();
    });
  });
});
