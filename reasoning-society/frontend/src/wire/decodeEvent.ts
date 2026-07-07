import { ANSWERS, agentId, candidateId, questionId, term } from '../model';
import type { Answer, DefinitionOrigin, ReasoningEvent, Term } from '../model';

// The trust boundary between the backend and the fold. Every SSE frame is UNTRUSTED input (ts-security:
// decode + validate, never `as`-cast raw JSON into a domain type), so this decoder validates the `type`
// discriminator and the required fields of the matching variant against the wire contract in
// `backend/.../Wire.scala`, and fails CLOSED — a malformed, extra-typed, or short frame decodes to
// `null` and is dropped rather than admitted as a lie about its shape. The accepted shapes are the
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

// The optional `governing` reference on `answer_given` (clarification-feature §4). Three outcomes,
// kept distinct because the field is OMITTED when empty: `undefined` in means the key is absent
// (a non-clarified answer — valid, no `governing`); an array of non-blank strings decodes to
// `Term[]`; anything else (a non-array, or an element that is not a non-blank string) is a lie about
// the shape → `null`, which fails the whole frame closed.
function governingTerms(raw: unknown): readonly Term[] | null {
  if (!Array.isArray(raw)) {
    return null;
  }
  // `Array.isArray` narrows to `any[]`; re-type as `unknown[]` so each element is validated, never
  // trusted (ts-security: untrusted input, no unchecked `any` reaches the domain).
  const items = raw as readonly unknown[];
  const terms: Term[] = [];
  for (const item of items) {
    if (typeof item !== 'string' || item.trim().length === 0) {
      return null;
    }
    terms.push(term(item));
  }
  return terms;
}

// The nested `origin` of a `definition_remembered` frame (two-tier-reset-design) — untrusted, so
// every field is validated. `agentId` / `questionId` are non-blank ids, `seq` a finite number, and
// `gameId` is OPTIONAL: absent (a not-yet-stamped provenance) omits the key; present must be a finite
// number, else the whole frame fails closed. Mirrors the backend `Wire.originJson` shape.
function originOf(raw: unknown): DefinitionOrigin | null {
  if (!isRecord(raw)) {
    return null;
  }
  const agent = idField(raw, 'agentId');
  const question = idField(raw, 'questionId');
  const seq = numberField(raw, 'seq');
  if (agent === null || question === null || seq === null) {
    return null;
  }
  const base: DefinitionOrigin = {
    agentId: agentId(agent),
    questionId: questionId(question),
    seq,
  };
  const rawGame = raw.gameId;
  if (rawGame === undefined) {
    return base;
  }
  return typeof rawGame === 'number' && Number.isFinite(rawGame)
    ? { ...base, gameId: rawGame }
    : null;
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
    case 'clarification_requested': {
      // The human's challenge — a questionId and the challenged term. NO agentId (the human's move).
      const question = idField(json, 'questionId');
      const challenged = idField(json, 'term');
      if (question === null || challenged === null) {
        return null;
      }
      return {
        ...base,
        type: 'clarification_requested',
        questionId: questionId(question),
        term: term(challenged),
      };
    }
    case 'definition_given': {
      // The asking agent's meaning — agentId (the proposer), questionId, term, and the meaning.
      const agent = idField(json, 'agentId');
      const question = idField(json, 'questionId');
      const defined = idField(json, 'term');
      const meaning = stringField(json, 'meaning');
      if (
        agent === null ||
        question === null ||
        defined === null ||
        meaning === null
      ) {
        return null;
      }
      return {
        ...base,
        type: 'definition_given',
        agentId: agentId(agent),
        questionId: questionId(question),
        term: term(defined),
        meaning,
      };
    }
    case 'definition_remembered': {
      // A recalled definition — a term, its meaning, and the nested `origin` provenance. NO top-level
      // agentId (its author spoke in a prior game).
      const recalledTerm = idField(json, 'term');
      const meaning = stringField(json, 'meaning');
      const origin = originOf(json.origin);
      if (recalledTerm === null || meaning === null || origin === null) {
        return null;
      }
      return {
        ...base,
        type: 'definition_remembered',
        term: term(recalledTerm),
        meaning,
        origin,
      };
    }
    case 'answer_given': {
      const question = idField(json, 'questionId');
      const answer = json.answer;
      if (question === null || !isAnswer(answer)) {
        return null;
      }
      // `governing` is optional: absent (a non-clarified answer) omits the field; present-but-malformed
      // fails the frame closed.
      const rawGoverning = json.governing;
      const answered = {
        ...base,
        type: 'answer_given' as const,
        questionId: questionId(question),
        answer,
      };
      if (rawGoverning === undefined) {
        return answered;
      }
      const governing = governingTerms(rawGoverning);
      return governing === null ? null : { ...answered, governing };
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
