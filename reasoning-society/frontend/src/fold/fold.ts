import type {
  AgentId,
  Answer,
  BeliefState,
  BelnapCorner,
  Candidate,
  CandidateId,
  CurrentQuestion,
  GateDecision,
  QuestionId,
  ReasoningEvent,
} from '../model';
import { gradeOf } from './grade';

// The gate's sign policy (brief §2). θ is the grade floor; a guess also needs the lone-candidate,
// no-live-glut, and ≥2-distinct-corroborator (no-lone-sign) conditions below.
export const SIGN_THRESHOLD = 0.6;
export const MIN_CORROBORATORS = 2;

// A per-candidate accumulator, mutated only inside this pure fold and never escaping it. The
// returned BeliefState is fully immutable; the local Map is a contained implementation detail
// (ts-style: a mutable local as a bounded optimization).
interface Acc {
  content: string;
  readonly supporting: number[];
  readonly opposing: number[];
  readonly supportingAgents: AgentId[];
  struck: boolean;
  readonly firstSeq: number;
}

interface AskedQuestion {
  readonly questionId: QuestionId;
  readonly content: string;
  readonly proposedBy: AgentId;
}

function assertNever(x: never): never {
  throw new Error(`unhandled event variant: ${JSON.stringify(x)}`);
}

// fold(events, playhead) — the heart. A pure reducer over the ordered log up to `playhead`
// (every event with `seq <= playhead`). Replay, live mode, and every panel are a call to this
// function; it holds no state between calls, so scrubbing is just re-folding a prefix (brief §1).
// Assumes `events` is ordered by `seq` (the log's global serialization guarantee).
export function fold(
  events: readonly ReasoningEvent[],
  playhead: number,
): BeliefState {
  const accs = new Map<CandidateId, Acc>();
  const answers = new Map<QuestionId, Answer>();
  let gate: GateDecision = { kind: 'watching' };
  let lastAsked: AskedQuestion | undefined;

  for (const event of events) {
    if (event.seq > playhead) {
      continue;
    }
    switch (event.type) {
      case 'assert': {
        const acc = ensureAcc(accs, event.candidateId, event.seq);
        if (acc.content.length === 0) {
          acc.content = event.content;
        }
        acc.supporting.push(event.seq);
        addAgent(acc, event.agentId);
        break;
      }
      case 'corroborate': {
        const acc = ensureAcc(accs, event.candidateId, event.seq);
        acc.supporting.push(event.seq);
        addAgent(acc, event.agentId);
        break;
      }
      case 'refute': {
        const acc = ensureAcc(accs, event.candidateId, event.seq);
        acc.opposing.push(event.seq);
        break;
      }
      case 'strike': {
        const acc = ensureAcc(accs, event.candidateId, event.seq);
        acc.struck = true;
        break;
      }
      case 'question_proposed':
        // Logged for replay richness (brief §3), but a proposed-yet-unasked question moves no
        // belief and is not the current question until it is asked.
        break;
      case 'question_asked':
        lastAsked = {
          questionId: event.questionId,
          content: event.content,
          proposedBy: event.agentId,
        };
        break;
      case 'answer_given':
        answers.set(event.questionId, event.answer);
        break;
      case 'gate_abstain':
        gate = { kind: 'abstained', reason: event.reason, seq: event.seq };
        break;
      case 'gate_sign':
        gate = {
          kind: 'signed',
          candidateId: event.candidateId,
          seq: event.seq,
        };
        break;
      default:
        assertNever(event);
    }
  }

  const candidates = buildCandidates(accs);
  const cardinality = candidates.filter(
    (candidate) => candidate.corner === 'resolved',
  ).length;

  return {
    playhead,
    candidates,
    cardinality,
    gate,
    currentQuestion: buildCurrentQuestion(lastAsked, answers),
  };
}

