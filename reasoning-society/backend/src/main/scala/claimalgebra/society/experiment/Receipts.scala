package claimalgebra.society
package experiment

import cats.syntax.all.*

import java.security.MessageDigest

/** The committed *receipt* of one archived experiment run: a distilled, one-line-per-game
  * projection of the full archive that lives under `results/<archive>/<arm>/<game>.log`. A receipt
  * carries only the header fields a headline table is a fold over — arm, cell, target, seed,
  * outcome, signPath — never the transcript bodies. The full archives are gitignored and
  * machine-local; the receipts are committed, so the published result tables can be recomputed from
  * them on any machine (the reproducibility surface behind `ResultsReceiptsSuite`).
  *
  * The vocab below (`AllowedOutcomes`, `AllowedSignPaths`) is a frozen projection of what
  * [[Archiver]] renders from `PrimaryOutcome` and `SignPath`; the parser is fail-closed, so a token
  * outside it is an error at distill time rather than a silently dropped game. Each digest carries
  * a `sha256` over its canonical (sorted) body, so a receipt that was edited or truncated fails
  * [[Receipts.parseDigest]] rather than yielding a wrong count.
  *
  * Trust boundary: that sha256 is over the receipt BODY, not the source archive. The suite proves a
  * receipt is internally consistent and folds to its published table; faithful distillation from
  * the gitignored archive is attested only by re-running [[RunDistillReceipts]] and diffing, never
  * by this suite.
  */
final case class GameRow(
    arm: String,
    cell: String,
    target: String,
    seed: String,
    outcome: String,
    signPath: String
)

/** A parsed, integrity-checked receipt: its label, the run's config stamp, and its games. */
final case class Receipt(name: String, stamp: String, games: List[GameRow])

