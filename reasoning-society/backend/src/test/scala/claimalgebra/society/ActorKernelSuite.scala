package claimalgebra.society

import cats.effect.syntax.all.*
import cats.effect.{Deferred, IO}
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

import scala.concurrent.duration.*

/** The actor kernel over the mail system: send/receive, `become` as the returned replacement,
  * `create` and child communication, single-consumer serialization, and the per-message error
  * boundary. All effectful tests are synchronized with `Deferred`/`Ref` and bounded by timeouts —
  * no sleeps.
  */
class ActorKernelSuite extends CatsEffectSuite:

  private def addr(raw: String): Address =
    Address.from(raw).fold(e => fail(s"bad address: $e"), identity)

  private def mid(raw: String): MessageId =
    MessageId.from(raw).fold(e => fail(s"bad message id: $e"), identity)

  // --- test actors ---

  /** Completes a probe with the one message it is told. */
  final private class Echo(context: ActorContext[Int], probe: Deferred[IO, Int])
      extends Actor[Int](context):
    def receive(message: Int): IO[Actor[Int]] = probe.complete(message).as(this)

  private enum Count:
    case Add(n: Int)
    case Boom
    case Report(probe: Deferred[IO, Int])

  /** Accumulates a running total in its `become`-state; `Boom` raises (the error-boundary probe);
    * `Report` answers with the total.
    */
  final private class Counter(context: ActorContext[Count], total: Int)
      extends Actor[Count](context):
    def receive(message: Count): IO[Actor[Count]] = message match
      case Count.Add(n) => IO.pure(new Counter(context, total + n))
      case Count.Boom => IO.raiseError[Actor[Count]](new RuntimeException("boom"))
      case Count.Report(probe) => probe.complete(total).as(this)

  private enum ToChild:
    case Ping(text: String)

  final private class Child(context: ActorContext[ToChild], probe: Deferred[IO, String])
      extends Actor[ToChild](context):
    def receive(message: ToChild): IO[Actor[ToChild]] = message match
      case ToChild.Ping(text) => probe.complete(text).as(this)

  private enum ToParent:
    case Begin

  /** On `Begin`, creates a child and tells it — exercising `create` plus actor-to-actor `send`. */
  final private class Parent(
      context: ActorContext[ToParent],
      probe: Deferred[IO, String],
      childAddress: Address,
      pingId: MessageId
  ) extends Actor[ToParent](context):
    def receive(message: ToParent): IO[Actor[ToParent]] = message match
      case ToParent.Begin =>
        create(childAddress)(childContext => new Child(childContext, probe))
          .flatMap(child => send(child, pingId, ToChild.Ping("hello")))
          .as(this)

  // --- tests ---

  test("an actor handles a message told to it") {
    ActorSystem.resource.use { system =>
      for
        probe <- Deferred[IO, Int]
        ref <- system.actorOf(addr("echo"))(context => new Echo(context, probe))
        _ <- ref.tell(mid("m1"), 42)
        got <- probe.get.timeout(5.seconds)
      yield assertEquals(got, 42)
    }
  }

  test("become accumulates state — the final total equals the pure fold of the inputs") {
    val samples: List[List[Int]] =
      val gen = Gen.listOf(Gen.choose(-1000, 1000))
      val seed = Seed(20260706L)
      (0 until 60).toList
        .map(i => gen.pureApply(Gen.Parameters.default.withSize(40), seed.reseed(i.toLong)))

    ActorSystem.resource.use { system =>
      samples.zipWithIndex.traverse_ { case (input, i) =>
        for
          probe <- Deferred[IO, Int]
          ref <- system.actorOf(addr(s"counter-$i"))(context => new Counter(context, 0))
          _ <- input.zipWithIndex.traverse_ { case (n, j) =>
            ref.tell(mid(s"c$i-add$j"), Count.Add(n))
          }
          _ <- ref.tell(mid(s"c$i-report"), Count.Report(probe))
          got <- probe.get.timeout(5.seconds)
        yield assertEquals(got, input.sum)
      }
    }
  }

  test("an actor spawns a child and they communicate") {
    ActorSystem.resource.use { system =>
      for
        probe <- Deferred[IO, String]
        parent <- system.actorOf(addr("parent"))(context =>
          new Parent(context, probe, addr("parent/child"), mid("ping"))
        )
        _ <- parent.tell(mid("begin"), ToParent.Begin)
        got <- probe.get.timeout(5.seconds)
      yield assertEquals(got, "hello")
    }
  }

  test("concurrent tells are processed one at a time — no lost updates") {
    val n = 500
    ActorSystem.resource.use { system =>
      for
        probe <- Deferred[IO, Int]
        ref <- system.actorOf(addr("serial"))(context => new Counter(context, 0))
        _ <- (1 to n).toList.parTraverseN(32)(i => ref.tell(mid(s"add-$i"), Count.Add(1)))
        _ <- ref.tell(mid("report"), Count.Report(probe))
        got <- probe.get.timeout(10.seconds)
      yield assertEquals(got, n)
    }
  }

  test("a receive that raises does not kill the actor — later messages still process") {
    ActorSystem.resource.use { system =>
      for
        probe <- Deferred[IO, Int]
        ref <- system.actorOf(addr("resilient"))(context => new Counter(context, 0))
        _ <- ref.tell(mid("e1"), Count.Add(1))
        _ <- ref.tell(mid("e2"), Count.Boom)
        _ <- ref.tell(mid("e3"), Count.Add(1))
        _ <- ref.tell(mid("e4"), Count.Report(probe))
        got <- probe.get.timeout(5.seconds)
      yield assertEquals(got, 2)
    }
  }
