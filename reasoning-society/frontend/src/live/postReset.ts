// The default per-call budget for the reset POST. Every external call carries a timeout
// (ts-concurrency): a hung backend must not leave the Full reset button spinning forever.
const DEFAULT_TIMEOUT_MS = 10_000;

export interface PostResetOptions {
  readonly url?: string;
  // Injectable for tests; defaults to a self-aborting timeout signal.
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

/**
 * Ask the backend to FULLY reset the session via `POST /reset` — the two-tier reset's "Full Reset"
 * (two-tier-reset-design §Reset mechanics). Unlike `POST /start` (New Game, which keeps learned
 * definitions), the backend's GameSupervisor here cancels the running game AND clears persistent memory
 * and the game counter, forking a fresh game byte-identical to the first. Serialized on the backend, so
 * a double-click cannot stack games. Resolves on a 2xx; rejects with an `Error` on a non-2xx response or
 * a network/timeout failure, so the caller can surface a transient error and let the operator retry. It
 * never throws a non-`Error`.
 */
export async function postReset(options: PostResetOptions = {}): Promise<void> {
  const { url = '/reset', timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const signal = options.signal ?? AbortSignal.timeout(timeoutMs);
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw new Error(
      `reset POST rejected with status ${response.status.toString()}`,
    );
  }
}