object Receipts:

  /** Frozen projection of `Archiver`'s `PrimaryOutcome` rendering — the three outcome tokens a game
    * header can carry. A token outside this set fails the parse (fail-closed).
    */
  val AllowedOutcomes: Set[String] = Set("SignCorrect", "SignWrong", "Abstain")

  /** Frozen projection of `Archiver`'s `signPath` rendering: `-` for an abstained game (no path),
    * plus the two sign paths a signed game can take.
    */
  val AllowedSignPaths: Set[String] = Set("-", "BackerQuorum", "OracleConfirmed")

  private val FieldsLine = "# fields: arm cell target seed outcome signPath"

  /** Parse the first (header) line of an archived game log, `# arm=… cell=… target=… seed=…
    * outcome=… signed=… signPath=…`, into a [[GameRow]]. Fail-closed: a missing key or an
    * out-of-vocabulary outcome/signPath token is a `Left`, never a dropped or coerced row.
    */
  def parseArchiveHeader(line: String): Either[String, GameRow] =
    val kvs =
      line
        .stripPrefix("#")
        .trim
        .split("\\s+")
        .toList
        .flatMap { tok =>
          tok.split("=", 2) match
            case Array(k, v) => List(k -> v)
            case _ => Nil
        }
        .toMap
    for
      arm <- kvs.get("arm").toRight(s"missing arm in header: $line")
      cell <- kvs.get("cell").toRight(s"missing cell in header: $line")
      target <- kvs.get("target").toRight(s"missing target in header: $line")
      seed <- kvs.get("seed").toRight(s"missing seed in header: $line")
      outcome <- kvs
        .get("outcome")
        .toRight(s"missing outcome in header: $line")
        .flatMap(validOutcome)
      signPath <- kvs
        .get("signPath")
        .toRight(s"missing signPath in header: $line")
        .flatMap(validSignPath)
    yield GameRow(arm, cell, target, seed, outcome, signPath)

  /** Render a receipt file: a `# receipt …` header carrying the canonical-body sha256 and the game
    * count, the fields legend, then one sorted data line per game.
    */
  def renderDigest(name: String, stamp: String, rows: List[GameRow]): String =
    val sorted = canonicalOrder(rows)
    val header =
      s"# receipt $name stamp=$stamp games=${sorted.size} sha256=${sha256Hex(bodyOf(sorted))}"
    (header :: FieldsLine :: sorted.map(dataLine)).mkString("", "\n", "\n")

  /** Parse a committed receipt file and re-verify its integrity: the recomputed canonical-body
    * sha256 must equal the header's, and the data-line count must equal the header's `games`. Any
    * mismatch is a `Left` — a tampered or truncated receipt cannot yield a table.
    */
  def parseDigest(text: String): Either[String, Receipt] =
    val lines = text.linesIterator.toList
    val dataLines = lines.filter(l => l.nonEmpty && !l.startsWith("#"))
    for
      header <- lines
        .find(_.startsWith("# receipt "))
        .toRight("receipt file has no `# receipt` header")
      meta <- parseHeaderMeta(header)
      rows <- dataLines.traverse(parseDataLine)
      _ <- Either.cond(
        rows.sizeIs == meta.games,
        (),
        s"receipt ${meta.name}: header games=${meta.games} but body has ${rows.size} rows"
      )
      recomputed = sha256Hex(bodyOf(rows))
      _ <- Either.cond(
        recomputed == meta.sha,
        (),
        s"receipt ${meta.name}: sha256 mismatch (header ${meta.sha}, recomputed $recomputed) — edited or truncated"
      )
    yield Receipt(meta.name, meta.stamp, rows)

  /** The canonical-body sha256 of a set of games — the same stamp `renderDigest` writes into the
    * header and `parseDigest` re-verifies. Public so the distiller can echo it to the console.
    */
  def sha256Of(rows: List[GameRow]): String = sha256Hex(bodyOf(rows))

  /** SHA-256 of a string, lowercase hex. Used for the receipt's tamper-evidence stamp. */
  def sha256Hex(s: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(s.getBytes("UTF-8"))
      .map(b => f"${b & 0xff}%02x")
      .mkString

  private def canonicalOrder(rows: List[GameRow]): List[GameRow] =
    rows.sortBy(r => (r.arm, r.cell, r.target, r.seed))

  private def bodyOf(rows: List[GameRow]): String =
    canonicalOrder(rows).map(dataLine).mkString("\n")

  private def dataLine(r: GameRow): String =
    s"${r.arm} ${r.cell} ${r.target} ${r.seed} ${r.outcome} ${r.signPath}"

  private def validOutcome(s: String): Either[String, String] =
    Either.cond(AllowedOutcomes.contains(s), s, s"unknown outcome token: $s")

  private def validSignPath(s: String): Either[String, String] =
    Either.cond(AllowedSignPaths.contains(s), s, s"unknown signPath token: $s")

  private def parseDataLine(line: String): Either[String, GameRow] =
    line.trim.split("\\s+").toList match
      case arm :: cell :: target :: seed :: outcome :: signPath :: Nil =>
        for
          o <- validOutcome(outcome)
          p <- validSignPath(signPath)
        yield GameRow(arm, cell, target, seed, o, p)
      case other =>
        Left(s"receipt data line needs 6 fields, got ${other.size}: $line")

  final private case class DigestMeta(name: String, stamp: String, games: Int, sha: String)

  private def parseHeaderMeta(header: String): Either[String, DigestMeta] =
    val kvs =
      header
        .split("\\s+")
        .toList
        .flatMap { tok =>
          tok.split("=", 2) match
            case Array(k, v) => List(k -> v)
            case _ => Nil
        }
        .toMap
    // `# receipt <name> stamp=… games=… sha256=…` — the name is the 3rd whitespace token.
    val tokens = header.split("\\s+").toList
    for
      name <- tokens.lift(2).toRight(s"receipt header has no name: $header")
      stamp <- kvs.get("stamp").toRight(s"receipt header has no stamp: $header")
      gamesS <- kvs.get("games").toRight(s"receipt header has no games: $header")
      games <- gamesS.toIntOption.toRight(s"receipt header games is not an int: $gamesS")
      sha <- kvs.get("sha256").toRight(s"receipt header has no sha256: $header")
    yield DigestMeta(name, stamp, games, sha)
