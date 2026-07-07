import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import type { ReasoningEvent } from '../model';
import { agentId, candidateId } from '../model';
import { TransportPanel } from './TransportPanel';

function handlers() {
  return {
    onSeek: vi.fn<(playhead: number) => void>(),
    onTogglePlay: vi.fn<() => void>(),
    onStep: vi.fn<() => void>(),
    onStepBack: vi.fn<() => void>(),
    onSpeed: vi.fn<(speed: number) => void>(),
  };
}

describe('TransportPanel', () => {
  it('reads out the playhead position and marks replay versus live', () => {
    const h = handlers();
    render(
      <TransportPanel
        playhead={12}
        total={33}
        playing={false}
        speed={1}
        atHead={false}
        {...h}
      />,
    );

    expect(screen.getByText('12 / 33')).toBeInTheDocument();
    expect(screen.getByText(/replay/i)).toBeInTheDocument();
  });

  it('shows LIVE and disables the forward step at the head', () => {
    const h = handlers();
    render(
      <TransportPanel
        playhead={33}
        total={33}
        playing
        speed={1}
        atHead
        {...h}
      />,
    );

    expect(screen.getByText(/live/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
  });

  it('disables the back step at the start', () => {
    const h = handlers();
    render(
      <TransportPanel
        playhead={0}
        total={33}
        playing={false}
        speed={1}
        atHead={false}
        {...h}
      />,
    );

    expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled();
  });

  it('reports play, step, and back gestures to the App', async () => {
    const user = userEvent.setup();
    const h = handlers();
    render(
      <TransportPanel
        playhead={5}
        total={33}
        playing={false}
        speed={1}
        atHead={false}
        {...h}
      />,
    );

    await user.click(screen.getByRole('button', { name: /play/i }));
    await user.click(screen.getByRole('button', { name: /next/i }));
    await user.click(screen.getByRole('button', { name: /prev/i }));

    expect(h.onTogglePlay).toHaveBeenCalledOnce();
    expect(h.onStep).toHaveBeenCalledOnce();
    expect(h.onStepBack).toHaveBeenCalledOnce();
  });

  it('reports a speed change', async () => {
    const user = userEvent.setup();
    const h = handlers();
    render(
      <TransportPanel
        playhead={5}
        total={33}
        playing={false}
        speed={1}
        atHead={false}
        {...h}
      />,
    );

    await user.selectOptions(screen.getByLabelText(/speed/i), '2');

    expect(h.onSpeed).toHaveBeenCalledWith(2);
  });

  it('renders the timeline slider bound to the playhead and total', () => {
    const h = handlers();
    render(
      <TransportPanel
        playhead={5}
        total={33}
        playing={false}
        speed={1}
        atHead={false}
        {...h}
      />,
    );

    const slider = screen.getByRole('slider', { name: /timeline/i });
    expect(slider).toHaveValue('5');
    expect(slider).toHaveAttribute('max', '33');
  });

  it('seeks when a corner-coloured histogram tick is clicked', async () => {
    const user = userEvent.setup();
    const h = handlers();
    const events: readonly ReasoningEvent[] = [
      {
        type: 'assert',
        agentId: agentId('a1'),
        candidateId: candidateId('c1'),
        content: 'dog',
        seq: 1,
        timestamp: 1_000,
      },
      {
        type: 'refute',
        agentId: agentId('a2'),
        candidateId: candidateId('c1'),
        note: 'no',
        seq: 2,
        timestamp: 2_000,
      },
    ];
    render(
      <TransportPanel
        playhead={0}
        total={2}
        playing={false}
        speed={1}
        atHead={false}
        events={events}
        {...h}
      />,
    );

    await user.click(screen.getByRole('button', { name: /seek to event 2/i }));
    expect(h.onSeek).toHaveBeenCalledWith(2);
  });
});
