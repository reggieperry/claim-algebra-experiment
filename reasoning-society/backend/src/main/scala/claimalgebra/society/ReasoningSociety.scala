package claimalgebra.society

/** The reasoning-society backend — a claim-algebra-governed society of LLM agents whose every
  * belief traces to its evidence, whose contradictions stay explicit rather than averaged away, and
  * which refuses to sign what it cannot ground.
  *
  * Design of record: `docs/reasoning-society/auditable-society-of-minds-v1.md` (the architecture)
  * and `docs/reasoning-society/twenty-questions-build-brief.md` (the first buildable window — a
  * browser observability tool watching a small society play Twenty Questions).
  *
  * Two critical decisions from the brief map onto already-shipped `claimalgebra` code:
  *   - the belief state is a pure fold over an ordered event log — that fold is the calculus
  *     `Ledger` (`claimalgebra.calculus`), and live mode is replay with the playhead pinned to the
  *     head (one system, not two);
  *   - agents are light actors — address, mailbox, one message at a time, send/create/designate —
  *     with the event log as the global serialization point; the mailbox is a lightweight design
  *     over `cats.effect.std.Queue` (no Akka/Pekko).
  *
  * This module emits the ordered event log; the React observability UI in `../frontend` is a pure
  * viewer of it (the observer gets no vote). Personal research only (brief §7): public/synthetic
  * data, personal accounts.
  *
  * Scaffold: the module, its build wiring, and the standard source tree are in place. Behavior —
  * the event/claim model, the fold, the actor substrate, and the cheap diverse agents — is built in
  * the Build 1 slice (a mock-log observability shell first, per brief §10).
  */
object ReasoningSociety
