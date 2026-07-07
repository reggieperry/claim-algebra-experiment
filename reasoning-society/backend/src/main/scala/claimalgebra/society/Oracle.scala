package claimalgebra.society

import cats.effect.{IO, Ref}

/** A question put to the oracle — its identity (so the answer routes back to the right slot on the
  * log) and its human-readable text.
  */
final case class Question(id: QuestionId, text: String)

/** The human/oracle seam (brief item 3): the ONLY source of ground truth (`AnswerGiven`), outside
  * the actor graph (actor-abstraction §3 — the observer gets no vote; the oracle is asked, it does
  * not reason). A trait so tests inject a scripted/deterministic oracle and a later slice wires the
  * frontend. The LogActor asks through this and routes the reply back to itself as an actor
  * message.
  */
trait Oracle:
  def answer(question: Question): IO[OracleAnswer]

object Oracle:

  /** A deterministic oracle that replies from a fixed script in order, defaulting to `Unknown` once
    * exhausted (a real human who has stopped answering — belief-inert, never a stand-in for "no").
    * The cursor is a `Ref`, so replies are stable and order-independent of wall-clock timing.
    */
  def scripted(replies: List[OracleAnswer]): IO[Oracle] =
    Ref[IO].of(0).map { cursor =>
      new Oracle:
        def answer(question: Question): IO[OracleAnswer] =
          cursor.getAndUpdate(_ + 1).map(i => replies.lift(i).getOrElse(OracleAnswer.Unknown))
    }

  /** A console oracle for a live run — prints the question and reads a yes/no/unknown from stdin. A
    * blank or unrecognized line (or EOF) is `Unknown`, never a guessed `No`.
    */
  val console: Oracle = new Oracle:
    def answer(question: Question): IO[OracleAnswer] =
      for
        _ <- IO.println(s"\nQ: ${question.text}  [y/n/?]")
        line <- IO.blocking(Option(scala.io.StdIn.readLine()))
      yield line.map(_.trim.toLowerCase) match
        case Some("y") | Some("yes") => OracleAnswer.Yes
        case Some("n") | Some("no") => OracleAnswer.No
        case _ => OracleAnswer.Unknown
