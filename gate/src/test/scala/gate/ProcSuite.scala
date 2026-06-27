package gate

import munit.CatsEffectSuite

import java.nio.file.Paths

/** The subprocess seam, against a real, always-present program (`git`). */
class ProcSuite extends CatsEffectSuite:

  private val here = Paths.get(".").toAbsolutePath.normalize

  test("a successful command captures stdout and a zero exit") {
    Proc.run(Seq("git", "--version"), here).map { r =>
      assertEquals(r.exitCode, 0)
      assert(r.stdout.contains("git version"), r.stdout)
    }
  }

  test("a failing command reports a non-zero exit, not an exception") {
    Proc.run(Seq("git", "rev-parse", "--verify", "no-such-ref-zzz"), here).map { r =>
      assertNotEquals(r.exitCode, 0)
    }
  }
