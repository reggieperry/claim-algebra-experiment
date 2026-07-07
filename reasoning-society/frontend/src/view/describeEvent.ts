import type { AgentId, ReasoningEvent } from '../model';

// The one place an event is rendered to a readable actor / verb / detail triple. Shared so the on-screen
// event stream and the downloadable transcript speak ONE vocabulary — the transcript is the same words the
// UI shows, never a second set invented for export. Exhaustive over the event union (a new variant breaks
// the switch at `assertNever`).
export interface EventLine {
  readonly actor: string;
  readonly verb: string;
  readonly detail: string;
}

// The agent an event is attributed to, or `undefined` for the oracle's answer, the human's challenge, a
// recalled definition (its author spoke in a prior game), and the gate's decisions. Exhaustive.
export function agentOf(event: ReasoningEvent): AgentId | undefined {
  switch (event.type) {
    case 'assert':
    case 'corroborate':
    case 'refute':
    case 'strike':
    case 'question_proposed':
    case 'question_asked':
    case 'definition_given':
      return event.agentId;
    case 'clarification_requested':
    case 'definition_remembered':
    case 'answer_given':
    case 'gate_abstain':
    case 'gate_sign':
      return undefined;
  }
}

export function describeEvent(
  event: ReasoningEvent,
  resolveAgent: (id: AgentId) => string,
): EventLine {
  switch (event.type) {
    case 'assert':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'asserts',
        detail: event.content,
      };
    case 'corroborate':
      return {
        actor: resolveAgent(event.agentId),
        verb: `corroborates ${event.candidateId}`,
        detail: event.note,
      };
    case 'refute':
      return {
        actor: resolveAgent(event.agentId),
        verb: `refutes ${event.candidateId}`,
        detail: event.note,
      };
    case 'strike':
      return {
        actor: resolveAgent(event.agentId),
        verb: `strikes ${event.candidateId}`,
        detail: event.note,
      };
    case 'question_proposed':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'proposes',
        detail: event.content,
      };
    case 'question_asked':
      return {
        actor: resolveAgent(event.agentId),
        verb: 'asks',
        detail: event.content,
      };
    case 'clarification_requested':
      // The human challenges a term before answering (clarification-feature §1) — no agent.
      return {
        actor: 'Human',
        verb: 'challenges',
        detail: `define “${event.term}”`,
      };
    case 'definition_given':
      // The asking agent defines the challenged term (§2).
      return {
        actor: resolveAgent(event.agentId),
        verb: `defines “${event.term}”`,
        detail: event.meaning,
      };
    case 'definition_remembered':
      // A definition recalled from persistent memory (two-tier-reset-design) — carried from a prior
      // game, no this-game author.
      return {
        actor: 'Memory',
        verb: `recalls “${event.term}”`,
        detail: event.meaning,
      };
    case 'answer_given': {
      // A clarified answer records WHAT it was grounded to (§4) — surfaced when `governing` is present.
      const grounded =
        event.governing !== undefined && event.governing.length > 0
          ? ` · grounded to ${event.governing.join(', ')}`
          : '';
      return {
        actor: 'Oracle',
        verb: 'answers',
        detail: `${event.answer.toUpperCase()}${grounded}`,
      };
    }
    case 'gate_abstain':
      return { actor: 'Gate', verb: 'abstains', detail: event.reason };
    case 'gate_sign':
      return { actor: 'Gate', verb: 'signs', detail: event.candidateId };
    default:
      return assertNever(event);
  }
}

function assertNever(x: never): never {
  throw new Error(`unhandled event: ${JSON.stringify(x)}`);
}
