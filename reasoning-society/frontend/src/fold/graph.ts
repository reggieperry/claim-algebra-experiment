import type { AgentId, CandidateId, ReasoningEvent } from '../model';

// The architected agent→claim node/edge model (brief §4, build2-ui-design §2: "store agents = nodes,
// claim-relationships = edges from day one; the list is a node-table VIEW of that model"). Build 2
// renders the LIST (society navigator, memory Tier 2); this model is what a future graph render
// reads, and it is what the list and the relationship tier derive from — so the graph is a drop-in
// that reuses the identical pipeline. Like everything here it is a PURE projection of the log prefix.

// The four claim-touching relations an agent can hold to a claim. `as const` so the union and the
// runtime list never drift (ts-types). A question is agent→question, not agent→claim, so it is not an
// edge in this model — the claim graph is exactly the corroborate/refute structure the future
// force-layout needs (corroborate pulls together, refute pushes apart, brief §4).
export const RELATIONS = [
  'asserts',
  'corroborates',
  'refutes',
  'strikes',
] as const;
export type Relation = (typeof RELATIONS)[number];

// One directed edge: an agent related to a claim at a definite event seq (its birth index, so the
// relationship carries provenance for free, build2-ui-design §3).
export interface ClaimEdge {
  readonly agentId: AgentId;
  readonly candidateId: CandidateId;
  readonly relation: Relation;
  readonly seq: number;
}

// The node/edge graph at a playhead. Nodes are the distinct agents and claims that have touched a
// claim; edges are every claim-touching event. Rendered as a list in v1 (§2: "earn the graph" only
// past ~15–20 nodes), this is the model that render reuses.
export interface SocietyGraph {
  readonly agents: readonly AgentId[];
  readonly claims: readonly CandidateId[];
  readonly edges: readonly ClaimEdge[];
}

function relationOf(
  type: 'assert' | 'corroborate' | 'refute' | 'strike',
): Relation {
  switch (type) {
    case 'assert':
      return 'asserts';
    case 'corroborate':
      return 'corroborates';
    case 'refute':
      return 'refutes';
    case 'strike':
      return 'strikes';
  }
}

// Build the agent→claim graph from the log prefix at `playhead`. A pure fold over the same ordered
// stream `fold` consumes; scrubbing re-derives it, so a relationship born at e-N is simply absent at
// any playhead before N.
export function buildGraph(
  events: readonly ReasoningEvent[],
  playhead: number,
): SocietyGraph {
  const edges: ClaimEdge[] = [];
  const agents = new Set<AgentId>();
  const claims = new Set<CandidateId>();

  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    if (
      event.type === 'assert' ||
      event.type === 'corroborate' ||
      event.type === 'refute' ||
      event.type === 'strike'
    ) {
      edges.push({
        agentId: event.agentId,
        candidateId: event.candidateId,
        relation: relationOf(event.type),
        seq: event.seq,
      });
      agents.add(event.agentId);
      claims.add(event.candidateId);
    }
  }

  return { agents: [...agents], claims: [...claims], edges };
}

// The set of claims an agent has touched (asserted, corroborated, refuted, or struck). This is the
// selection abstraction the agent-filter scopes belief on — a set of claim ids, exactly what a future
// graph selection would produce, so the linking pipeline is identical (§2).
export function candidatesTouchedBy(
  graph: SocietyGraph,
  agentId: AgentId,
): ReadonlySet<CandidateId> {
  const touched = new Set<CandidateId>();
  for (const edge of graph.edges) {
    if (edge.agentId === agentId) {
      touched.add(edge.candidateId);
    }
  }
  return touched;
}
