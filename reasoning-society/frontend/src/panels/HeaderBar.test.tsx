import { render, screen } from '@testing-library/react';

import { candidateId, type Candidate, type ConvergenceStatus } from '../model';
import { HeaderBar } from './HeaderBar';

// A distinctive candidate on the board, used to prove the non-convergence flag never names it — the
// backend cannot know WHICH answer is wrong (detect-not-diagnose), so neither can the header.
const petrifiedWood: Candidate = {
  id: candidateId('petrified-wood'),
  content: 'Petrified wood',
  provenance: { supporting: [1], opposing: [] },
  supportingAgents: [],
  corner: 'resolved',
  grade: 0.4,
};

function renderHeader(
  convergence: ConvergenceStatus | undefined,
): ReturnType<typeof render> {
  return render(
    <HeaderBar
      gate={{ kind: 'watching' }}
      candidates={[petrifiedWood]}
      convergence={convergence}
      playhead={40}
      total={71}
      connection="live"
      onNewGame={() => undefined}
      newGamePending={false}
      newGameError={null}
      onFullReset={() => undefined}
      fullResetPending={false}
      fullResetError={null}
    />,
  );
}

describe('HeaderBar convergence flag', () => {
  it('surfaces the human hand-off when a warning is in scope', () => {
    renderHeader({ roundsWithoutConsolidation: 5, glutPersistence: 4 });

    const flag = screen.getByRole('status', { name: /convergence warning/i });
    // The message is the reconsider-an-earlier-answer hand-off (visibility of system status).
    expect(flag).toHaveTextContent(/trouble converging/i);
    expect(flag).toHaveTextContent(/reconsider an earlier answer/i);
    // The structural evidence is shown subtly — the count, no candidate.
    expect(flag).toHaveTextContent(/5 rounds, no candidate consolidating/i);
  });

  it('carries NO candidate name and NO diagnosis of which answer (generic hand-off only)', () => {
    renderHeader({ roundsWithoutConsolidation: 5, glutPersistence: 4 });

    const flag = screen.getByRole('status', { name: /convergence warning/i });
    // detect-not-diagnose: the flag never names the candidate on the board…
    expect(flag).not.toHaveTextContent(/petrified/i);
    // …and never diagnoses which answer is wrong or names a suspect premise.
    expect(flag).not.toHaveTextContent(/wrong/i);
    expect(flag).not.toHaveTextContent(/because/i);
  });

  it('is absent when the search is not flagged as stuck', () => {
    renderHeader(undefined);

    expect(
      screen.queryByRole('status', { name: /convergence warning/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/trouble converging/i)).not.toBeInTheDocument();
  });
});
