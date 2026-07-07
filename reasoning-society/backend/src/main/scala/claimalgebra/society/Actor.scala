package claimalgebra.society

import cats.effect.IO

/** An unforgeable handle to another actor — the only thing you ever hold of one. You may tell it a
  * message; you cannot read its state or its inbox (Hewitt's locality: influence only by message).
  * Invariant in `M` — contravariance is unearned until a widening call site exists.
  */
final class ActorRef[M] private[society] (
    val address: Address,
    deliver: (MessageId, M) => IO[Unit]
):
  /** The one cross-actor operation: buffer a message for the target. The caller mints the message's
    * id (the dedup tag a durable backing will need) and threads it through.
    */
  def tell(id: MessageId, message: M): IO[Unit] = deliver(id, message)

/** What the runtime grants a running actor: its own handle, and the power to create actors.
  * Supplied when an actor (or its replacement) is installed, which resolves the self-reference.
  */
trait ActorContext[M]:
  def self: ActorRef[M]
  def spawn[N](address: Address)(make: ActorContext[N] => Actor[N]): IO[ActorRef[N]]

/** An actor: an addressable entity whose entire vocabulary is
  *   - `receive` — handle one message, returning its replacement behavior;
  *   - `send` — tell another actor;
  *   - `create` — make a new actor;
  *   - `self` — its own handle.
  *
  * There is deliberately no shared-state primitive and no side channel, so the type makes it
  * structurally true that all communication is actor-to-actor (Hewitt's sole primitive, Agha's
  * message-passing-only). An actor can name another only by a handle it was told, was constructed
  * holding, or got from `create` — the three ways an address becomes known. There is no directory
  * and no forging.
  */
abstract class Actor[M](protected val context: ActorContext[M]):

  /** The behavior — the work. One message in; the replacement behavior out (Agha's replacement
    * behavior, Hewitt's designating the behavior for the next message). The runtime runs it one
    * message at a time.
    */
  def receive(message: M): IO[Actor[M]]

  /** The sole cross-actor operation. Asynchronous; no return; no ambient sender — to get a reply,
    * put a handle (usually `self`) inside the message and let the peer tell it back. The caller
    * mints the message's stable id.
    */
  final protected def send[N](to: ActorRef[N], id: MessageId, message: N): IO[Unit] =
    to.tell(id, message)

  /** Create a new actor at the given address; the runtime supplies its context. */
  final protected def create[N](address: Address)(
      make: ActorContext[N] => Actor[N]
  ): IO[ActorRef[N]] =
    context.spawn(address)(make)

  final protected def self: ActorRef[M] = context.self

  /** Designate no change — keep the current behavior. */
  final protected def unchanged: IO[Actor[M]] = IO.pure(this)
