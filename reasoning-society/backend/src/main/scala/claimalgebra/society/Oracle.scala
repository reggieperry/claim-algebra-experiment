package claimalgebra.society

import cats.effect.{IO, Ref}

/** A question put to the oracle — its identity (so the answer routes back to the right slot on the
  * log) and its human-readable text.
  */
final case class Question(id: QuestionId, text: String)

/** The human's TWO-move response to a question (clarification-feature §1): either [[Answer]] the
  * question (yes/no/unknown, as before), or [[Challenge]] a term in it — "what do you mean by
  * '<term>'?" — asked BEFORE answering. A [[Challenge]] PAUSES grounding: no `AnswerGiven` enters
  * the ledger; instead the asking agent defines the term and the question is re-asked, so the human
  * can answer against the agreed meaning or challenge again. A closed `enum`, so the LogActor's
  * routing over it is exhaustively checked.
  */
enum HumanMove:
  case Answer(answer: OracleAnswer)
  case Challenge(term: Term)

/** The human/oracle seam (brief item 3): the ONLY source of ground truth (`AnswerGiven`), outside
  * the actor graph (actor-abstraction §3 — the observer gets no vote; the oracle is asked, it does
  * not reason). A trait so tests inject a scripted/deterministic oracle and a later slice wires the
  * frontend. The LogActor asks through this and routes the reply back to itself as an actor
  * message. The reply is a [[HumanMove]] (an answer OR a clarification challenge), so the human can
  * refuse to answer an ambiguous question until its meaning is pinned down.
  */
trait Oracle:
  def respond(question: Question): IO[HumanMove]

object Oracle:

  /** A deterministic oracle that ANSWERS from a fixed script in order, defaulting to `Unknown` once
    * exhausted (a real human who has stopped answering — belief-inert, never a stand-in for "no").
    * Never challenges — the answer-only script the pre-clarification suites use unchanged.
    */
  def scripted(replies: List[OracleAnswer]): IO[Oracle] =
    scriptedMoves(replies.map(HumanMove.Answer(_)))

  /** A deterministic oracle that replies with a fixed script of [[HumanMove]]s in order — the
    * two-move counterpart of [[scripted]], so a test can script a `Challenge` then an `Answer` and
    * exercise the grounding-pause flow. Defaults to `Answer(Unknown)` once exhausted (never a
    * dangling challenge that would wedge the loop). The cursor is a `Ref`, so replies are stable
    * and order-independent of wall-clock timing.
    */
  def scriptedMoves(moves: List[HumanMove]): IO[Oracle] =
    Ref[IO].of(0).map { cursor =>
      new Oracle:
        def respond(question: Question): IO[HumanMove] =
          cursor
            .getAndUpdate(_ + 1)
            .map(i => moves.lift(i).getOrElse(HumanMove.Answer(OracleAnswer.Unknown)))
    }

  /** A console oracle for a live run — prints the question and reads one line from stdin. `y/n/?`
    * (or `yes/no/unknown`) is an [[HumanMove.Answer]]; `define <term>` (or `? <term>`) is an
    * [[HumanMove.Challenge]] of that term. A blank or unrecognized line (or EOF) is
    * `Answer(Unknown)`, never a guessed `No` and never a blank-term challenge.
    */
  val console: Oracle = new Oracle:
    def respond(question: Question): IO[HumanMove] =
      for
        _ <- IO.println(s"\nQ: ${question.text}  [y/n/? | define <term>]")
        line <- IO.blocking(Option(scala.io.StdIn.readLine()))
      yield parseConsole(line)

  /** Parse one console line into a [[HumanMove]], fail-closed to `Answer(Unknown)` on anything
    * unrecognized or a blank challenge term.
    */
  private def parseConsole(line: Option[String]): HumanMove =
    line.map(_.trim).map(l => (l.toLowerCase(java.util.Locale.ROOT), l)) match
      case Some(("y", _)) | Some(("yes", _)) => HumanMove.Answer(OracleAnswer.Yes)
      case Some(("n", _)) | Some(("no", _)) => HumanMove.Answer(OracleAnswer.No)
      case Some((lower, raw)) if lower.startsWith("define ") || lower.startsWith("? ") =>
        val rawTerm = raw.dropWhile(c => c != ' ').trim
        Term.from(rawTerm).fold(_ => HumanMove.Answer(OracleAnswer.Unknown), HumanMove.Challenge(_))
      case _ => HumanMove.Answer(OracleAnswer.Unknown)
