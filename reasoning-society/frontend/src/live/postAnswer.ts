import type { Answer } from '../model';

// The default per-call budget for the answer POST. Every external call carries a timeout (ts-concurrency):
// a hung backend must not leave the button spinning forever.
const DEFAULT_TIMEOUT_MS = 10_000;

export interface PostAnswerOptions {
  readonly url?: string;
  // Injectable for tests; defaults to a self-aborting timeout signal.
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

/**
 * Send the human oracle's reply to the backend's `POST /answer`. The body is the exact command the
 * backend decodes — `{questionId, answer}` with `answer` in the closed `yes|no|unknown` set. Rejects
 * with an `Error` on a non-2xx response (a malformed body is a 400) or a network/timeout failure, so the
 * caller can surface a transient error and let the operator retry; it never throws a non-`Error`.
 */
export async function postAnswer(
  questionId: string,
  answer: Answer,
  options: PostAnswerOptions = {},
): Promise<void> {
  const { url = '/answer', timeoutMs = DEFAULT_TIMEOUT_MS } = options;
  const signal = options.signal ?? AbortSignal.timeout(timeoutMs);
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ questionId, answer }),
    signal,
  });
  if (!response.ok) {
    throw new Error(
      `answer POST rejected with status ${response.status.toString()}`,
    );
  }
}