// The structurally-signable candidate at a belief state, or `undefined` if the gate must abstain.
// This is the gate's decision recomputed from the fold — a scripted `gate_sign` is honest only when
// this agrees with it. A guess requires: exactly one live for-candidate (cardinality 1), no live
// glut anywhere (a conflict must be resolved first), the lone candidate's grade at or above θ, and
// at least MIN_CORROBORATORS distinct supporting agents (the no-lone-sign floor).
export function signableCandidate(
  belief: BeliefState,
  threshold: number = SIGN_THRESHOLD,
): CandidateId | undefined {
  if (belief.cardinality !== 1) {
    return undefined;
  }
  if (belief.candidates.some((candidate) => candidate.corner === 'conflict')) {
    return undefined;
  }
  const lone = belief.candidates.find(
    (candidate) => candidate.corner === 'resolved',
  );
  if (lone === undefined) {
    return undefined;
  }
  if (lone.grade < threshold) {
    return undefined;
  }
  if (lone.supportingAgents.length < MIN_CORROBORATORS) {
    return undefined;
  }
  return lone.id;
}

// The seq of the answer to a given question, if one has been given anywhere in the log. Used by the
// transport's reveal control to advance the playhead to the oracle's scripted answer (brief §4).
export function answerSeqFor(
  events: readonly ReasoningEvent[],
  question: QuestionId,
): number | undefined {
  for (const event of events) {
    if (event.type === 'answer_given' && event.questionId === question) {
      return event.seq;
    }
  }
  return undefined;
}

function ensureAcc(
  accs: Map<CandidateId, Acc>,
  id: CandidateId,
  seq: number,
): Acc {
  const existing = accs.get(id);
  if (existing !== undefined) {
    return existing;
  }
  const created: Acc = {
    content: '',
    supporting: [],
    opposing: [],
    supportingAgents: [],
    struck: false,
    firstSeq: seq,
  };
  accs.set(id, created);
  return created;
}

function addAgent(acc: Acc, agent: AgentId): void {
  if (!acc.supportingAgents.includes(agent)) {
    acc.supportingAgents.push(agent);
  }
}

// Read a candidate's Belnap corner STRUCTURALLY from its evidence (brief §3, mirrored from
// claimalgebra.calculus.Resolution). A strike dominates: a struck candidate is Superseded even if it
// also carried support. A con-only candidate (opposed, never asserted, not struck) has no signable
// support and reads Missing — it does not arise in a well-formed log, where a refute follows an
// assert and therefore forms a glut.
function cornerOf(acc: Acc): BelnapCorner {
  if (acc.struck) {
    return 'superseded';
  }
  const hasPro = acc.supporting.length > 0;
  const hasCon = acc.opposing.length > 0;
  if (hasPro && hasCon) {
    return 'conflict';
  }
  if (hasPro) {
    return 'resolved';
  }
  return 'missing';
}

function buildCandidates(accs: Map<CandidateId, Acc>): readonly Candidate[] {
  const rows = [...accs.entries()].map(([id, acc]) => {
    const corner = cornerOf(acc);
    const candidate: Candidate = {
      id,
      content: acc.content.length > 0 ? acc.content : id,
      provenance: {
        supporting: [...acc.supporting],
        opposing: [...acc.opposing],
      },
      supportingAgents: [...acc.supportingAgents],
      corner,
      grade: gradeOf(acc.supporting.length, acc.opposing.length, acc.struck),
    };
    return { candidate, firstSeq: acc.firstSeq };
  });

  // Sort by grade descending, then by first-seen ascending so ties are stable and deterministic.
  rows.sort((a, b) => {
    if (a.candidate.grade !== b.candidate.grade) {
      return b.candidate.grade - a.candidate.grade;
    }
    return a.firstSeq - b.firstSeq;
  });

  return rows.map((row) => row.candidate);
}

function buildCurrentQuestion(
  lastAsked: AskedQuestion | undefined,
  answers: Map<QuestionId, Answer>,
): CurrentQuestion | undefined {
  if (lastAsked === undefined) {
    return undefined;
  }
  return {
    questionId: lastAsked.questionId,
    content: lastAsked.content,
    proposedBy: lastAsked.proposedBy,
    answer: answers.get(lastAsked.questionId),
  };
}
