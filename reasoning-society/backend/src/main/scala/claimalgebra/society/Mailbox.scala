package claimalgebra.society

import cats.effect.kernel.Concurrent
import cats.effect.std.Queue
import cats.syntax.functor.*

/** A routing address — Agha's mail target. A label the delivery layer keys on; for the durable tier
  * it becomes the partition key so one address keeps per-address arrival order. Opaque over a
  * `String` behind a validating constructor: a blank label names no target, so it is refused.
  */
opaque type Address = String

object Address:
  /** The only constructor. Fail closed: a blank (or whitespace-only) label is rejected rather than
    * admitted as a nameless address. Insignificant surrounding whitespace is trimmed.
    */
  def from(raw: String): Either[String, Address] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Left("address must be a non-blank label")
    else Right(trimmed)

  extension (a: Address) def key: String = a

/** Message identity — Agha's tag, and the dedup key an at-least-once backing needs. The caller
  * mints it and threads it through the send; the Queue backing does not dedup on it (that arrives
  * with the durable tier). Distinct from the algebra's `Lineage`, which lives inside the payload.
  * Opaque over a `String` behind a validating constructor.
  */
opaque type MessageId = String

object MessageId:
  /** The only constructor. Fail closed: a blank tag identifies nothing, so it is refused. */
  def from(raw: String): Either[String, MessageId] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Left("message id must be a non-blank tag")
    else Right(trimmed)

  extension (m: MessageId) def value: String = m

/** The guarantee axes a backing declares. Each is a closed enum so a backing swap is a legible
  * change of named values rather than a silent behavior shift.
  */
enum Durability:
  case Ephemeral, DurableReplayable

enum Delivery:
  case AtMostOnce, AtLeastOnce

enum Ordering:
  case PerKeyFifo, Unordered

enum Replay:
  case NotSupported, FromOffset

enum Flow:
  case Unbounded, BackpressuredBounded

/** What a backing promises, named. Advisory — nothing branches on it at runtime; it documents the
  * construction site and gives a config assertion a place to land.
  */
final case class MailboxContract(
    durability: Durability,
    delivery: Delivery,
    ordering: Ordering,
    replay: Replay,
    flow: Flow
)

/** One delivered message. `ack` confirms the consumer processed it — strictly stronger than "the
  * mail system delivered it," which already happened when this value was taken. On the Queue
  * backing `ack` is a no-op (`take` already removed it); on a durable backing it advances the read
  * cursor. `A` must be immutable (a Queue shares heap references).
  */
final case class Envelope[F[_], A](
    id: MessageId,
    address: Address,
    payload: A,
    ack: F[Unit]
)

/** A per-target inbox: the intersection of what a Queue and a durable partition can both honestly
  * provide — a buffered `offer`, an in-order `take`, and a per-message `ack`. Deliberately no peek,
  * no reordering, no selective ack: a richer surface would make a durable backing a liar. `offer`
  * completing means buffered, not processed; `take` yields the next message in arrival order.
  */
trait Mailbox[F[_], A]:
  def address: Address
  def offer(message: A, id: MessageId): F[Unit]
  def take: F[Envelope[F, A]]
  def contract: MailboxContract

/** The build-now backing over `cats.effect.std.Queue`: ephemeral, at-most-once, per-key FIFO,
  * unbounded, with a no-op `ack`. A `take` recursion drives the consumer loop — no fs2.
  */
final class QueueMailbox[F[_]: Concurrent, A] private (
    val address: Address,
    queue: Queue[F, Envelope[F, A]]
) extends Mailbox[F, A]:

  def offer(message: A, id: MessageId): F[Unit] =
    queue.offer(Envelope(id, address, message, Concurrent[F].unit))

  def take: F[Envelope[F, A]] = queue.take

  val contract: MailboxContract =
    MailboxContract(
      Durability.Ephemeral,
      Delivery.AtMostOnce,
      Ordering.PerKeyFifo,
      Replay.NotSupported,
      Flow.Unbounded
    )

object QueueMailbox:
  def make[F[_]: Concurrent, A](address: Address): F[Mailbox[F, A]] =
    Queue.unbounded[F, Envelope[F, A]].map(new QueueMailbox(address, _))
