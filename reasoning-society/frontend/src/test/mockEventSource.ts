// A deterministic EventSource test double (ts-testing: no real network; mock only what we own — the
// browser's EventSource seam). A test stubs `globalThis.EventSource` with this class, then drives the
// hook by pushing lifecycle events (emitOpen / emitError) and frames (emit). The hook wires its handlers
// onto the instance exactly as it would a real EventSource, so the seam under test is exercised for real.
export class MockEventSource {
  static instances: MockEventSource[] = [];

  readonly url: string;
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent<unknown>) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  emitOpen(): void {
    this.onopen?.();
  }

  emit(data: string): void {
    this.onmessage?.(new MessageEvent<unknown>('message', { data }));
  }

  emitError(): void {
    this.onerror?.();
  }

  close(): void {
    this.closed = true;
  }

  static reset(): void {
    MockEventSource.instances = [];
  }
}
