import { useCallback, useEffect, useState } from 'react';

import type { ReasoningEvent } from '../model';
import { decodeEvent } from '../wire';

// The live event source's connection state — surfaced so the header can show LIVE vs offline, and so the
// App can fall back to the scripted demo when the backend is unreachable.
//   connecting   — the EventSource is opening (or no EventSource exists yet)
//   live         — the stream is open and frames are flowing
//   disconnected — the stream errored / the backend is down (App falls back to the mock)
export type LiveStatus = 'connecting' | 'live' | 'disconnected';

export interface LiveEvents {
  // The growing, ordered log — the SINGLE source of truth. The fold and every panel are pure readers of
  // it; nothing mirrors the belief state here (ts-react: derive, don't store).
  readonly events: readonly ReasoningEvent[];
  readonly status: LiveStatus;
  // Drop the accumulated log and open a FRESH EventSource — used after a `New game` restart so the
  // stream catches up on the reset backend log (the new game only, from seq 1) rather than filtering
  // the new events out against the old subscription's high-water seq.
  readonly reconnect: () => void;
}

// Parse a frame's `data` payload without throwing — a non-JSON frame becomes `null`, which the decoder
// then rejects (untrusted input, fail closed).
function parseFrame(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

/**
 * Subscribe to the backend's `GET /events` Server-Sent-Events stream, decode each frame at the trust
 * boundary, and accumulate the valid ones into a growing `ReasoningEvent[]`. Malformed frames are dropped
 * (never crash the stream). The EventSource is torn down on unmount. Where no `EventSource` exists (the
 * jsdom test runtime, or an ancient browser) the hook reports `disconnected` at once, so a caller can
 * fall back to an offline demo rather than break.
 */
export function useLiveEvents(url = '/events'): LiveEvents {
  const [events, setEvents] = useState<readonly ReasoningEvent[]>([]);
  // Where no EventSource exists (jsdom, an ancient browser) there is nothing to connect to, so start
  // `disconnected` — the App shows the offline demo. A lazy initializer reads the stable, synchronous
  // condition once at mount, so the effect never has to set it synchronously (cascading-render trap).
  const [status, setStatus] = useState<LiveStatus>(() =>
    typeof EventSource === 'undefined' ? 'disconnected' : 'connecting',
  );
  // A monotonic subscription generation. Bumping it re-runs the effect below, which tears the current
  // EventSource down (its cleanup) and opens a fresh one — the clean way to force a reconnect while the
  // effect keeps sole ownership of the stream's lifecycle.
  const [generation, setGeneration] = useState(0);

  const reconnect = useCallback((): void => {
    setEvents([]); // drop the old game's log — the fresh stream refills from seq 1
    setStatus(
      typeof EventSource === 'undefined' ? 'disconnected' : 'connecting',
    );
    setGeneration((current) => current + 1); // force the effect to reopen the EventSource
  }, []);

  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      return undefined;
    }

    const source = new EventSource(url);

    source.onopen = (): void => {
      setStatus('live');
    };

    source.onmessage = (message: MessageEvent<unknown>): void => {
      const raw = message.data;
      if (typeof raw !== 'string') {
        return; // a non-text frame is not one of ours — drop it
      }
      const event = decodeEvent(parseFrame(raw));
      if (event === null) {
        return; // malformed frame — drop it, keep the stream alive
      }
      setEvents((previous) => [...previous, event]);
    };

    source.onerror = (): void => {
      // The stream dropped or the backend is unreachable. EventSource retries on its own; the App shows
      // the offline demo meanwhile, and a successful reconnect flips `status` back to `live`.
      setStatus('disconnected');
    };

    return () => {
      source.close();
    };
    // `generation` is a re-subscription trigger, not read in the body: bumping it via `reconnect`
    // re-runs this effect, tearing the current source down (cleanup) and opening a fresh one.
  }, [url, generation]);

  return { events, status, reconnect };
}
