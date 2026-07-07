import type {
  AgentId,
  Answer,
  BeliefState,
  BelnapCorner,
  Candidate,
  CandidateId,
  CurrentQuestion,
  DefinitionClaim,
  GateDecision,
  PendingChallenge,
  QuestionId,
  ReasoningEvent,
  Term,
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

// The latest challenge raised on a question — its term and the seq it was raised at, so the ordering
// gate can tell whether a later `definition_given` has since answered it.
interface Challenge {
  readonly term: Term;
  readonly seq: number;
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
  // The clarification exchange, keyed by question — the latest challenge per question, and the
  // definitions given for it (in seq order). Belief-inert: these feed only the current-question read,
  // never a candidate (mirrors the Scala `project` dropping the clarification pair).
  const lastChallenges = new Map<QuestionId, Challenge>();
  const definitionsByQuestion = new Map<QuestionId, DefinitionClaim[]>();
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
      case 'clarification_requested':
        // Belief-inert (clarification-feature §4): a challenge moves no hypothesis. Recorded so the
        // ordering gate can see the OPEN challenge and hold answering until the agent defines it.
        lastChallenges.set(event.questionId, {
          term: event.term,
          seq: event.seq,
        });
        break;
      case 'definition_given': {
        // Belief-inert: a definition grounds the vocabulary, it is never a hypothesis about the
        // answer. Recorded as a claim for the current-question display and the definitions list.
        const claim: DefinitionClaim = {
          term: event.term,
          meaning: event.meaning,
          establishedSeq: event.seq,
          // A this-game exchange — provenance is the current game, so `origin.gameId` is ABSENT.
          origin: { agent: event.agentId, questionId: event.questionId },
        };
        const existing = definitionsByQuestion.get(event.questionId) ?? [];
        existing.push(claim);
        definitionsByQuestion.set(event.questionId, existing);
        break;
      }
      case 'definition_remembered':
        // Belief-inert (two-tier-reset-design, invariant 1): a recalled definition is grounding
        // vocabulary carried across games, never a hypothesis. It belongs to NO this-game question,
        // so — unlike `definition_given` — it must NOT touch `definitionsByQuestion`: doing so would
        // perturb the ordering gate (`pendingChallengeOf`) on a colliding qid. The definitions panel
        // reads it via a separate projection (`definitionsOf`). Mirrors the Scala `project` drop.
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
    currentQuestion: buildCurrentQuestion(
      lastAsked,
      answers,
      lastChallenges,
      definitionsByQuestion,
    ),
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
  lastChallenges: Map<QuestionId, Challenge>,
  definitionsByQuestion: Map<QuestionId, DefinitionClaim[]>,
): CurrentQuestion | undefined {
  if (lastAsked === undefined) {
    return undefined;
  }
  const answer = answers.get(lastAsked.questionId);
  const definitions = definitionsByQuestion.get(lastAsked.questionId) ?? [];
  return {
    questionId: lastAsked.questionId,
    content: lastAsked.content,
    proposedBy: lastAsked.proposedBy,
    answer,
    definitions,
    pendingChallenge: pendingChallengeOf(
      lastChallenges.get(lastAsked.questionId),
      definitions,
      answer,
    ),
  };
}

// The ordering gate's core read: is a challenge on the current question still awaiting its
// definition? An OPEN challenge is the latest `clarification_requested` for the question with NO
// later `definition_given` (clarification-feature §3). Once answered, the exchange is closed — no
// challenge is outstanding — so an answer clears the gate too. Derived structurally; never stored.
function pendingChallengeOf(
  lastChallenge: Challenge | undefined,
  definitions: readonly DefinitionClaim[],
  answer: Answer | undefined,
): PendingChallenge | undefined {
  if (lastChallenge === undefined || answer !== undefined) {
    return undefined;
  }
  const latestDefinitionSeq = definitions.reduce(
    (max, definition) => Math.max(max, definition.establishedSeq),
    0,
  );
  // A definition raised AFTER the latest challenge answers it; otherwise the challenge is still open.
  if (latestDefinitionSeq > lastChallenge.seq) {
    return undefined;
  }
  return { term: lastChallenge.term };
}
