import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {
  agentId,
  candidateId,
  type AgentId,
  type ReasoningEvent,
} from '../model';
import { EventStreamPanel } from './EventStreamPanel';

const cartographer = agentId('cartographer');
const dog = candidateId('dog');
const resolveAgent = (id: AgentId): string =>
  id === cartographer ? 'Cartographer' : id;

const EVENTS: readonly ReasoningEvent[] = [
  {
    type: 'assert',
    agentId: cartographer,
    candidateId: dog,
    content: "It's a dog.",
    seq: 1,
    timestamp: 1,
  },
  { type: 'gate_sign', candidateId: dog, seq: 2, timestamp: 2 },
];

// The download's DOM boundary, mocked (jsdom implements the URL statics, so a spy restores cleanly). We
// capture each Blob handed to createObjectURL, the revoked URLs, and the anchor's download/href — read as
// plain properties inside the click stub, never by aliasing `this` — so we can assert the MIME + filename.
let createdBlobs: Blob[] = [];
let revokedUrls: string[] = [];
let capturedDownload: string | undefined;
let capturedHref: string | undefined;

beforeEach(() => {
  createdBlobs = [];
  revokedUrls = [];
  capturedDownload = undefined;
  capturedHref = undefined;
  vi.spyOn(URL, 'createObjectURL').mockImplementation(
    (obj: Blob | MediaSource): string => {
      if (obj instanceof Blob) {
        createdBlobs.push(obj);
      }
      return 'blob:mock-url';
    },
  );
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url: string): void => {
    revokedUrls.push(url);
  });
  // userEvent drives the button by dispatching events, not element.click(), so this stub fires only for
  // the download's own programmatic anchor.click() — capturing its attributes without a real navigation.
  vi.spyOn(HTMLElement.prototype, 'click').mockImplementation(function (
    this: HTMLElement,
  ): void {
    if (this instanceof HTMLAnchorElement) {
      capturedDownload = this.download;
      capturedHref = this.href;
    }
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('EventStreamPanel download log', () => {
  it('downloads a Markdown transcript with the right MIME and filename', async () => {
    const user = userEvent.setup();
    render(
      <EventStreamPanel
        events={EVENTS}
        playhead={EVENTS.length}
        resolveAgent={resolveAgent}
      />,
    );

    await user.click(screen.getByRole('button', { name: /transcript/i }));

    expect(createdBlobs).toHaveLength(1);
    expect(createdBlobs[0]?.type).toBe('text/markdown');
    expect(capturedDownload).toBe('reasoning-society-log.md');
    expect(capturedHref).toBe('blob:mock-url');
    expect(revokedUrls).toContain('blob:mock-url');
    // The Blob carries the pure formatter's output — the transcript is what gets written.
    expect(await createdBlobs[0]?.text()).toContain(
      '# Reasoning Society — event log',
    );
  });

  it('downloads pretty JSON with the right MIME and filename', async () => {
    const user = userEvent.setup();
    render(
      <EventStreamPanel
        events={EVENTS}
        playhead={EVENTS.length}
        resolveAgent={resolveAgent}
      />,
    );

    await user.click(screen.getByRole('button', { name: /json/i }));

    expect(createdBlobs).toHaveLength(1);
    expect(createdBlobs[0]?.type).toBe('application/json');
    expect(capturedDownload).toBe('reasoning-society-log.json');
    // Round-trippable: the Blob body parses back to the events.
    const body = await createdBlobs[0]?.text();
    expect(JSON.parse(body ?? '')).toEqual(EVENTS);
  });

  it('disables both formats when the log is empty', () => {
    render(
      <EventStreamPanel events={[]} playhead={0} resolveAgent={resolveAgent} />,
    );
    expect(screen.getByRole('button', { name: /transcript/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /json/i })).toBeDisabled();
  });
});
