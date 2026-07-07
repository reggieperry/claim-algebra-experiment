import { render, screen } from '@testing-library/react';

import { App } from './App';

describe('App', () => {
  it('renders the Reasoning Society heading', () => {
    render(<App />);

    expect(
      screen.getByRole('heading', { name: /reasoning society/i }),
    ).toBeInTheDocument();
  });

  it('describes the not-yet-wired instrument shell', () => {
    render(<App />);

    expect(
      screen.getByText(/instrument shell not yet wired/i),
    ).toBeInTheDocument();
  });
});
