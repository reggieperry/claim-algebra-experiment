import {
  agentId,
  candidateId,
  questionId,
  type AgentId,
  type ReasoningEvent,
} from '../model';

// A hand-written game of Twenty Questions, scripted to show off the three moments Build 1 exists to
// make visible (brief §5, §10):
//   (a) a candidate field NARROWING  — four animal hypotheses pruned to one as evidence arrives;
//   (b) a GLUT forming and flagged   — 'cat' gathers support, then an answer contradicts it, so it
//                                       reads Conflict (Belnap glut) before it is struck;
//   (c) the GATE abstaining then     — it holds while rivals stand, while a glut is open, and while
//       signing                        the leader rests on a single voice, then signs 'dog' once one
//                                       hypothesis is uncontradicted with ≥2 distinct corroborators.
// The human is the oracle thinking of a DOG; the ground truth lives here, outside the agents.

// The society — three deliberately diverse agents (brief §2: diversity is the mitigation for
// monoculture and what makes the disagreement productive).
export interface Agent {
  readonly id: AgentId;
  readonly name: string;
  readonly stance: string;
}

const cartographer = agentId('cartographer');
const prospector = agentId('prospector');
const skeptic = agentId('skeptic');

export const AGENTS: readonly Agent[] = [
  {
    id: cartographer,
    name: 'Cartographer',
    stance: 'splits the space with broad category questions',
  },
  {
    id: prospector,
    name: 'Prospector',
    stance: 'drills into specifics to confirm or kill a hypothesis',
  },
  {
    id: skeptic,
    name: 'Skeptic',
    stance: 'hunts for the rival hypothesis the others missed',
  },
];

// A friendly label for an agent id, falling back to the raw id for anything off-roster.
export function agentName(id: AgentId): string {
  const found = AGENTS.find((agent) => agent.id === id);
  return found === undefined ? id : found.name;
}

const dog = candidateId('dog');
const cat = candidateId('cat');
const wolf = candidateId('wolf');
const eagle = candidateId('eagle');

const qAlive = questionId('q-alive');
const qMammal = questionId('q-mammal');
const qFeathers = questionId('q-feathers');
const qPet = questionId('q-pet');
const qBark = questionId('q-bark');
const qFetch = questionId('q-fetch');

// Build the log with contiguous 1-based seqs and a monotonic display clock. `seq` is the global
// serialization point the fold consumes (brief §1.5), so it is assigned here, in order, once.
const BASE_TS = Date.parse('2026-07-06T18:00:00.000Z');
const TICK_MS = 1_400;

// A distributive omit — `Omit` applied to each union member, so every variant keeps its own payload
// (a plain `Omit<ReasoningEvent, …>` would collapse to the shared keys and lose the discriminant).
type EventSpec = ReasoningEvent extends infer E
  ? E extends ReasoningEvent
    ? Omit<E, 'seq' | 'timestamp'>
    : never
  : never;

