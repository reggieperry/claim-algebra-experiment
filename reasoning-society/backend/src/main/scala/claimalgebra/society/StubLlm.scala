package claimalgebra.society

import cats.effect.{IO, Ref}
import claimalgebra.extract.{CallError, LlmCall}

/** A deterministic, hermetic [[LlmCall]] over a fixed per-call script — the ith call returns the
  * ith scripted result (a canned move, or a [[CallError]] to exercise the failure-is-abstention
  * path), repeating the last entry once exhausted. Backed by a `Ref` cursor, so it is stable
  * regardless of fiber timing. Used by the hermetic [[RunSociety]] demo and by the test suites
  * (only mock what you own — this is our own facade). Never hits the network.
  */
object StubLlm:

  /** A move DTO with no null fields (Scala bans the `null` literal; the Java carrier tolerates
    * absent fields as blank, which `AgentMove.parse` treats identically).
    */
  def move(action: String, candidate: String = "", text: String = ""): AgentMoveDto =
    new AgentMoveDto(action, candidate, text)

  val pass: AgentMoveDto = move("pass")

  def scripted(script: List[Either[CallError, AgentMoveDto]]): IO[LlmCall[AgentMoveDto]] =
    Ref[IO].of(0).map { cursor =>
      new LlmCall[AgentMoveDto]:
        def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
          cursor.getAndUpdate(_ + 1).map { i =>
            script.lift(i).orElse(script.lastOption).getOrElse(Right(pass))
          }
    }
