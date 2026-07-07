import {
  agentId,
  candidateId,
  questionId,
  term,
  type AgentId,
  type ReasoningEvent,
} from '../model';
import { toJson, toTranscript } from './logExport';

// A distributive omit — `Omit` applied to each union member, so every variant keeps its own payload (a
// plain `Omit<ReasoningEvent, …>` would collapse to the shared keys and lose the discriminant). Mirrors
// the scenario builder, so a test event is a fully-typed variant minus the two log-assigned fields.
type EventSpec = ReasoningEvent extends infer E
  ? E extends ReasoningEvent
    ? Omit<E, 'seq' | 'timestamp'>
    : never
  : never;

// Stamp contiguous 1-based seqs (the fold's serialization point) and a monotonic display clock. `seq`
// and `timestamp` are the only fields the log assigns; nothing else is forged.
function stamped(specs: readonly EventSpec[]): readonly ReasoningEvent[] {
  const events: ReasoningEvent[] = [];
  for (const spec of specs) {
    const seq = events.length + 1;
    events.push({ ...spec, seq, timestamp: 1_000 + seq });
  }
  return events;
}

const cartographer = agentId('cartographer');
const prospector = agentId('prospector');
const skeptic = agentId('skeptic');
const dog = candidateId('dog');
const cat = candidateId('cat');
const qAlive = questionId('q-alive');
const qMammal = questionId('q-mammal');

const NAMES = new Map<string, string>([
  [cartographer, 'Cartographer'],
  [prospector, 'Prospector'],
  [skeptic, 'Skeptic'],
]);
// The same resolver shape the UI uses: a friendly name, falling back to the raw id off-roster.
const resolveAgent = (id: AgentId): string => NAMES.get(id) ?? id;

// A fixed export instant — injected, never read from an ambient clock, so the header is deterministic.
const EXPORTED_AT = new Date('2026-07-06T18:00:00.000Z');

// One log exercising every one of the 12 event variants, for the round-trip and exhaustiveness proofs.
const ALL_VARIANTS: readonly ReasoningEvent[] = stamped([
  {
    type: 'assert',
    agentId: cartographer,
    candidateId: dog,
    content: "It's a dog.",
  },
  {
    type: 'corroborate',
    agentId: prospector,
    candidateId: dog,
    note: 'Fetches a ball.',
  },
  {
    type: 'refute',
    agentId: skeptic,
    candidateId: cat,
    note: 'It barks — not a cat.',
  },
  {
    type: 'strike',
    agentId: cartographer,
    candidateId: cat,
    note: 'Struck the cat.',
  },
  {
    type: 'question_proposed',
    agentId: prospector,
    questionId: qMammal,
    content: 'Is it a mammal?',
  },
  {
    type: 'question_asked',
    agentId: prospector,
    questionId: qMammal,
    content: 'Is it a mammal?',
  },
  {
    type: 'clarification_requested',
    questionId: qMammal,
    term: term('mammal'),
  },
  {
    type: 'definition_given',
    agentId: prospector,
    questionId: qMammal,
    term: term('mammal'),
    meaning: 'a warm-blooded vertebrate',
  },
  {
    type: 'definition_remembered',
    term: term('physical object'),
    meaning: 'a tangible thing',
    origin: { gameId: 1, agentId: cartographer, questionId: qAlive, seq: 9 },
  },
  {
    type: 'answer_given',
    questionId: qMammal,
    answer: 'yes',
    governing: [term('mammal')],
  },
  { type: 'gate_abstain', reason: 'Two live hypotheses remain.' },
  { type: 'gate_sign', candidateId: dog },
]);

