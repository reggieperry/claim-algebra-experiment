// The default per-call budget for the challenge POST. Every external call carries a timeout
// (ts-concurrency): a hung backend must not leave the control spinning forever.
const DEFAULT_TIMEOUT_MS = 10_000;

export interface PostChallengeOptions {
  readonly url?: string;
  // Injectable for tests; defaults to a self-aborting timeout signal.
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

/**
 * CHALLENGE a term on the current question via the backend's `POST /challenge`
 * (clarification-feature §1) — "define '<term>'", asked BEFORE answering, which pauses grounding
 * until the asking agent supplies the meaning. The body is the exact command the backend decodes —
 * `{questionId, term}`; the backend normalizes the term (trim / case-fold) so it grounds against the
 * same key a stored definition uses. Rejects with an `Error` on a non-2xx response (a blank term is a
 * 400) or a network/timeout failure, so the caller can surface a transient error and let the operator
 * retry; it never throws a non-`Error`.
 */
export async function postChallenge(
  questionId: string,
  term: string,
  options: PostChallengeOptions = {},
): Promise<void> {
  const { url = '/challenge', timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const signal = options.signal ?? AbortSignal.timeout(timeoutMs);
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ questionId, term }),
    signal,
  });
  if (!response.ok) {
    throw new Error(
      `challenge POST rejected with status ${response.status.toString()}`,
    );
  }
}
