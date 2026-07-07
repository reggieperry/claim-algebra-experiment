package claimalgebra.society

import cats.effect.IO
import munit.CatsEffectSuite

/** The build-now mailbox: the validating `Address`/`MessageId` constructors (both sides of the
  * invariant), and the `QueueMailbox` — FIFO delivery, the envelope's address and no-op ack, and
  * the declared contract.
  */
class MailboxSuite extends CatsEffectSuite:

  private def addr(raw: String): Address =
    Address.from(raw).fold(e => fail(s"expected a valid address: $e"), identity)

  private def mid(raw: String): MessageId =
    MessageId.from(raw).fold(e => fail(s"expected a valid message id: $e"), identity)

  test("Address.from rejects a blank label and accepts a real one") {
    assert(Address.from("").isLeft, "empty is rejected")
    assert(Address.from("   ").isLeft, "whitespace-only is rejected")
    assertEquals(Address.from("agent-1").map(_.key), Right("agent-1"))
  }

  test("MessageId.from rejects a blank tag and accepts a real one") {
    assert(MessageId.from("").isLeft, "empty is rejected")
    assert(MessageId.from(" \t ").isLeft, "whitespace-only is rejected")
    assertEquals(MessageId.from("m-1").map(_.value), Right("m-1"))
  }

  test("QueueMailbox delivers offered messages in per-key FIFO order") {
    for
      inbox <- QueueMailbox.make[IO, Int](addr("box"))
      _ <- inbox.offer(1, mid("a"))
      _ <- inbox.offer(2, mid("b"))
      first <- inbox.take
      second <- inbox.take
    yield
      assertEquals(first.payload, 1)
      assertEquals(second.payload, 2)
      assertEquals(first.id, mid("a"))
  }

  test("QueueMailbox envelope carries the mailbox address and a no-op ack") {
    for
      inbox <- QueueMailbox.make[IO, Int](addr("box"))
      _ <- inbox.offer(7, mid("x"))
      envelope <- inbox.take
      _ <- envelope.ack
    yield
      assertEquals(envelope.address, addr("box"))
      assertEquals(envelope.payload, 7)
  }

  test("QueueMailbox reports the build-now-tier contract") {
    QueueMailbox.make[IO, Int](addr("box")).map { inbox =>
      assertEquals(
        inbox.contract,
        MailboxContract(
          Durability.Ephemeral,
          Delivery.AtMostOnce,
          Ordering.PerKeyFifo,
          Replay.NotSupported,
          Flow.Unbounded
        )
      )
    }
  }
