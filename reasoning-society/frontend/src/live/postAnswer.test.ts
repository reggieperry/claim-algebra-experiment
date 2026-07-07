import { afterEach, describe, expect, it, vi } from 'vitest';

import { postAnswer } from './postAnswer';

// A caller-supplied signal keeps the unit test off the real timeout timer (determinism, ts-testing).
const inertSignal = (): AbortSignal => new AbortController().signal;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('postAnswer', () => {
  it('POSTs {questionId, answer} as JSON to /answer', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: true, status: 200 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await postAnswer('q1', 'yes', { signal: inertSignal() });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/answer');
    expect(init).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ questionId: 'q1', answer: 'yes' }),
    });
  });

  it('rejects with an Error on a non-ok response (a 400 malformed-body surface)', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue({ ok: false, status: 400 } as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      postAnswer('q1', 'no', { signal: inertSignal() }),
    ).rejects.toThrow(/400/);
  });
});
