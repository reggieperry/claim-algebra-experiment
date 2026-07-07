package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}

/** The mail system, behind one call. Installing an actor mints a fresh inbox, resolves its
  * self-reference, and starts a supervised one-message-at-a-time loop; none of that appears in an
  * actor's code (§5 — the mail system is hidden underneath).
  */
trait ActorSystem:
  /** Install an actor on a fresh inbox and start its loop; return its handle. The `make` factory
    * receives the context (self + spawn) the runtime built.
    */
  def actorOf[M](address: Address)(make: ActorContext[M] => Actor[M]): IO[ActorRef[M]]

object ActorSystem:

  /** A system whose loop fibers are owned by a `Supervisor`, so they are canceled on release — no
    * fiber outlives the resource.
    */
  def resource: Resource[IO, ActorSystem] =
    Supervisor[IO].map(supervisor => new Supervised(supervisor))

  final private class Supervised(supervisor: Supervisor[IO]) extends ActorSystem:

    def actorOf[M](address: Address)(make: ActorContext[M] => Actor[M]): IO[ActorRef[M]] =
      QueueMailbox.make[IO, M](address).flatMap { inbox =>
        val ref = new ActorRef[M](address, (id, message) => inbox.offer(message, id))
        val context = new ActorContext[M]:
          def self: ActorRef[M] = ref
          def spawn[N](childAddress: Address)(
              childMake: ActorContext[N] => Actor[N]
          ): IO[ActorRef[N]] =
            actorOf(childAddress)(childMake)
        supervisor.supervise(loop(inbox, make(context))).as(ref)
      }

  /** The one-at-a-time consumer: take one message, fold `receive` over the actor, commit, recurse
    * on the replacement. The per-message error boundary is the contract that a raised `receive` is
    * contained — it recovers to the current behavior rather than killing the loop fiber. The
    * recursion is `IO`-trampolined, so it is stack-safe; `take` suspends the fiber, so idle costs
    * nothing.
    */
  private def loop[M](inbox: Mailbox[IO, M], actor: Actor[M]): IO[Unit] =
    inbox.take.flatMap { envelope =>
      actor
        .receive(envelope.payload)
        .handleError(_ => actor)
        .flatMap(next => envelope.ack.flatMap(_ => loop(inbox, next)))
    }
