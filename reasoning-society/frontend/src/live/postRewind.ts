// The default per-call budget for the rewind POST. Every external call carries a timeout
// (ts-concurrency): a hung backend must not leave the rewind control spinning forever.
const DEFAULT_TIMEOUT_MS = 10_000;

export interface PostRewindOptions {
  readonly url?: string;
  // Injectable for tests; defaults to a self-aborting timeout signal.
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

/**
 * Ask the backend to REWIND to before a poisoned early answer via `POST /rewind` (B2,
 * recovery-and-endgame). `toSeq` is the `answer_given` event seq the human chose to reconsider; the
 * BACKEND snaps it to the round boundary and re-folds the truncated log (the client never computes the
 * snap). The same game resumes, re-asking the poisoned question so the human can answer it afresh.
 * Resolves on a 2xx; rejects with an `Error` on a non-2xx response or a network/timeout failure, so the
 * caller can surface a transient error and let the operator retry. It never throws a non-`Error`.
 */
export async function postRewind(
  toSeq: number,
  options: PostRewindOptions = {},
): Promise<void> {
  const { url = '/rewind', timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const signal = options.signal ?? AbortSignal.timeout(timeoutMs);
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ toSeq }),
    signal,
  });
  if (!response.ok) {
    throw new Error(
      `rewind POST rejected with status ${response.status.toString()}`,
    );
  }
}
