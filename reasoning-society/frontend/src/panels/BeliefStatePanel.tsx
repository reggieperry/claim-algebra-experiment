import type { ReactElement } from 'react';

import type { BeliefState, Candidate, GateDecision } from '../model';
import { formatPercent, toneLabel, toneOf, type Tone } from '../view/tone';

interface BeliefStatePanelProps {
  readonly state: BeliefState;
}

// The centerpiece (brief §4): the live candidate hypotheses, each a row with an SVG grade bar and a
// Belnap-corner colour, sorted by grade. A PURE view of the fold — it stores nothing, it reads
// `state` and renders. Motion (the bar widths, the colour) is CSS-driven off the values, so a
// re-fold at a new playhead animates the change.
export function BeliefStatePanel({
  state,
}: BeliefStatePanelProps): ReactElement {
  const signedId =
    state.gate.kind === 'signed' ? state.gate.candidateId : undefined;

  return (
    <section className="panel panel--belief" aria-label="Belief state">
      <header className="panel__head">
        <h2 className="panel__title">Belief state</h2>
        <p className="panel__sub">
          {state.cardinality} live{' '}
          {state.cardinality === 1 ? 'hypothesis' : 'hypotheses'}
        </p>
      </header>

      <p className={`gate gate--${state.gate.kind}`} role="status">
        {gateText(state.gate, state.candidates)}
      </p>

      {state.candidates.length === 0 ? (
        <p className="panel__empty">No hypotheses on the board yet.</p>
      ) : (
        <ul className="belief-list">
          {state.candidates.map((candidate) => (
            <CandidateRow
              key={candidate.id}
              candidate={candidate}
              cardinality={state.cardinality}
              signed={candidate.id === signedId}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

interface CandidateRowProps {
  readonly candidate: Candidate;
  readonly cardinality: number;
  readonly signed: boolean;
}

function CandidateRow({
  candidate,
  cardinality,
  signed,
}: CandidateRowProps): ReactElement {
  const tone = toneOf(candidate, cardinality);
  const percent = formatPercent(candidate.grade);
  return (
    <li
      className={`belief-row belief-row--${tone}${signed ? ' belief-row--signed' : ''}`}
    >
      <div className="belief-row__head">
        <span className="belief-row__name">{candidate.content}</span>
        <span className="belief-row__badge">{toneLabel(tone)}</span>
      </div>
      <GradeBar grade={candidate.grade} tone={tone} label={candidate.content} />
      <div className="belief-row__meta">
        <span className="belief-row__grade">{percent}</span>
        <span>
          {candidate.supportingAgents.length}{' '}
          {candidate.supportingAgents.length === 1 ? 'agent' : 'agents'}
        </span>
        <span className="belief-row__prov">
          +{candidate.provenance.supporting.length} / −
          {candidate.provenance.opposing.length}
        </span>
        {signed ? <span className="belief-row__sign">SIGNED</span> : null}
      </div>
    </li>
  );
}

interface GradeBarProps {
  readonly grade: number;
  readonly tone: Tone;
  readonly label: string;
}

// An SVG bar in a 0..100 track; the fill width is the grade. The width is a CSS-transitioned
// geometry property, so a grade change slides rather than jumps (brief §8: motion encodes state).
function GradeBar({ grade, tone, label }: GradeBarProps): ReactElement {
  const width = Math.max(0, Math.min(100, grade * 100));
  return (
    <svg
      className="grade-bar"
      viewBox="0 0 100 6"
      preserveAspectRatio="none"
      role="img"
      aria-label={`${label} grade ${formatPercent(grade)}`}
    >
      <rect className="grade-bar__track" x="0" y="0" width="100" height="6" />
      <rect
        className={`grade-bar__fill grade-bar__fill--${tone}`}
        x="0"
        y="0"
        width={width}
        height="6"
      />
    </svg>
  );
}

function gateText(
  gate: GateDecision,
  candidates: readonly Candidate[],
): string {
  switch (gate.kind) {
    case 'watching':
      return 'Gate: watching — no decision yet.';
    case 'abstained':
      return `Gate abstains — ${gate.reason}`;
    case 'signed': {
      const signed = candidates.find(
        (candidate) => candidate.id === gate.candidateId,
      );
      const name = signed === undefined ? gate.candidateId : signed.content;
      return `Gate SIGNS — ${name}`;
    }
    default:
      return assertNever(gate);
  }
}

function assertNever(x: never): never {
  throw new Error(`unhandled gate: ${JSON.stringify(x)}`);
}
