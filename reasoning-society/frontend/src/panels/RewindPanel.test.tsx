import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { AnsweredQuestion } from '../fold';
import { RewindPanel } from './RewindPanel';

const answers: readonly AnsweredQuestion[] = [
  { seq: 2, content: 'Is it alive?', answer: 'no' },
  { seq: 4, content: 'Is it a fruit?', answer: 'yes' },
];

describe('RewindPanel', () => {
  it('is inert until the convergence flag fires', () => {
    const { container } = render(
      <RewindPanel
        answers={answers}
        flagged={false}
        live
        onRewind={() => undefined}
        error={null}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('is inert offline — a rewind re-forks the backend game', () => {
    const { container } = render(
      <RewindPanel
        answers={answers}
        flagged
        live={false}
        onRewind={() => undefined}
        error={null}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('surfaces the human answers with rewind buttons and reports the chosen seq', async () => {
    const user = userEvent.setup();
    const onRewind = vi.fn<(seq: number) => void>();
    render(
      <RewindPanel
        answers={answers}
        flagged
        live
        onRewind={onRewind}
        error={null}
      />,
    );

    expect(screen.getByText('Is it alive?')).toBeInTheDocument();
    expect(screen.getByText('Is it a fruit?')).toBeInTheDocument();
    expect(
      screen.getAllByRole('button', { name: /rewind to before/i }),
    ).toHaveLength(2);

    await user.click(
      screen.getByRole('button', {
        name: /rewind to before "is it a fruit\?"/i,
      }),
    );
    expect(onRewind).toHaveBeenCalledWith(4);
  });

  it('does not name a culprit — no answer is marked wrong (non-generative)', () => {
    render(
      <RewindPanel
        answers={answers}
        flagged
        live
        onRewind={() => undefined}
        error={null}
      />,
    );
    expect(
      screen.queryByText(/wrong|culprit|problem answer|likely the/i),
    ).not.toBeInTheDocument();
  });
});
