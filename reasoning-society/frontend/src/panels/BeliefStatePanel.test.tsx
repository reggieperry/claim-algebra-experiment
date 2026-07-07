import { render, screen } from '@testing-library/react';

import { fold } from '../fold';
import { MOCK_EVENTS } from '../mock';
import { BeliefStatePanel } from './BeliefStatePanel';

describe('BeliefStatePanel', () => {
  it('shows the four rival hypotheses as a narrowing field at the opening frame', () => {
    render(<BeliefStatePanel state={fold(MOCK_EVENTS, 7)} />);

    expect(
      screen.getByRole('heading', { name: /belief state/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/4 live hypotheses/i)).toBeInTheDocument();
    // A rivalled resolved candidate reads as narrowing, distinct from a glut.
    expect(screen.getAllByText(/rival — narrowing/i).length).toBeGreaterThan(0);
    expect(screen.getByText("It's a dog.")).toBeInTheDocument();
  });

  it('flags the Conflict glut on the contradicted candidate', () => {
    render(<BeliefStatePanel state={fold(MOCK_EVENTS, 24)} />);

    expect(screen.getByText(/conflict — glut/i)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/abstains/i);
  });

  it('shows the gate abstaining while a single hypothesis rests on one voice', () => {
    render(<BeliefStatePanel state={fold(MOCK_EVENTS, 27)} />);

    expect(screen.getByText(/1 live hypothesis/i)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/abstains/i);
  });

  it('marks the signed guess at the finale and reads the gate as SIGNS', () => {
    render(<BeliefStatePanel state={fold(MOCK_EVENTS, MOCK_EVENTS.length)} />);

    expect(screen.getByRole('status')).toHaveTextContent(/signs/i);
    expect(screen.getByText('SIGNED')).toBeInTheDocument();
    expect(screen.getByText("It's a dog.")).toBeInTheDocument();
  });

  it('renders an empty board before any hypothesis is posted', () => {
    render(<BeliefStatePanel state={fold(MOCK_EVENTS, 0)} />);

    expect(
      screen.getByText(/no hypotheses on the board yet/i),
    ).toBeInTheDocument();
  });
});
