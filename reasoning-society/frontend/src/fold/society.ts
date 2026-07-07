import type {
  AgentId,
  BeliefState,
  BelnapCorner,
  Candidate,
  ReasoningEvent,
} from '../model';
import type { SocietyGraph } from './graph';

// The society navigator's data (build2-ui-design §2): one dense summary per agent — its verb counts,
// when it last spoke, and its dominant epistemic corner now — plus the society-level diversity scalar
// and the monoculture warning. A PURE projection of the fold: it takes no selection, so the agent
// filter can never perturb it (the load-bearing safety rule — filtering is a VIEW, never a re-fold).

export interface AgentSummary {
  readonly id: AgentId;
  // Claim-touching verb counts, read from the architected graph edges (§2 k9s +/¬/⊘ columns).
  readonly asserted: number;
  readonly refuted: number;
  readonly superseded: number;
  // The last event seq at which this agent acted at all (a claim OR a question) — `undefined` marks a
  // silent/dead agent, visible at a glance (§2).
  readonly lastSpokeSeq: number | undefined;
  // The corner of the highest-grade candidate this agent still supports — its stance now, rendered in
  // the SAME four-corner palette (§2 leading band). `undefined` when it backs no live candidate.
  readonly dominantCorner: BelnapCorner | undefined;
}

export interface Society {
  // Every roster agent, silent ones included, in roster order (stable slots across scrubbing).
  readonly agents: readonly AgentSummary[];
  // Distinct live candidate positions — a candidate that still carries pro support (resolved or the
  // conflict glut), not one that has been struck away (§2).
  readonly diversity: number;
  // The echo-chamber signal (§2): the whole society sits on one position, more than one voice backs
  // it, and no refutation has ever been raised — agreement with no productive disagreement (brief §2:
  // "if all agents agree on everything the game is boring; that boringness IS monoculture").
  readonly monoculture: boolean;
}

// The agent attributed to an event, or `undefined` for the oracle's answer and the gate's decisions
// (which no agent authors). Exhaustive over the event union — a new variant breaks the build.
function agentOf(event: ReasoningEvent): AgentId | undefined {
  switch (event.type) {
    // The seven agent-bearing variants — including the asking agent's `definition_given` (the
    // proposer speaks), mirroring the Scala `Event.agentId` giving `Some` on `DefinitionGiven`.
    case 'assert':
    case 'corroborate':
    case 'refute':
    case 'strike':
    case 'question_proposed':
    case 'question_asked':
    case 'definition_given':
      return event.agentId;
    // The human's challenge, a RECALLED definition (its author spoke in a prior game — the origin
    // agent is provenance, not a this-game speaker), the oracle's answer, and the gate's decisions no
    // this-game agent authors.
    case 'clarification_requested':
    case 'definition_remembered':
    case 'answer_given':
    case 'gate_abstain':
    case 'gate_sign':
      return undefined;
  }
}

function lastSpokeOf(
  events: readonly ReasoningEvent[],
  playhead: number,
  agentId: AgentId,
): number | undefined {
  let last: number | undefined;
  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (agentOf(event) === agentId) {
      last = event.seq;
    }
  }
  return last;
}

// The corner the agent stands hardest behind. `candidates` is grade-sorted (fold), so the first one
// this agent supports is its highest-grade backing — its dominant stance.
function dominantCornerOf(
  agentId: AgentId,
  candidates: readonly Candidate[],
): BelnapCorner | undefined {
  const backed = candidates.find((candidate) =>
    candidate.supportingAgents.includes(agentId),
  );
  return backed?.corner;
}

// Derive the whole society view. Pure in `(events, playhead, belief, roster, graph)`; identical
// output for identical input, and no argument carries the selected agent — display filtering happens
// later, in the panels, over this same full-log projection.
export function societyOf(
  events: readonly ReasoningEvent[],
  playhead: number,
  belief: BeliefState,
  roster: readonly AgentId[],
  graph: SocietyGraph,
): Society {
  const agents = roster.map((id): AgentSummary => {
    let asserted = 0;
    let refuted = 0;
    let superseded = 0;
    for (const edge of graph.edges) {
      if (edge.agentId !== id) {
        continue;
      }
      switch (edge.relation) {
        case 'asserts':
          asserted += 1;
          break;
        case 'corroborates':
          break;
        case 'refutes':
          refuted += 1;
          break;
        case 'strikes':
          superseded += 1;
          break;
      }
    }
    return {
      id,
      asserted,
      refuted,
      superseded,
      lastSpokeSeq: lastSpokeOf(events, playhead, id),
      dominantCorner: dominantCornerOf(id, belief.candidates),
    };
  });

  // A live position is a candidate still carrying pro support: resolved (uncontradicted) or conflict
  // (a supported claim now contradicted). A struck candidate has been withdrawn and no longer counts.
  const livePositions = belief.candidates.filter(
    (candidate) =>
      candidate.corner === 'resolved' || candidate.corner === 'conflict',
  );
  const diversity = livePositions.length;

  const everRefuted = graph.edges.some((edge) => edge.relation === 'refutes');
  const lone = livePositions[0];
  const monoculture =
    diversity === 1 &&
    lone !== undefined &&
    lone.supportingAgents.length >= 2 &&
    !everRefuted;

  return { agents, diversity, monoculture };
}
