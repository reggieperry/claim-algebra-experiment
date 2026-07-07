import fc from 'fast-check';

import { fold, signableCandidate } from '../fold';
import { agentId, candidateId, type BelnapCorner } from '../model';
import { AGENTS, agentName, MOCK_EVENTS } from './scenario';

const dog = candidateId('dog');
const cat = candidateId('cat');
const eagle = candidateId('eagle');
const wolf = candidateId('wolf');

function cornerAt(playhead: number, id: typeof dog): BelnapCorner | undefined {
  return fold(MOCK_EVENTS, playhead).candidates.find(
    (candidate) => candidate.id === id,
  )?.corner;
}

describe('the mock scenario', () => {
  it('is a well-formed log: contiguous 1-based seqs', () => {
    MOCK_EVENTS.forEach((event, index) => {
      expect(event.seq).toBe(index + 1);
    });
  });

  describe('narrowing — the field is pruned as evidence arrives', () => {
    it('opens with four live rival hypotheses (seq 7)', () => {
      const state = fold(MOCK_EVENTS, 7);
      expect(state.cardinality).toBe(4);
      expect(cornerAt(7, dog)).toBe('resolved');
      expect(cornerAt(7, cat)).toBe('resolved');
      expect(cornerAt(7, eagle)).toBe('resolved');
      expect(cornerAt(7, wolf)).toBe('resolved');
    });

    it('supersedes the eagle once a mammal is confirmed (seq 13)', () => {
      expect(cornerAt(13, eagle)).toBe('superseded');
      expect(fold(MOCK_EVENTS, 13).cardinality).toBe(3);
    });

    it('supersedes the wolf once a pet is confirmed, leaving two (seq 18)', () => {
      expect(cornerAt(18, wolf)).toBe('superseded');
      expect(fold(MOCK_EVENTS, 18).cardinality).toBe(2);
    });
  });

  describe('the glut — a contradiction lands on a supported hypothesis', () => {
    it("puts 'cat' into Conflict when barking contradicts it (seq 24)", () => {
      const state = fold(MOCK_EVENTS, 24);
      const catRow = state.candidates.find((candidate) => candidate.id === cat);
      expect(catRow?.corner).toBe('conflict');
      expect(catRow?.provenance.supporting.length).toBeGreaterThan(0);
      expect(catRow?.provenance.opposing.length).toBeGreaterThan(0);
      // A live glut blocks any guess even though only 'dog' is resolved.
      expect(state.cardinality).toBe(1);
      expect(signableCandidate(state)).toBeUndefined();
    });

    it("supersedes 'cat' once it is struck (seq 26)", () => {
      expect(cornerAt(26, cat)).toBe('superseded');
    });
  });

  describe('the gate — abstains, then signs only when the floor is met', () => {
    it('holds while rival hypotheses stand (seq 20)', () => {
      const state = fold(MOCK_EVENTS, 20);
      expect(state.gate.kind).toBe('abstained');
      expect(state.cardinality).toBe(2);
    });

    it("holds when 'dog' stands alone but on a single corroborator (seq 27)", () => {
      const state = fold(MOCK_EVENTS, 27);
      expect(cornerAt(27, dog)).toBe('resolved');
      expect(state.cardinality).toBe(1);
      const dogRow = state.candidates.find((candidate) => candidate.id === dog);
      expect(dogRow?.supportingAgents.length).toBe(1);
      // The no-lone-sign floor is exactly what holds the gate here.
      expect(signableCandidate(state)).toBeUndefined();
      expect(state.gate.kind).toBe('abstained');
    });

    it("becomes signable once 'dog' has two distinct corroborators (seq 31)", () => {
      const state = fold(MOCK_EVENTS, 31);
      const dogRow = state.candidates.find((candidate) => candidate.id === dog);
      expect(dogRow?.supportingAgents.length).toBe(2);
      expect(signableCandidate(state)).toBe(dog);
    });

    it("signs 'dog' at the finale, and the sign is structurally earned (seq 33)", () => {
      const state = fold(MOCK_EVENTS, MOCK_EVENTS.length);
      expect(state.gate).toEqual({
        kind: 'signed',
        candidateId: dog,
        seq: MOCK_EVENTS.length,
      });
      const dogRow = state.candidates.find((candidate) => candidate.id === dog);
      expect(dogRow?.corner).toBe('resolved');
      expect(dogRow?.supportingAgents.length).toBe(3);
      expect(signableCandidate(state)).toBe(dog);
    });

    it('never signs a guess it is not structurally ready to sign (signed ⟹ readable)', () => {
      fc.assert(
        fc.property(fc.nat(MOCK_EVENTS.length), (playhead) => {
          const state = fold(MOCK_EVENTS, playhead);
          // signed ⟹ structurally readable (an unconditional invariant, so it holds vacuously
          // whenever the gate has not signed).
          const honest =
            state.gate.kind !== 'signed' ||
            signableCandidate(state) === state.gate.candidateId;
          expect(honest).toBe(true);
        }),
        { seed: 4242, numRuns: 120 },
      );
    });
  });

  describe('the society roster', () => {
    it('names each agent on the roster', () => {
      expect(AGENTS).toHaveLength(3);
      expect(agentName(agentId('cartographer'))).toBe('Cartographer');
    });

    it('falls back to the raw id for an off-roster agent', () => {
      expect(agentName(agentId('ghost'))).toBe('ghost');
    });
  });
});
