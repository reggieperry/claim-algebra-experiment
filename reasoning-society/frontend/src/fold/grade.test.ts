import fc from 'fast-check';

import { gradeOf } from './grade';

const count = fc.integer({ min: 0, max: 200 });
const positive = fc.integer({ min: 1, max: 200 });

describe('gradeOf', () => {
  it('collapses a struck candidate to zero regardless of support', () => {
    fc.assert(
      fc.property(count, count, (pro, con) => {
        expect(gradeOf(pro, con, true)).toBe(0);
      }),
      { seed: 4242, numRuns: 300 },
    );
  });

  it('is zero with no evidence at all', () => {
    expect(gradeOf(0, 0, false)).toBe(0);
  });

  it('stays within [0, 1] for any evidence', () => {
    fc.assert(
      fc.property(count, count, fc.boolean(), (pro, con, struck) => {
        const grade = gradeOf(pro, con, struck);
        expect(grade).toBeGreaterThanOrEqual(0);
        expect(grade).toBeLessThanOrEqual(1);
      }),
      { seed: 4242, numRuns: 500 },
    );
  });

  it('rises strictly with an added corroboration when con is held fixed', () => {
    fc.assert(
      fc.property(count, count, (pro, con) => {
        expect(gradeOf(pro + 1, con, false)).toBeGreaterThan(
          gradeOf(pro, con, false),
        );
      }),
      { seed: 4242, numRuns: 500 },
    );
  });

  it('falls strictly with an added contradiction when a candidate has support', () => {
    fc.assert(
      fc.property(positive, count, (pro, con) => {
        expect(gradeOf(pro, con + 1, false)).toBeLessThan(
          gradeOf(pro, con, false),
        );
      }),
      { seed: 4242, numRuns: 500 },
    );
  });

  it('pins the diminishing-returns support ladder', () => {
    expect(gradeOf(1, 0, false)).toBeCloseTo(0.5, 10);
    expect(gradeOf(2, 0, false)).toBeCloseTo(2 / 3, 10);
    expect(gradeOf(3, 0, false)).toBeCloseTo(0.75, 10);
  });
});
