// The live-transport surface — the SSE event source hook and the answer POST (ts-modules: the index is
// the contract; the browser talks to the backend only through these two seams).
export { useLiveEvents } from './useLiveEvents';
export type { LiveEvents, LiveStatus } from './useLiveEvents';
export { postAnswer } from './postAnswer';
export type { PostAnswerOptions } from './postAnswer';
export { postChallenge } from './postChallenge';
export type { PostChallengeOptions } from './postChallenge';
export { postStart } from './postStart';
export type { PostStartOptions } from './postStart';
