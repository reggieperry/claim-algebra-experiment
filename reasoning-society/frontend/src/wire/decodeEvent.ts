import { ANSWERS, agentId, candidateId, questionId } from '../model';
import type { Answer, ReasoningEvent } from '../model';

// The trust boundary between the backend and the fold. Every SSE frame is UNTRUSTED input (ts-security:
// decode + validate, never `as`-cast raw JSON into a domain type), so this decoder validates the `type`
// discriminator and the required fields of the matching variant against the wire contract in
// `backend/.../Wire.scala`, and fails CLOSED — a malformed, extra-typed, or short frame decodes to
// `null` and is dropped rather than admitted as a lie about its shape. The nine accepted shapes are the
// golden examples the backend's own tests pin, so a round-trip test provably ties the two together.

// A non-null object with string keys of `unknown` value — the only thing we can index. A user-defined
// type predicate (ts-types), so no `as`-cast of the raw JSON is needed to read a field.
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

// A finite number field (rejects `NaN`/`Infinity`, which JSON cannot carry but an object literal can).
function numberField(obj: Record<string, unknown>, key: string): number | null {
  const value = obj[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

// A plain string field — `content` / `note` / `reason` may be empty, so emptiness is not rejected here.
function stringField(obj: Record<string, unknown>, key: string): string | null {
  const value = obj[key];
  return typeof value === 'string' ? value : null;
}

// An identifier field — a non-empty, non-blank string. The branded constructor trims and would throw on
// a blank id, so guard here and fail closed to `null` instead of letting an untrusted frame throw.
function idField(obj: Record<string, unknown>, key: string): string | null {
  const value = obj[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

// The oracle token, validated against the closed `ANSWERS` set (the only place a bare string becomes an
// `Answer`, after the membership guard — the sanctioned narrowing of an untrusted value).
function isAnswer(value: unknown): value is Answer {
  return (
    typeof value === 'string' && (ANSWERS as readonly string[]).includes(value)
  );
}

// The three con/pro-claim variants share the agent + candidate + note shape; validate it once.
function noteClaim(obj: Record<string, unknown>): {
  readonly agent: string;
  readonly candidate: string;
  readonly note: string;
} | null {
  const agent = idField(obj, 'agentId');
  const candidate = idField(obj, 'candidateId');
  const note = stringField(obj, 'note');
  if (agent === null || candidate === null || note === null) {
    return null;
  }
  return { agent, candidate, note };
}

/**
 * Decode one wire frame into a typed `ReasoningEvent`, or `null` on anything that does not match the
 * contract exactly. Untrusted input in, a validated domain event or a rejection out — the boundary the
 * live event source and the fold both rely on.
 */
export function decodeEvent(json: unknown): ReasoningEvent | null {
  if (!isRecord(json)) {
    return null;
  }
  const seq = numberField(json, 'seq');
  const timestamp = numberField(json, 'timestamp');
  const type = json.type;
  if (seq === null || timestamp === null || typeof type !== 'string') {
    return null;
  }
  const base = { seq, timestamp };

  switch (type) {
    case 'assert': {
      const agent = idField(json, 'agentId');
      const candidate = idField(json, 'candidateId');
      const content = stringField(json, 'content');
      if (agent === null || candidate === null || content === null) {
        return null;
      }
      return {
        ...base,
        type: 'assert',
        agentId: agentId(agent),
        candidateId: candidateId(candidate),
        content,
      };
    }
    case 'corroborate': {
      const claim = noteClaim(json);
      return claim === null
        ? null
        : {
            ...base,
            type: 'corroborate',
            agentId: agentId(claim.agent),
            candidateId: candidateId(claim.candidate),
            note: claim.note,
          };
    }
    case 'refute': {
      const claim = noteClaim(json);
      return claim === null
        ? null
        : {
            ...base,
            type: 'refute',
            agentId: agentId(claim.agent),
            candidateId: candidateId(claim.candidate),
            note: claim.note,
          };
    }
    case 'strike': {
      const claim = noteClaim(json);
      return claim === null
        ? null
        : {
            ...base,
            type: 'strike',
            agentId: agentId(claim.agent),
            candidateId: candidateId(claim.candidate),
            note: claim.note,
          };
    }
    case 'question_proposed': {
      const agent = idField(json, 'agentId');
      const question = idField(json, 'questionId');
      const content = stringField(json, 'content');
      if (agent === null || question === null || content === null) {
        return null;
      }
      return {
        ...base,
        type: 'question_proposed',
        agentId: agentId(agent),
        questionId: questionId(question),
        content,
      };
    }
    case 'question_asked': {
      const agent = idField(json, 'agentId');
      const question = idField(json, 'questionId');
      const content = stringField(json, 'content');
      if (agent === null || question === null || content === null) {
        return null;
      }
      return {
        ...base,
        type: 'question_asked',
        agentId: agentId(agent),
        questionId: questionId(question),
        content,
      };
    }
    case 'answer_given': {
      const question = idField(json, 'questionId');
      const answer = json.answer;
      if (question === null || !isAnswer(answer)) {
        return null;
      }
      return {
        ...base,
        type: 'answer_given',
        questionId: questionId(question),
        answer,
      };
    }
    case 'gate_abstain': {
      const reason = stringField(json, 'reason');
      return reason === null ? null : { ...base, type: 'gate_abstain', reason };
    }
    case 'gate_sign': {
      const candidate = idField(json, 'candidateId');
      return candidate === null
        ? null
        : { ...base, type: 'gate_sign', candidateId: candidateId(candidate) };
    }
    default:
      // An unrecognized discriminator is not a frame we understand — reject it.
      return null;
  }
}
