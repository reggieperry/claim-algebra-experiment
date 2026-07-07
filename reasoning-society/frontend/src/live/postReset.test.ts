import { afterEach, describe, expect, it, vi } from 'vitest';

import { postReset } from './postReset';

// A caller-supplied signal keeps the unit test off the real timeout timer (determinism, ts-testing).
const inertSignal = (): AbortSignal => new AbortController().signal;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('postReset', () => {
  it('POSTs to /reset', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await postReset({ signal: inertSignal() });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/reset');
    expect(init).toMatchObject({ method: 'POST' });
  });

  it('rejects with an Error on a non-ok response', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: false, status: 503 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await expect(postReset({ signal: inertSignal() })).rejects.toThrow(/503/);
  });
});
