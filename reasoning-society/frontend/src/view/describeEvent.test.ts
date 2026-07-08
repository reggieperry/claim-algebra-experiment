import { describeEvent } from './describeEvent';
import { candidateId, type ReasoningEvent } from '../model';

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

// B1 (recovery-and-endgame): the society's guess to the oracle renders as the question and the reply,
// attributed to the Gate (the committing voice) — never to an agent.
describe('describeEvent — guess_answered', () => {
  const guess = (answer: 'yes' | 'no' | 'unknown'): ReasoningEvent => ({
    type: 'guess_answered',
    candidateId: candidateId('salt'),
    answer,
    seq: 1,
    timestamp: 1,
  });

  const noAgent = (): string => 'unused';

  it('renders the guess with a CONFIRMED verdict on yes, attributed to the Gate', () => {
    const line = describeEvent(guess('yes'), noAgent);
    expect(line.actor).toBe('Gate');
    expect(line.verb).toBe('guesses');
    expect(line.detail).toBe('is it "salt"? — CONFIRMED');
  });

  it('renders a declined verdict on no, and an unknown verdict on unknown', () => {
    expect(describeEvent(guess('no'), noAgent).detail).toBe(
      'is it "salt"? — declined',
    );
    expect(describeEvent(guess('unknown'), noAgent).detail).toBe(
      'is it "salt"? — unknown',
    );
  });
});
