package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{Deferred, IO}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}

import scala.util.control.NoStackTrace

/** Wiring failed because two agents share an id — a violation of the one-actor-one-id contract the
  * no-lone-sign floor depends on (a Sybil could satisfy the ≥2-distinct-backer floor with one real
  * actor). Fail closed at wiring, before any actor is installed.
  */
final case class DuplicateAgentIds(ids: List[String])
    extends RuntimeException(s"agent ids must be distinct; duplicates: ${ids.mkString(", ")}")
    with NoStackTrace

/** The wiring: install the [[ActorSystem]], the N [[AgentActor]]s (each with a DISTINCT id), the
  * single [[LogActor]], and an [[Oracle]]; play one game; return the terminal [[Outcome]]. Every
  * fiber — the actor loops, the round timeouts, the oracle round-trips — is owned by the composed
  * `Supervisor` / `ActorSystem` resource, so none outlives the game (scala-concurrency.md).
  */
object Society:

  /** The society's default per-game config: five rounds, a bounded round wait, a whole-game safety
    * deadline. Tune per run; tests inject their own.
    */
  val defaultConfig: SocietyConfig =
    import scala.concurrent.duration.*
    SocietyConfig(maxRounds = 12, roundTimeout = 30.seconds, hardDeadline = 10.minutes)

  /** A fail-closed [[LlmCall]] for the define carrier used when no real definer is wired (the
    * DEFAULT `definerFor`). It is never invoked in a game that has no challenge — only a
    * [[ToAgent.Clarify]] calls the definer, and Clarify is sent only on a challenge — so a
    * non-clarification game (every pre-clarification suite, the answer-only scripted oracle) never
    * touches it. If it somehow were called it fails closed: no meaning ⇒ no definition posted.
    */
  private val noDefiner: LlmCall[DefinitionDto] = new LlmCall[DefinitionDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, DefinitionDto]] =
      IO.pure(Left(CallError.Malformed("no definer wired for this agent")))

  /** Play one game to a terminal outcome.
    *
    * @param strategies
    *   the diverse agent cohort — ids MUST be distinct (else [[DuplicateAgentIds]]).
    * @param llmFor
    *   the model call for each agent (a shared live Anthropic call in production, a per-agent
    *   scripted stub in tests).
    * @param oracle
    *   the ground-truth seam.
    * @param sink
    *   where the ordered event log is emitted.
    * @param schedulerOf
    *   builds the timeout/submission scheduler from the game's supervisor; the production default
    *   supervises real fibers, tests inject deterministic doubles.
    * @param definerFor
    *   the clarification-define call for each agent (clarification-feature §2). Defaults to a
    *   fail-closed [[noDefiner]] since a game with no challenge never invokes it; the live path and
    *   the clarification suites wire a real one.
    */
  def play(
      strategies: List[AgentStrategy],
      llmFor: AgentId => LlmCall[AgentMoveDto],
      oracle: Oracle,
      sink: EventSink,
      config: SocietyConfig,
      schedulerOf: Supervisor[IO] => Scheduler = Scheduler.supervised,
      definerFor: AgentId => LlmCall[DefinitionDto] = _ => noDefiner
  ): IO[Outcome] =
    val ids = strategies.map(_.id.value)
    val duplicates = ids.diff(ids.distinct).distinct
    if duplicates.nonEmpty then IO.raiseError(DuplicateAgentIds(duplicates))
    else
      (Supervisor[IO], ActorSystem.resource).tupled.use { case (supervisor, system) =>
        val scheduler = schedulerOf(supervisor)
        val now = IO.realTime.map(_.toMillis)
        for
          logAddress <- address("society/log")
          beginId <- messageId("begin")
          done <- Deferred[IO, Outcome]
          deps = LogDeps(oracle, scheduler, sink, now, o => done.complete(o).void, config)
          logRef <- system.actorOf(logAddress)(context =>
            new LogActor(context, deps, LogState.initial)
          )
          agentRefs <- strategies.traverse { strategy =>
            address(s"society/agent/${strategy.id.value}").flatMap { agentAddress =>
              system
                .actorOf(agentAddress)(context =>
                  new AgentActor(
                    context,
                    strategy,
                    llmFor(strategy.id),
                    definerFor(strategy.id),
                    logRef
                  )
                )
                .map(ref => (strategy.id, ref))
            }
          }
          _ <- logRef.tell(beginId, ToLog.Begin(agentRefs))
          outcome <- done.get.timeout(config.hardDeadline)
        yield outcome
      }

  /** Lift a validating constructor's `Either[String, A]` into `IO`, raising (as a value, not a
    * `throw`) on the impossible blank — these labels are static non-blank literals.
    */
  private def address(raw: String): IO[Address] =
    IO.fromEither(Address.from(raw).leftMap(new IllegalArgumentException(_)))

  private def messageId(raw: String): IO[MessageId] =
    IO.fromEither(MessageId.from(raw).leftMap(new IllegalArgumentException(_)))
