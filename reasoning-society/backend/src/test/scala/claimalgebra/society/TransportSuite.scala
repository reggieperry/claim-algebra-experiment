package claimalgebra.society

import cats.effect.IO
import cats.effect.std.Supervisor
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import fs2.{Stream, text}
import io.circe.Json
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.circe.*
import org.http4s.implicits.*
import org.http4s.{HttpApp, MediaType, Method, Request, Status}

import scala.concurrent.duration.*

/** The HTTP transport end to end, hermetic and deterministic (no sleep-as-sync in the assertions;
  * subscriptions are registered before the events are published via `subscribeAwait`, and the
  * subscriber count is awaited before publishing). Covers: the SSE endpoint carries the JSON wire
  * frames; a stub game streams through the Topic; `POST /answer` resolves the pending oracle
  * question so the game proceeds; a malformed body is a 400 not a 500; and residue #3 — a failing
  * subscriber touches neither the sink nor a second subscriber.
  */
class TransportSuite extends CatsEffectSuite with SocietyFixtures:

  private val apple = mkAnswer("apple")
  private val fast = SocietyConfig(maxRounds = 6, roundTimeout = 1.hour, hardDeadline = 10.seconds)

  // The round timeout never fires — rounds close on full-cohort report, so the outcome is
  // deterministic and order-free (matching SocietyGameSuite).
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  private def isGateSign(event: Event): Boolean = event match
    case _: Event.GateSign => true
    case _ => false

  private def assertOf(candidate: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("assert", candidate, "guess"))
  private def corroborateOf(candidate: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("corroborate", candidate, "agreed"))
  private def proposeOf(question: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("propose", "", question))
  private val passMove: Either[CallError, AgentMoveDto] = Right(StubLlm.pass)

  private val passStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(passMove)

  private def scriptedLlms(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, passStub))

  // The winning scenario: driller asserts apple, splitter proposes a question then (after the answer)
  // corroborates apple, skeptic passes — the gate abstains at one backer, signs at two.
  private val appleScripts = Map(
    "driller" -> List(assertOf("apple"), passMove),
    "splitter" -> List(proposeOf("Is it a fruit?"), corroborateOf("apple")),
    "skeptic" -> List(passMove, passMove)
  )

  test("GET /events is a text/event-stream that carries the emitted events as JSON data frames") {
    val e1 = Event.Assert(1, 1L, mkAgent("driller"), apple, "a fruit")
    val e2 = Event.GateAbstain(2, 2L, "watching")
    val e3 = Event.GateSign(3, 3L, apple)
    for
      topicAndSink <- TopicSink.make
      (sink, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      resp <- routes.run(Request[IO](method = Method.GET, uri = uri"/events"))
      _ = assertEquals(resp.status, Status.Ok)
      _ = assertEquals(resp.contentType.map(_.mediaType), Some(MediaType.`text/event-stream`))
      collector <- resp.body
        .through(text.utf8.decode)
        .through(text.lines)
        .filter(_.startsWith("data:"))
        .take(3)
        .compile
        .toList
        .start
      _ <- topic.subscribers.filter(_ >= 1).head.compile.drain
      _ <- List(e1, e2, e3).traverse_(sink.emit)
      frames <- collector.joinWithNever
    yield assertEquals(
      frames,
      List(e1, e2, e3).map(event => s"data: ${Wire.encode(event).noSpaces}")
    )
  }

  test("GET /events replays the game so far to a late-joining subscriber (catch-up then follow)") {
    val e1 = Event.Assert(1, 1L, mkAgent("driller"), apple, "a fruit")
    val e2 = Event.GateAbstain(2, 2L, "watching")
    val e3 = Event.GateSign(3, 3L, apple)
    for
      topicAndSink <- TopicSink.make
      (sink, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      // Events are emitted BEFORE any subscriber connects — a bare Topic would lose them entirely.
      _ <- List(e1, e2, e3).traverse_(sink.emit)
      resp <- routes.run(Request[IO](method = Method.GET, uri = uri"/events"))
      frames <- resp.body
        .through(text.utf8.decode)
        .through(text.lines)
        .filter(_.startsWith("data:"))
        .take(3)
        .compile
        .toList
    yield assertEquals(
      frames,
      List(e1, e2, e3).map(event => s"data: ${Wire.encode(event).noSpaces}"),
      clue("a late subscriber catches up on the full committed log")
    )
  }

  test(
    "a stub game's events stream through the Topic as JSON, ending in a gate_sign for the winner"
  ) {
    for
      topicAndSink <- TopicSink.make
      (sink, topic, _) = topicAndSink
      llmFor <- scriptedLlms(appleScripts)
      oracle <- Oracle.scripted(List(OracleAnswer.Yes))
      result <- topic.subscribeAwaitUnbounded.use { stream =>
        for
          collector <- stream.takeThrough(event => !isGateSign(event)).compile.toList.start
          outcome <- Society.play(AgentStrategy.cohort, llmFor, oracle, sink, fast, noTimeout)
          events <- collector.joinWithNever
        yield (outcome, events)
      }
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Signed(apple))
      events.lastOption match
        case None => fail("the game emitted no events")
        case Some(sign) =>
          assert(isGateSign(sign), clue("the stream ends at the signature"))
          val lastJson = Wire.encode(sign).noSpaces
          assert(lastJson.contains("\"type\":\"gate_sign\""), clue(lastJson))
          assert(lastJson.contains("\"candidateId\":\"apple\""), clue(lastJson))
  }

  test("POST /answer resolves the pending question so the game proceeds to a signature") {
    for
      topicAndSink <- TopicSink.make
      (sink, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      llmFor <- scriptedLlms(appleScripts)
      result <- topic.subscribeAwaitUnbounded.use { stream =>
        val autoAnswer: IO[Unit] =
          stream
            .collect { case q: Event.QuestionAsked => q.questionId }
            .evalMap(qid => postAnswer(routes, qid))
            .compile
            .drain
        IO.race(
          Society.play(AgentStrategy.cohort, llmFor, oracle, sink, fast, noTimeout),
          autoAnswer
        )
      }
    yield result match
      case Left(outcome) => assertEquals(outcome, Outcome.Signed(apple))
      case Right(_) => fail("the auto-answer stream ended before the game did")
  }

  test("POST /challenge completes a pending question with a Challenge move (resolved:true)") {
    val qid = mkQuestion("q1")
    for
      topicAndSink <- TopicSink.make
      (_, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      parked <- oracle.respond(Question(qid, "is it alive?")).start
      resolved <- postChallengeWhenRegistered(routes, qid, "Alive")
      move <- parked.joinWithNever
    yield
      assertEquals(resolved, true, clue("the challenge woke the pending question"))
      // "Alive" is normalized to the "alive" key — the challenge grounds against the stored form.
      assertEquals(move, HumanMove.Challenge(mkTerm("alive")))
  }

  test("POST /challenge rejects a malformed body with 400, never a 500") {
    for
      topicAndSink <- TopicSink.make
      (_, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      notJson <- routes.run(
        Request[IO](method = Method.POST, uri = uri"/challenge").withEntity("not json")
      )
      blankTerm <- routes.run(
        Request[IO](method = Method.POST, uri = uri"/challenge")
          .withEntity(Json.obj("questionId" -> "q1".asJson, "term" -> "   ".asJson))
      )
    yield
      assertEquals(notJson.status, Status.BadRequest, clue("a non-JSON body is a 400"))
      assertEquals(blankTerm.status, Status.BadRequest, clue("a blank term is a 400"))
  }

  test("POST /answer rejects a malformed body with 400, never a 500") {
    for
      topicAndSink <- TopicSink.make
      (_, topic, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      routes = SocietyRoutes(topic, logRef, oracle, IO.unit, IO.unit).orNotFound
      notJson <- routes.run(
        Request[IO](method = Method.POST, uri = uri"/answer").withEntity("not json")
      )
      badToken <- routes.run(
        Request[IO](method = Method.POST, uri = uri"/answer")
          .withEntity(Json.obj("questionId" -> "q1".asJson, "answer" -> "maybe".asJson))
      )
    yield
      assertEquals(notJson.status, Status.BadRequest, clue("a non-JSON body is a 400"))
      assertEquals(badToken.status, Status.BadRequest, clue("an answer outside the set is a 400"))
  }

  test("a failing SSE subscriber is contained — the sink and a second subscriber are unaffected") {
    val e1 = Event.Assert(1, 1L, mkAgent("driller"), apple, "x")
    val e2 = Event.GateAbstain(2, 2L, "watching")
    val e3 = Event.GateSign(3, 3L, apple)
    for
      topicAndSink <- TopicSink.make
      (sink, topic, _) = topicAndSink
      received <- topic.subscribeAwaitUnbounded.use { healthy =>
        val failing: Stream[IO, Nothing] =
          topic.subscribeUnbounded
            .evalMap(_ => IO.raiseError[Nothing](new RuntimeException("browser dropped")))
            .handleErrorWith(_ => Stream.exec(IO.unit))
        for
          healthyFiber <- healthy.take(3).compile.toList.start
          failFiber <- failing.compile.drain.start
          _ <- topic.subscribers.filter(_ >= 2).head.compile.drain
          // The sink publishes the whole log without blocking or failing on the bad subscriber.
          _ <- List(e1, e2, e3).traverse_(sink.emit)
          got <- healthyFiber.joinWithNever
          _ <- failFiber.joinWithNever // the failure was contained — no throw escapes the boundary
        yield got
      }
    yield assertEquals(received, List(e1, e2, e3), clue("the healthy subscriber got the whole log"))
  }

  private def postAnswer(routes: HttpApp[IO], qid: QuestionId): IO[Unit] =
    val body = Json.obj("questionId" -> qid.value.asJson, "answer" -> "yes".asJson)
    val request = Request[IO](method = Method.POST, uri = uri"/answer").withEntity(body)
    routes.run(request).flatMap(_.as[Json]).flatMap { response =>
      // The oracle registers the pending question asynchronously (the LogActor submits the parked
      // call after emitting question_asked), so retry until resolve reports it woke a question.
      if response.hcursor.get[Boolean]("resolved").getOrElse(false) then IO.unit
      else IO.sleep(2.millis) *> postAnswer(routes, qid)
    }

  /** POST a challenge, retrying until it reports it woke a pending question — the registration is
    * asynchronous, so the same deterministic wait-for-registration idiom as `postAnswer`.
    */
  private def postChallengeWhenRegistered(
      routes: HttpApp[IO],
      qid: QuestionId,
      term: String
  ): IO[Boolean] =
    val body = Json.obj("questionId" -> qid.value.asJson, "term" -> term.asJson)
    val request = Request[IO](method = Method.POST, uri = uri"/challenge").withEntity(body)
    routes.run(request).flatMap(_.as[Json]).flatMap { response =>
      if response.hcursor.get[Boolean]("resolved").getOrElse(false) then IO.pure(true)
      else IO.sleep(2.millis) *> postChallengeWhenRegistered(routes, qid, term)
    }