function buildScenario(): readonly ReasoningEvent[] {
  const events: ReasoningEvent[] = [];

  const push = (event: EventSpec): void => {
    const seq = events.length + 1;
    // `event` is a fully-typed variant minus the two log-assigned fields; stamping `seq` and
    // `timestamp` back on reconstructs a complete variant (no field is forged).
    events.push({
      ...event,
      seq,
      timestamp: BASE_TS + seq * TICK_MS,
    });
  };

  push({
    type: 'question_proposed',
    agentId: cartographer,
    questionId: qAlive,
    content: 'Is it alive?',
  });
  push({
    type: 'question_asked',
    agentId: cartographer,
    questionId: qAlive,
    content: 'Is it alive?',
  });
  push({ type: 'answer_given', questionId: qAlive, answer: 'yes' });

  // The field opens: four rival hypotheses, all merely asserted, none yet contradicted.
  push({
    type: 'assert',
    agentId: cartographer,
    candidateId: dog,
    content: "It's a dog.",
  });
  push({
    type: 'assert',
    agentId: skeptic,
    candidateId: cat,
    content: "Not so fast — it's a cat.",
  });
  push({
    type: 'assert',
    agentId: skeptic,
    candidateId: eagle,
    content: 'Or an eagle, even.',
  });
  push({
    type: 'assert',
    agentId: prospector,
    candidateId: wolf,
    content: 'A wolf would fit too.',
  });
  push({
    type: 'gate_abstain',
    reason: 'Four hypotheses stand — nowhere near a guess.',
  });

  // Narrowing begins. A mammal question; the Skeptic floats a rival question that is never chosen
  // (logged anyway, for replay richness).
  push({
    type: 'question_proposed',
    agentId: prospector,
    questionId: qMammal,
    content: 'Is it a mammal?',
  });
  push({
    type: 'question_proposed',
    agentId: skeptic,
    questionId: qFeathers,
    content: 'Does it have feathers?',
  });
  push({
    type: 'question_asked',
    agentId: prospector,
    questionId: qMammal,
    content: 'Is it a mammal?',
  });
  push({ type: 'answer_given', questionId: qMammal, answer: 'yes' });
  push({
    type: 'strike',
    agentId: prospector,
    candidateId: eagle,
    note: 'A mammal — the eagle is out.',
  });
  push({ type: 'gate_abstain', reason: 'Three hypotheses remain.' });

  push({
    type: 'question_proposed',
    agentId: prospector,
    questionId: qPet,
    content: 'Is it kept as a pet?',
  });
  push({
    type: 'question_asked',
    agentId: prospector,
    questionId: qPet,
    content: 'Is it kept as a pet?',
  });
  push({ type: 'answer_given', questionId: qPet, answer: 'yes' });
  push({
    type: 'strike',
    agentId: prospector,
    candidateId: wolf,
    note: 'A pet — strike the wolf.',
  });
  // 'cat' gains a second backer, so its coming contradiction lands on real support — a true glut.
  push({
    type: 'corroborate',
    agentId: cartographer,
    candidateId: cat,
    note: 'A cat is a pet, granted.',
  });
  push({
    type: 'gate_abstain',
    reason: 'Two live hypotheses: dog and cat.',
  });

  // The glut. The oracle says it barks; that contradicts 'cat', which still carries support.
  push({
    type: 'question_proposed',
    agentId: prospector,
    questionId: qBark,
    content: 'Does it bark?',
  });
  push({
    type: 'question_asked',
    agentId: prospector,
    questionId: qBark,
    content: 'Does it bark?',
  });
  push({ type: 'answer_given', questionId: qBark, answer: 'yes' });
  push({
    type: 'refute',
    agentId: prospector,
    candidateId: cat,
    note: "It barks — cats don't. 'cat' is contradicted.",
  });
  push({
    type: 'gate_abstain',
    reason: "'cat' is in conflict — a glut to resolve before any guess.",
  });
  push({
    type: 'strike',
    agentId: cartographer,
    candidateId: cat,
    note: 'Barking settles it — strike the cat.',
  });
  // 'dog' now stands alone, but on the Cartographer's single voice — the floor is not yet met.
  push({
    type: 'gate_abstain',
    reason:
      "Only 'dog' stands, but on a single voice — the floor needs two corroborators.",
  });

  // The confirmation run brings the second and third corroborators; the Skeptic concedes.
  push({
    type: 'question_proposed',
    agentId: prospector,
    questionId: qFetch,
    content: 'Does it fetch a ball?',
  });
  push({
    type: 'question_asked',
    agentId: prospector,
    questionId: qFetch,
    content: 'Does it fetch a ball?',
  });
  push({ type: 'answer_given', questionId: qFetch, answer: 'yes' });
  push({
    type: 'corroborate',
    agentId: prospector,
    candidateId: dog,
    note: "Fetching a ball — that's a dog.",
  });
  push({
    type: 'corroborate',
    agentId: skeptic,
    candidateId: dog,
    note: "I'm convinced — it's the dog.",
  });
  push({
    type: 'gate_sign',
    candidateId: dog,
  });

  return events;
}

// The scripted log Build 1 replays. A frozen, ordered `ReasoningEvent[]`; the UI is a pure viewer of
// it (brief §6) and never mutates it.
export const MOCK_EVENTS: readonly ReasoningEvent[] = buildScenario();
