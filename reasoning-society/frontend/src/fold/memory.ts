import type {
  AgentId,
  BeliefState,
  BelnapCorner,
  CandidateId,
  ReasoningEvent,
} from '../model';
import type { Relation, SocietyGraph } from './graph';

// The memory panel's data (build2-ui-design §3): a STILL, browsable three-tier store — a different
// affordance from the live log, not a second tail. Thin in v1 (it shows what THIS game established;
// its payoff is across games), and, like every panel, a PURE projection of the fold: a fact born at
// e-N is simply absent at any playhead before N, so memory re-derives as you scrub. Never store the
// rendered grade — it is read from the fold each tick.

// Tier 1 — a durable established fact: the human's grounded answers and any gate-signed value, each
// with its exact birth index rendered as a seek target (§3).
export interface Fact {
  readonly key: string;
  readonly statement: string;
  readonly corner: BelnapCorner;
  readonly grade: number;
  readonly establishedSeq: number;
  // A fact that later goes Conflict is greyed with a "reopened" flag rather than silently updating (§3).
  readonly reopened: boolean;
  // The agents whose support underwrites this fact ([] for an oracle answer) — used only to SCOPE the
  // display when an agent is selected, never to compute the grade.
  readonly agents: readonly AgentId[];
}

// Tier 2 — a validated relationship: the future edge-graph's edge list rendered as a browsable list
// first (§3, Backstage typed relations). A roll-up of the architected agent→claim edges.
export interface Relationship {
  readonly key: string;
  readonly agentId: AgentId;
  readonly relation: Relation;
  readonly candidateId: CandidateId;
  readonly strength: number;
  readonly lastSeq: number;
}

// Tier 3 — a method's calibration. Show the SAMPLE SIZE beside the rate so a 1/1 cannot masquerade as
// certainty; low-n greys out (§3). The quietest tier, thin/placeholder in v1.
export interface MethodCalibration {
  readonly key: string;
  readonly method: string;
  readonly heldUp: number;
  readonly sample: number;
}

export interface Memory {
  readonly asOf: number;
  readonly facts: readonly Fact[];
  readonly relationships: readonly Relationship[];
  readonly methods: readonly MethodCalibration[];
}

function questionContentMap(
  events: readonly ReasoningEvent[],
  playhead: number,
): Map<string, string> {
  const byId = new Map<string, string>();
  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (event.type === 'question_proposed' || event.type === 'question_asked') {
      byId.set(event.questionId, event.content);
    }
  }
  return byId;
}

function factsOf(
  events: readonly ReasoningEvent[],
  playhead: number,
  belief: BeliefState,
): readonly Fact[] {
  const questions = questionContentMap(events, playhead);
  // Keyed entities that update in place, never append (§3): dedupe by fact-key so a re-answered
  // question or a re-signed value stays one durable row.
  const byKey = new Map<string, Fact>();

  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (event.type === 'answer_given') {
      const asked = questions.get(event.questionId) ?? event.questionId;
      byKey.set(`answer:${event.questionId}`, {
        key: `answer:${event.questionId}`,
        statement: `${asked} — ${event.answer.toUpperCase()}`,
        corner: 'resolved',
        grade: 1,
        establishedSeq: event.seq,
        reopened: false,
        agents: [],
      });
    } else if (event.type === 'gate_sign') {
      const candidate = belief.candidates.find(
        (row) => row.id === event.candidateId,
      );
      byKey.set(`sign:${event.candidateId}`, {
        key: `sign:${event.candidateId}`,
        statement: candidate?.content ?? event.candidateId,
        corner: candidate?.corner ?? 'missing',
        grade: candidate?.grade ?? 0,
        establishedSeq: event.seq,
        // A signed value that has since fallen into conflict is a reopened fact.
        reopened: candidate?.corner === 'conflict',
        agents: candidate?.supportingAgents ?? [],
      });
    }
  }

  // Sort by relevance (grade), stable on birth index; recency lives in the age column, not the order (§3).
  return [...byKey.values()].sort((a, b) => {
    if (a.grade !== b.grade) {
      return b.grade - a.grade;
    }
    return a.establishedSeq - b.establishedSeq;
  });
}

function relationshipsOf(graph: SocietyGraph): readonly Relationship[] {
  const byKey = new Map<string, Relationship>();
  for (const edge of graph.edges) {
    const key = `${edge.agentId}|${edge.relation}|${edge.candidateId}`;
    const existing = byKey.get(key);
    if (existing === undefined) {
      byKey.set(key, {
        key,
        agentId: edge.agentId,
        relation: edge.relation,
        candidateId: edge.candidateId,
        strength: 1,
        lastSeq: edge.seq,
      });
    } else {
      byKey.set(key, {
        ...existing,
        strength: existing.strength + 1,
        lastSeq: Math.max(existing.lastSeq, edge.seq),
      });
    }
  }
  return [...byKey.values()].sort((a, b) => {
    if (a.strength !== b.strength) {
      return b.strength - a.strength;
    }
    return b.lastSeq - a.lastSeq;
  });
}

// Whether a candidate currently holds up — its claim survived to a resolved corner.
function heldUp(candidateId: CandidateId, belief: BeliefState): boolean {
  const candidate = belief.candidates.find((row) => row.id === candidateId);
  return candidate?.corner === 'resolved';
}

function methodsOf(
  events: readonly ReasoningEvent[],
  playhead: number,
  belief: BeliefState,
  graph: SocietyGraph,
): readonly MethodCalibration[] {
  const methods: MethodCalibration[] = [];

  for (const relation of ['asserts', 'corroborates'] as const) {
    const edges = graph.edges.filter((edge) => edge.relation === relation);
    if (edges.length === 0) {
      continue;
    }
    const held = edges.filter((edge) =>
      heldUp(edge.candidateId, belief),
    ).length;
    methods.push({
      key: `method:${relation}`,
      method: relation,
      heldUp: held,
      sample: edges.length,
    });
  }

  let signs = 0;
  let signsHeld = 0;
  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (event.type === 'gate_sign') {
      signs += 1;
      if (heldUp(event.candidateId, belief)) {
        signsHeld += 1;
      }
    }
  }
  if (signs > 0) {
    methods.push({
      key: 'method:gate-sign',
      method: 'gate sign',
      heldUp: signsHeld,
      sample: signs,
    });
  }

  return methods;
}

// Derive the whole memory store. Pure in `(events, playhead, belief, graph)`, and free of any
// selection — the agent filter dims memory rows in the panel, it never changes what is derived here.
export function memoryOf(
  events: readonly ReasoningEvent[],
  playhead: number,
  belief: BeliefState,
  graph: SocietyGraph,
): Memory {
  return {
    asOf: playhead,
    facts: factsOf(events, playhead, belief),
    relationships: relationshipsOf(graph),
    methods: methodsOf(events, playhead, belief, graph),
  };
}
