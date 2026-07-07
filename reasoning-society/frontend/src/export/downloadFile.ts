// The thin effectful shell that carries a formatted string to the user's disk — the client-side download
// with no backend and no storage. It wraps the content in a `Blob`, mints an object URL, clicks a transient
// `<a download>`, then revokes the URL. Kept apart from the pure formatters (`logExport.ts`) so those stay
// deterministic and unit-testable; this is the DOM boundary, exercised through the panel's click handler.
export function downloadTextFile(
  content: string,
  filename: string,
  mimeType: string,
): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  // Some engines only fire a synthetic click on an anchor that is in the document.
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
