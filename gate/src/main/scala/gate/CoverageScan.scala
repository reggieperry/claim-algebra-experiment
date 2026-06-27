package gate

import cats.effect.{IO, Resource}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** The scoverage statement-coverage source for Check D's coverage-drop block (and, later, Check E's
  * integration coverage). scoverage writes `scoverage.xml` per package; the pure [[parse]] folds
  * its `<class>` elements into a per-package-directory coverage map, and the thin [[findings]]
  * adapter runs the instrumented build and reads the report — mirroring the [[ScalafixScan]]
  * pattern.
  *
  * Coverage is heavy (an instrumented clean + a full test run on each tree), so the runner uses it
  * only behind a flag; with it off, the snapshot's coverage stays empty and Check D's coverage
  * block is inert.
  */
object CoverageScan:

  /** Parse a `scoverage.xml` into a `package-directory -> statement-coverage%` map. The directory
    * is keyed REPO-RELATIVE (the source root prefixed) so it shares the path-space of the diff's
    * file renames — a renamed package then reconciles through `translatePkg` instead of
    * false-positiving as a coverage drop. The percent is recomputed from the integer counts (the
    * rendered `statement-rate` is locale-sensitive); a package with zero statements is dropped,
    * never minted as 100%.
    */
  def parse(xml: String, sourceRoot: String = "src/main/scala"): Map[String, Double] =
    val doc = secureBuilder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
    val nodes = doc.getElementsByTagName("class")
    val rows =
      (0 until nodes.getLength).iterator
        .map(nodes.item)
        .flatMap { node =>
          val attrs = Option(node.getAttributes)
          def attr(name: String): Option[String] =
            attrs.flatMap(a => Option(a.getNamedItem(name))).map(_.getNodeValue)
          for
            filename <- attr("filename").map(_.replace('\\', '/')).filter(_.nonEmpty)
            count <- attr("statement-count").flatMap(_.toIntOption)
            invoked <- attr("statements-invoked").flatMap(_.toIntOption)
          yield (s"$sourceRoot/${dirOf(filename)}", count, invoked)
        }
        .toList
    rows
      .groupBy(_._1)
      .flatMap { case (dir, group) =>
        val total = group.map(_._2).sum
        val hit = group.map(_._3).sum
        Option.when(total > 0)(dir -> (hit.toDouble / total.toDouble * 100.0))
      }

  /** Run scoverage over the tree at `repo` and read its report into the coverage map. FAIL-CLOSED:
    * if the instrumented run exits non-zero AND produces no report, raise (an operational failure,
    * exit 2) rather than returning an empty map — a failed scan must not be read as "no coverage to
    * check", which would silently disable Check D's coverage-drop block on that tree (most acutely
    * a merge-base predating sbt-scoverage, but also any baseline flake). A clean exit with no
    * report is the only legitimate empty (nothing instrumented). The `clean` is required — the
    * `coverage` switch changes scalacOptions to instrument, and a stale non-instrumented compile
    * would otherwise be reused and report the wrong numbers.
    */
  def findings(repo: Path): IO[Map[String, Double]] =
    Proc
      .run(Seq("sbt", "-batch", "clean", "coverage", "test", "coverageReport"), repo, 20.minutes)
      .flatMap { result =>
        reportXml(repo).flatMap {
          case Some(path) => IO.blocking(Files.readString(path)).map(xml => parse(xml))
          case None if result.exitCode != 0 =>
            IO.raiseError(
              new RuntimeException(
                s"coverage scan did not complete (sbt exit ${result.exitCode}) and produced no report" +
                  " — refusing to read coverage as empty (fail-closed)"
              )
            )
          case None => IO.pure(Map.empty)
        }
      }

  /** The root project's scoverage.xml under the target scala-version scoverage-report directory,
    * located by walking the tree (not a hardcoded path) so a Scala-version bump does not break it.
    */
  private def reportXml(repo: Path): IO[Option[Path]] =
    val target = repo.resolve("target")
    IO.blocking(Files.isDirectory(target)).flatMap {
      case false => IO.pure(None)
      case true =>
        Resource.fromAutoCloseable(IO.blocking(Files.walk(target, 3))).use { stream =>
          IO.blocking(
            stream
              .iterator()
              .asScala
              .find(p =>
                p.getFileName.toString == "scoverage.xml" &&
                  Option(p.getParent).map(_.getFileName.toString).contains("scoverage-report")
              )
          )
        }
    }

  private def dirOf(p: String): String =
    val i = p.lastIndexOf('/')
    if i < 0 then "." else p.substring(0, i)

  /** A DocumentBuilder hardened against XXE / entity-expansion (scala-security.md): DTDs and
    * external references off, secure processing on — defense in depth even for a self-generated
    * report.
    */
  private def secureBuilder =
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    factory.newDocumentBuilder()
