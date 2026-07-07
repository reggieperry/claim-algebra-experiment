// The log-export contract: the two pure formatters and the effectful download. Import export through this
// surface, never a deep path into a member file (ts-modules).
export { toTranscript, toJson } from './logExport';
export { downloadTextFile } from './downloadFile';
