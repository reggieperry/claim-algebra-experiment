import {
  agentId,
  candidateId,
  type CandidateId,
  type ReasoningEvent,
} from '../model';
import { fold, retiredCandidates, signableCandidate } from './fold';

// Slice 3 of the hypothesis-lifecycle fix, frontend side: the TS fold MIRRORS the backend
// `GameCore` retirement predicate + masking, so the instrument's belief matches the backend at every
// playhead. The predicate (self-withdrawal + ≥2 standing refuters) is recomputed on read from the
// prefix; masking drops the retired candidate's pro/con from the fold. The authority is the
// PREDICATE, never the `retired`/`resurrected` markers (which are audit trace and lag by a round).
//
// These pins mirror `HypothesisLifecycleSuite` on the Scala side — the plant-or-fungus retirement,
// the anti-CWS real-glut HOLD, the replay boundary, and recovery.

type EventSpec = ReasoningEvent extends infer E
  ? E extends ReasoningEvent
    ? Omit<E, 'seq' | 'timestamp'>
    : never
  : never;

// Stamp specs into a contiguous 1-based log (the log's serialization guarantee), so `slice(0, k)` is
// exactly the prefix through seq k — the recompute-on-read boundary.
function log(specs: readonly EventSpec[]): readonly ReasoningEvent[] {
  return specs.map((spec, index) => ({
    ...spec,
    seq: index + 1,
    timestamp: 1_000 * (index + 1),
  }));
}

const driller = agentId('driller');
const skeptic = agentId('skeptic');
const grower = agentId('grower');
const x = agentId('x');
const y = agentId('y');
const z = agentId('z');

const pof = candidateId('plant or fungus');
const apple = candidateId('apple tree');
const h = candidateId('h');

// The plant-or-fungus shape (mirroring the real game): the driller asserts pof, the skeptic refutes,
// the driller REFUTES its own assertion (self-withdrawal) — two standing refuters, every pro-author
// withdrawn — and apple-tree is asserted+corroborated by two distinct agents. The self-withdrawal
// lands at seq 3 (the frontend analog of the real game's e-20), so `slice(0, 2)` is "before" and
// `slice(0, 3)` is "at/after" the retirement.
const pofGame = log([
  {
    type: 'assert',
    agentId: driller,
    candidateId: pof,
    content: 'plant or fungus?',
  },
  { type: 'refute', agentId: skeptic, candidateId: pof, note: 'no' },
  { type: 'refute', agentId: driller, candidateId: pof, note: 'i withdraw' },
  {
    type: 'assert',
    agentId: driller,
    candidateId: apple,
    content: 'apple tree',
  },
  { type: 'corroborate', agentId: grower, candidateId: apple, note: 'agreed' },
]);

const asSet = (ids: readonly CandidateId[]): ReadonlySet<CandidateId> =>
  new Set(ids);

describe('retiredCandidates — the ported self-withdrawal predicate', () => {
  it('does NOT retire pof before the self-withdrawal (skeptic alone; driller still behind)', () => {
    // Prefix through seq 2: one refuter (skeptic) and the driller still stands behind pof — a live
    // glut, held, exactly as the backend `retiredCandidates(pofGame.take(19))` = ∅.
    expect(retiredCandidates(pofGame.slice(0, 2))).toEqual(asSet([]));
  });

  it('retires pof AT the self-withdrawal (driller withdraws → 2 refuters, none behind)', () => {
    // Prefix through seq 3: every pro-author (the driller) self-withdrew and 2 distinct refuters
    // stand → defeated. Mirrors `retiredCandidates(pofGame.take(20))` = {pof}.
    expect(retiredCandidates(pofGame.slice(0, 3))).toEqual(asSet([pof]));
  });

  it('retires only pof at the full prefix — apple-tree is unrefuted, never retired', () => {
    expect(retiredCandidates(pofGame)).toEqual(asSet([pof]));
  });

  it('a single standing refuter never retires (below the ≥2 floor)', () => {
    // The sole author self-withdraws with ONE refuter total — one lone refutation never retires,
    // mirroring one lone assertion never signing (backend MinRefuters = 2).
    const lone = log([
      { type: 'assert', agentId: x, candidateId: h, content: 'h' },
      { type: 'refute', agentId: x, candidateId: h, note: 'i withdraw' },
    ]);
    expect(retiredCandidates(lone)).toEqual(asSet([]));
  });
});

describe('fold masking mirrors the backend belief', () => {
  it('masks pof out of the belief and leaves apple-tree the clean resolved leader', () => {
    const belief = fold(pofGame, pofGame.length);
    const ids = belief.candidates.map((candidate) => candidate.id);
    // pof is masked — not a live candidate; apple-tree is the only board candidate.
    expect(ids).not.toContain(pof);
    expect(ids).toEqual([apple]);
    const leader = belief.candidates.find(
      (candidate) => candidate.id === apple,
    );
    expect(leader?.corner).toBe('resolved');
    // A retired rival can never read as a live glut (the whole point of the fix).
    expect(
      belief.candidates.some((candidate) => candidate.corner === 'conflict'),
    ).toBe(false);
  });

  it('signs apple-tree at the full prefix — the masked slot clears the gate', () => {
    const belief = fold(pofGame, pofGame.length);
    // apple-tree: two distinct backers, resolved, sole live candidate → signable. pof's defeated con
    // no longer holds it hostage (the payoff the backend `decide` proves).
    expect(belief.cardinality).toBe(1);
    expect(signableCandidate(belief)).toBe(apple);
  });
});

