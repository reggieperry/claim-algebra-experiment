import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { MockEventSource } from '../test/mockEventSource';
import { useLiveEvents } from './useLiveEvents';

const ASSERT_FRAME =
  '{"seq":1,"timestamp":2,"type":"assert","agentId":"a1","candidateId":"dog","content":"hi"}';
const CORROBORATE_FRAME =
  '{"seq":3,"timestamp":4,"type":"corroborate","agentId":"a2","candidateId":"dog","note":"seconded"}';

afterEach(() => {
  vi.unstubAllGlobals();
  MockEventSource.reset();
});

describe('useLiveEvents', () => {
  it('reports disconnected at once when no EventSource exists (the offline runtime)', () => {
    vi.stubGlobal('EventSource', undefined);
    const { result } = renderHook(() => useLiveEvents());
    expect(result.current.status).toBe('disconnected');
    expect(result.current.events).toEqual([]);
  });

  it('opens the stream, transitions to live, and accumulates decoded events', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { result } = renderHook(() => useLiveEvents());

    // Before the connection opens, the hook is connecting and holds no events.
    expect(result.current.status).toBe('connecting');
    expect(result.current.events).toEqual([]);

    const source = MockEventSource.instances[0];
    expect(source?.url).toBe('/events');

    act(() => {
      source?.emitOpen();
    });
    expect(result.current.status).toBe('live');

    act(() => {
      source?.emit(ASSERT_FRAME);
    });
    act(() => {
      source?.emit(CORROBORATE_FRAME);
    });

    expect(result.current.events).toHaveLength(2);
    expect(result.current.events[0]?.type).toBe('assert');
    expect(result.current.events[1]?.type).toBe('corroborate');
  });

  it('drops malformed frames without crashing and keeps the valid ones', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { result } = renderHook(() => useLiveEvents());
    const source = MockEventSource.instances[0];

    act(() => {
      source?.emit('not json at all');
    });
    act(() => {
      source?.emit(JSON.stringify({ seq: 1, timestamp: 2, type: 'gate_veto' }));
    });
    act(() => {
      source?.emit(ASSERT_FRAME);
    });

    // Only the one valid frame survives; the two malformed frames neither crash nor accumulate.
    expect(result.current.events).toHaveLength(1);
    expect(result.current.events[0]?.type).toBe('assert');
  });

  it('transitions to disconnected on a stream error', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { result } = renderHook(() => useLiveEvents());
    const source = MockEventSource.instances[0];

    act(() => {
      source?.emitOpen();
    });
    expect(result.current.status).toBe('live');

    act(() => {
      source?.emitError();
    });
    expect(result.current.status).toBe('disconnected');
  });

  it('closes the EventSource on unmount', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { unmount } = renderHook(() => useLiveEvents());
    const source = MockEventSource.instances[0];
    expect(source?.closed).toBe(false);

    unmount();
    expect(source?.closed).toBe(true);
  });

  it('reconnect closes the old stream, clears the log, and opens a fresh EventSource', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { result } = renderHook(() => useLiveEvents());

    const first = MockEventSource.instances[0];
    act(() => {
      first?.emitOpen();
    });
    act(() => {
      first?.emit(ASSERT_FRAME);
    });
    expect(result.current.events).toHaveLength(1);

    act(() => {
      result.current.reconnect();
    });

    // The old source is closed, the accumulated log is dropped, and a fresh source is opened on /events.
    expect(first?.closed).toBe(true);
    expect(result.current.events).toEqual([]);
    expect(result.current.status).toBe('connecting');
    expect(MockEventSource.instances).toHaveLength(2);
    expect(MockEventSource.instances[1]?.url).toBe('/events');
  });

  it('accumulates events on the fresh stream after a reconnect, not the old one', () => {
    vi.stubGlobal('EventSource', MockEventSource);
    const { result } = renderHook(() => useLiveEvents());

    const first = MockEventSource.instances[0];
    act(() => {
      first?.emit(ASSERT_FRAME);
    });
    act(() => {
      result.current.reconnect();
    });

    // A frame on the OLD (closed) source is ignored; a frame on the fresh one accumulates from empty.
    const second = MockEventSource.instances[1];
    act(() => {
      second?.emit(CORROBORATE_FRAME);
    });
    expect(result.current.events).toHaveLength(1);
    expect(result.current.events[0]?.type).toBe('corroborate');
  });
});
