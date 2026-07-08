import { afterEach, describe, expect, it, vi } from 'vitest';

import { postRewind } from './postRewind';

// A caller-supplied signal keeps the unit test off the real timeout timer (determinism, ts-testing).
const inertSignal = (): AbortSignal => new AbortController().signal;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('postRewind', () => {
  it('POSTs the chosen seq to /rewind', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await postRewind(7, { signal: inertSignal() });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/rewind');
    expect(init).toMatchObject({ method: 'POST' });
    expect(init?.body).toBe(JSON.stringify({ toSeq: 7 }));
  });

  it('rejects with an Error on a non-ok response', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: false, status: 400 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await expect(postRewind(7, { signal: inertSignal() })).rejects.toThrow(
      /400/,
    );
  });
});
