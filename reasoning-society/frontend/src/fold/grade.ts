// The grade function — computed from provenance, never self-reported (brief §3). v1 keeps it
// deliberately simple: support raises the grade with diminishing returns, contradiction dampens it,
// a struck candidate collapses to zero.
//
// Each channel's strength is `1 - 1/(1 + count)`, a bounded, monotone map of an evidence count into
// [0, 1): 0 → 0, 1 → 0.5, 2 → 0.667, 3 → 0.75 … The grade is the pro strength attenuated by the con
// strength, so it stays in [0, 1] and every property below holds.
export function gradeOf(
  proCount: number,
  conCount: number,
  struck: boolean,
): number {
  if (struck) {
    return 0;
  }
  const proStrength = strengthOf(proCount);
  const conStrength = strengthOf(conCount);
  return proStrength * (1 - conStrength);
}

function strengthOf(count: number): number {
  if (count <= 0) {
    return 0;
  }
  return 1 - 1 / (1 + count);
}
