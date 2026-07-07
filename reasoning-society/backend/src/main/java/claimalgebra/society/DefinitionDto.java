package claimalgebra.society;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The structured-output carrier for the asking agent's DEFINITION of a challenged term
 * (clarification-feature &sect;2) — the wire schema the SDK derives from this type, and the type the
 * model's response deserializes into (scala-llm.md). A plain Java class, not a Scala case class, so
 * the SDK's Jackson reflection maps it cleanly: a public field for schema generation and a
 * {@code @JsonCreator} constructor for deserialization (a Scala case class hides its field behind an
 * accessor Jackson does not recognize), mirroring {@code AgentMoveDto}.
 *
 * <p>Deliberately FLAT and single-field (scala-llm.md: keep the carrier flat): the one output is the
 * agreed {@code meaning} of the term. The harness RE-VALIDATES it with a total Scala function
 * ({@code Definition.meaningOf}) before any {@code DefinitionGiven} reaches the log, fail-closed: a
 * null/blank meaning yields NO post (the human re-challenges or answers ungrounded), never a
 * fabricated definition. The algebra core never imports this type.
 */
public final class DefinitionDto {

    /** The agreed meaning of the challenged term; null/blank is rejected at the boundary. */
    public final String meaning;

    @JsonCreator
    public DefinitionDto(@JsonProperty("meaning") String meaning) {
        this.meaning = meaning;
    }
}
