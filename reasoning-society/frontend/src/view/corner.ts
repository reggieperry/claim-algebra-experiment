import type { BelnapCorner } from '../model';

// The Belnap corner as a pre-attentive GLYPH — the redundant, colourblind-safe encoding that rides
// alongside the hue reserved for the corner (build2-ui-design §5 DO: "reserve hue exclusively for
// epistemic corner + redundantly encode with a glyph"). Used by the header legend, the society
// navigator's leading band, and the memory panel, so one corner reads the same everywhere.
export function cornerGlyph(corner: BelnapCorner): string {
  switch (corner) {
    case 'resolved':
      return '⊞';
    case 'conflict':
      return '⊟';
    case 'superseded':
      return '⊘';
    case 'missing':
      return '▫';
  }
}

export function cornerLabel(corner: BelnapCorner): string {
  switch (corner) {
    case 'resolved':
      return 'Resolved';
    case 'conflict':
      return 'Conflict';
    case 'superseded':
      return 'Superseded';
    case 'missing':
      return 'Missing';
  }
}
