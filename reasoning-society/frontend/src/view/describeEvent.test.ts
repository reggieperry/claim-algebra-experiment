import { describeEvent } from './describeEvent';
import type { ReasoningEvent } from '../model';

// A1 (recovery-and-endgame): the librarian's non-convergence flag renders STRUCTURALLY and must not
// leak the raw glut counter. The header hand-off already reads "no candidate consolidating"; the
// event-stream line must likewise say "N rounds without a consolidating candidate" and only mention a
// contested candidate when the glut count is positive — never the meaningless "glut persisting 0".
describe('describeEvent — convergence_warning', () => {
  const warning = (
    roundsWithoutConsolidation: number,
    glutPersistence: number,
  ): ReasoningEvent => ({
    type: 'convergence_warning',
    roundsWithoutConsolidation,
    glutPersistence,
    seq: 1,
    timestamp: 1,
  });

  const noAgent = (): string => 'unused';

  it('is attributed to the Librarian and renders the rounds structurally', () => {
    const line = describeEvent(warning(11, 0), noAgent);
    expect(line.actor).toBe('Librarian');
    expect(line.verb).toBe('flags');
    expect(line.detail).toBe(
      'search not converging — 11 rounds without a consolidating candidate',
    );
  });

  it('omits the glut clause entirely when the glut count is zero', () => {
    const line = describeEvent(warning(11, 0), noAgent);
    expect(line.detail).not.toContain('glut');
    expect(line.detail).not.toContain('persisting');
  });

  it('appends the contested-candidate clause only when the glut count is positive', () => {
    const line = describeEvent(warning(5, 4), noAgent);
    expect(line.detail).toBe(
      'search not converging — 5 rounds without a consolidating candidate, one contested candidate held for 4 rounds',
    );
  });
});
