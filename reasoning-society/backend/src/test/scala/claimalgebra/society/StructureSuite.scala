package claimalgebra.society

import java.nio.file.{Files, Path, Paths}

/** Mail-system hiding as a usage check: the `Actor` abstraction names none of the delivery
  * machinery. The kernel's vocabulary is entirely actor-targeted, so an actor cannot reach the
  * inbox, the loop, or the supervisor — all communication is actor-to-actor by construction (§3).
  */
class StructureSuite extends munit.FunSuite:

  private def ancestors(p: Path): LazyList[Path] =
    p #:: Option(p.getParent).map(ancestors).getOrElse(LazyList.empty)

  private def actorSource: Path =
    val relative = "reasoning-society/backend/src/main/scala/claimalgebra/society/Actor.scala"
    ancestors(Paths.get("").toAbsolutePath)
      .map(_.resolve(relative))
      .find(Files.exists(_))
      .getOrElse(fail(s"could not locate Actor.scala from ${Paths.get("").toAbsolutePath}"))

  test("the Actor abstraction names no mail-system machinery") {
    val source = Files.readString(actorSource)
    List("Mailbox", "Queue", "Supervisor", "Envelope").foreach { term =>
      assert(!source.contains(term), s"Actor.scala leaks the mail-system type `$term`")
    }
  }