describe('toTranscript', () => {
  it('renders a representative log as readable lines in seq order, with the outcome summary', () => {
    const events = stamped([
      {
        type: 'question_asked',
        agentId: cartographer,
        questionId: qAlive,
        content: 'Is it alive?',
      },
      {
        type: 'definition_remembered',
        term: term('physical object'),
        meaning: 'a tangible thing',
        origin: {
          gameId: 1,
          agentId: cartographer,
          questionId: qAlive,
          seq: 9,
        },
      },
      {
        type: 'definition_given',
        agentId: cartographer,
        questionId: qAlive,
        term: term('alive'),
        meaning: 'a living organism',
      },
      {
        type: 'answer_given',
        questionId: qAlive,
        answer: 'yes',
        governing: [term('alive')],
      },
      {
        type: 'assert',
        agentId: cartographer,
        candidateId: dog,
        content: "It's a dog.",
      },
      {
        type: 'refute',
        agentId: skeptic,
        candidateId: cat,
        note: 'It barks — not a cat.',
      },
      { type: 'gate_sign', candidateId: dog },
    ]);

    const expected = `# Reasoning Society — event log
7 events · exported 2026-07-06T18:00:00.000Z

e-1  Cartographer asks: Is it alive?
e-2  Memory recalls “physical object”: a tangible thing
e-3  Cartographer defines “alive”: a living organism
e-4  Oracle answers: YES · grounded to alive
e-5  Cartographer asserts: It's a dog.
e-6  Skeptic refutes cat: It barks — not a cat.
e-7  Gate signs: dog

— outcome: gate SIGNED “It's a dog.”
`;

    expect(toTranscript(events, resolveAgent, EXPORTED_AT)).toBe(expected);
  });

  it('reports the event count and injected export time in the header', () => {
    const events = stamped([
      {
        type: 'assert',
        agentId: cartographer,
        candidateId: dog,
        content: "It's a dog.",
      },
    ]);
    const transcript = toTranscript(events, resolveAgent, EXPORTED_AT);
    const lines = transcript.split('\n');
    expect(lines[0]).toBe('# Reasoning Society — event log');
    // A single event is singular, and the timestamp is exactly the injected instant.
    expect(lines[1]).toBe('1 event · exported 2026-07-06T18:00:00.000Z');
  });

  it('renders every event variant without an undefined or [object Object] leak', () => {
    const transcript = toTranscript(ALL_VARIANTS, resolveAgent, EXPORTED_AT);
    expect(transcript).not.toMatch(/undefined/);
    expect(transcript).not.toContain('[object Object]');

    // Exactly one body line per event, each with a rendered detail past the verb.
    const bodyLines = transcript
      .split('\n')
      .filter((line) => line.startsWith('e-'));
    expect(bodyLines).toHaveLength(ALL_VARIANTS.length);
    for (const line of bodyLines) {
      expect(line.length).toBeGreaterThan('e-1  '.length);
    }
  });

  it('closes on a gate_abstain with an abstained outcome line', () => {
    const events = stamped([
      {
        type: 'assert',
        agentId: cartographer,
        candidateId: dog,
        content: "It's a dog.",
      },
      { type: 'gate_abstain', reason: 'Only one voice backs it.' },
    ]);
    expect(toTranscript(events, resolveAgent, EXPORTED_AT)).toContain(
      '— outcome: gate abstained — Only one voice backs it.',
    );
  });

  it('adds no outcome line while the game is still in flight', () => {
    const events = stamped([
      {
        type: 'assert',
        agentId: cartographer,
        candidateId: dog,
        content: "It's a dog.",
      },
    ]);
    expect(toTranscript(events, resolveAgent, EXPORTED_AT)).not.toContain(
      'outcome:',
    );
  });

  it('renders an empty log as a header-only transcript', () => {
    const transcript = toTranscript([], resolveAgent, EXPORTED_AT);
    expect(transcript).toContain(
      '0 events · exported 2026-07-06T18:00:00.000Z',
    );
    expect(transcript).not.toContain('e-');
    expect(transcript).not.toContain('outcome:');
  });
});

describe('toJson', () => {
  it('round-trips: parsing the JSON deep-equals the events', () => {
    expect(JSON.parse(toJson(ALL_VARIANTS))).toEqual(ALL_VARIANTS);
  });

  it('pretty-prints with a 2-space indent', () => {
    const json = toJson(ALL_VARIANTS);
    // The array opens, each element object sits at 2 spaces, its keys at 4.
    expect(json).toMatch(/^\[\n {2}\{\n {4}"/);
    expect(json).toContain('\n    "type": "assert"');
  });
});