describe('real-glut HOLD — the anti-CWS pin, frontend side', () => {
  it('holds a contested H as a live glut when the asserter stays SILENT (never retired)', () => {
    // X asserts H, Y+Z refute, X SILENT. X never withdrew → its assertion is LIVE support → H is a
    // real glut the fold must HOLD, not mask. This is the exact case self-withdrawal holds and
    // channel-recency would wrongly retire (masking the con, letting a wrong rival sign).
    const contested = log([
      { type: 'assert', agentId: x, candidateId: h, content: 'h' },
      { type: 'refute', agentId: y, candidateId: h, note: 'no' },
      { type: 'refute', agentId: z, candidateId: h, note: 'no' },
    ]);
    expect(retiredCandidates(contested)).toEqual(asSet([]));
    const belief = fold(contested, contested.length);
    const target = belief.candidates.find((candidate) => candidate.id === h);
    // H stays on the board as a Conflict glut — NOT masked away.
    expect(target?.corner).toBe('conflict');
    // And nothing signs around it.
    expect(signableCandidate(belief)).toBeUndefined();
  });
});

describe('replay — scrubbing shows the retirement (recompute-on-read)', () => {
  it('pof is live/glutted before the self-withdrawal and masked after — belief differs correctly', () => {
    // Before the driller's self-withdrawal (playhead 2): pof is a live glut (pro e-1, con e-2),
    // present on the board.
    const before = fold(pofGame, 2);
    const pofBefore = before.candidates.find(
      (candidate) => candidate.id === pof,
    );
    expect(pofBefore?.corner).toBe('conflict');

    // At/after it (playhead 3): pof is retired → masked → absent. apple is not asserted yet, so the
    // board is empty. The two states differ — scrubbing reveals the retirement.
    const after = fold(pofGame, 3);
    expect(after.candidates.map((candidate) => candidate.id)).not.toContain(
      pof,
    );
    expect(after.candidates).toEqual([]);
    expect(after.candidates).not.toEqual(before.candidates);
  });

  it('masking is recomputed at the prefix, not driven by a marker — a retired marker changes nothing', () => {
    // The predicate already masks pof at playhead 3 WITHOUT any `retired` marker in the log — the
    // authority is the recomputed predicate, not the trace. (The marker only mirrors it for the UI.)
    const withoutMarker = fold(pofGame, 3);
    expect(withoutMarker.candidates.map((candidate) => candidate.id)).toEqual(
      [],
    );
  });
});

describe('recovery — a fresh corroboration un-masks the retired candidate', () => {
  it('returns pof to the live belief when a fresh Corroborate lands above the refutations', () => {
    const retiredLog = pofGame.slice(0, 3); // pof retired at seq 3
    expect(retiredCandidates(retiredLog)).toEqual(asSet([pof]));

    // A FRESH agent corroborates pof above the refutations (seq 4 > the driller's e-3). Its latest
    // stance is pro → it stands behind pof → pof is no longer defeated (the predicate recomputes).
    const revived = log([
      {
        type: 'assert',
        agentId: driller,
        candidateId: pof,
        content: 'plant or fungus?',
      },
      { type: 'refute', agentId: skeptic, candidateId: pof, note: 'no' },
      {
        type: 'refute',
        agentId: driller,
        candidateId: pof,
        note: 'i withdraw',
      },
      {
        type: 'corroborate',
        agentId: grower,
        candidateId: pof,
        note: 'i still back it',
      },
    ]);
    expect(retiredCandidates(revived)).toEqual(asSet([]));

    // Un-masked, pof is back on the live board — its evidence folds again (a real glut, con still
    // standing), never silently absent.
    const belief = fold(revived, revived.length);
    const back = belief.candidates.find((candidate) => candidate.id === pof);
    expect(back).toBeDefined();
    expect(back?.corner).toBe('conflict');
  });
});

describe('the lifecycle markers are belief-inert', () => {
  it('appending retired/resurrected markers does not change candidates or corner beyond the mask', () => {
    // A base log with a clean resolved leader (two backers, no con → not retired).
    const base = log([
      { type: 'assert', agentId: x, candidateId: h, content: 'h' },
      { type: 'corroborate', agentId: y, candidateId: h, note: 'agreed' },
    ]);
    const baseState = fold(base, base.length);

    // Append belief-inert markers that do NOT touch the underlying stances (their candidateId is
    // inert — not an assert/refute — so retiredCandidates is unchanged).
    const extended: readonly ReasoningEvent[] = [
      ...base,
      { seq: 3, timestamp: 3, type: 'retired', candidateId: pof },
      { seq: 4, timestamp: 4, type: 'resurrected', candidateId: pof },
    ];
    const extendedState = fold(extended, extended.length);

    expect(extendedState.candidates).toEqual(baseState.candidates);
    expect(extendedState.cardinality).toBe(baseState.cardinality);
    expect(extendedState.gate).toEqual(baseState.gate);
  });
});
