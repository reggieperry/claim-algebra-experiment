import { afterEach, describe, expect, it, vi } from 'vitest';

import { postChallenge } from './postChallenge';

// A caller-supplied signal keeps the unit test off the real timeout timer (determinism, ts-testing).
const inertSignal = (): AbortSignal => new AbortController().signal;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('postChallenge', () => {
  it('POSTs {questionId, term} as JSON to /challenge', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await postChallenge('q1', 'alive', { signal: inertSignal() });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/challenge');
    expect(init).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ questionId: 'q1', term: 'alive' }),
    });
  });

  it('rejects with an Error on a non-ok response (a 400 blank-term surface)', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: false, status: 400 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      postChallenge('q1', 'alive', { signal: inertSignal() }),
    ).rejects.toThrow(/400/);
  });
});
