package gate

import cats.effect.IO

import java.nio.file.Path
import scala.concurrent.duration.*
import scala.sys.process.{Process, ProcessLogger}

/** The captured result of a subprocess run. */
final case class ProcResult(exitCode: Int, stdout: String, stderr: String)

/** A thin subprocess seam: run a command built from a `Seq` (never a shell string, so an argument's
  * metacharacters are inert — scala-security.md), in a working directory, on the blocking pool with
  * a timeout (scala-concurrency.md). The program name must be a fixed constant; arguments that come
  * from outside are validated by the caller before they reach here.
  */
object Proc:

  /** Run `command` (program first, then arguments) in `cwd`, capturing stdout, stderr, and the exit
    * code. Blocks a pool thread, so it is lifted with `IO.blocking` and bounded by `timeout`.
    */
  def run(command: Seq[String], cwd: Path, timeout: FiniteDuration = 5.minutes): IO[ProcResult] =
    IO.blocking {
      val out = new StringBuilder
      val err = new StringBuilder
      def collect(sb: StringBuilder)(line: String): Unit =
        sb.append(line).append('\n')
        ()
      val logger = ProcessLogger(collect(out), collect(err))
      val exit = Process(command, cwd.toFile).!(logger)
      ProcResult(exit, out.toString, err.toString)
    }.timeout(timeout)
