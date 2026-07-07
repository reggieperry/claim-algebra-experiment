// The default per-call budget for the start POST. Every external call carries a timeout
// (ts-concurrency): a hung backend must not leave the New game button spinning forever.
const DEFAULT_TIMEOUT_MS = 10_000;

export interface PostStartOptions {
  readonly url?: string;
  // Injectable for tests; defaults to a self-aborting timeout signal.
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

/**
 * Ask the backend to (re)start the game via `POST /start`. The backend's GameSupervisor cancels the
 * running game, resets the shared log and oracle, and forks one fresh game — serialized, so a
 * double-click cannot stack games. Resolves on a 2xx; rejects with an `Error` on a non-2xx response
 * or a network/timeout failure, so the caller can surface a transient error and let the operator
 * retry. It never throws a non-`Error`.
 */
export async function postStart(options: PostStartOptions = {}): Promise<void> {
  const { url = '/start', timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const signal = options.signal ?? AbortSignal.timeout(timeoutMs);
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw new Error(
      `start POST rejected with status ${response.status.toString()}`,
    );
  }
}
