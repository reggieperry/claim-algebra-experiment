package claimalgebra.society;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The structured-output carrier for one agent move — the wire schema the SDK derives from this type,
 * and the type the model's response deserializes into (scala-llm.md). A plain Java class, not a
 * Scala case class, so the SDK's Jackson reflection maps it cleanly: public fields for schema
 * generation and a {@code @JsonCreator} constructor for deserialization (a Scala case class hides its
 * field behind an accessor Jackson does not recognize), mirroring {@code ExtractionDto}.
 *
 * <p>The carrier is deliberately FLAT (scala-llm.md: recursive/complex-enum schemas are unsupported —
 * keep it flat). One agent move is one of a small closed set of actions:
 * <ul>
 *   <li>{@code assert} / {@code corroborate} / {@code refute}: {@code candidate} names the hypothesis,
 *       {@code text} is the human-readable content/note;
 *   <li>{@code propose}: {@code text} is the question to put to the oracle ({@code candidate} unused);
 *   <li>{@code pass}: no belief move (both unused).
 * </ul>
 * The wire schema constrains generation; the harness RE-VALIDATES every field with a total Scala
 * function ({@code AgentMove.parse}) before any move reaches the log, fail-closed: an unknown action or
 * a blank candidate where one is required yields NO post (an abstention), never a guessed belief. The
 * algebra core never imports this type.
 */
public final class AgentMoveDto {

    /** The action: one of {@code assert}, {@code corroborate}, {@code refute}, {@code propose}, {@code pass}. */
    public final String action;

    /** The hypothesis label (assert/corroborate/refute); null/blank otherwise. */
    public final String candidate;

    /** The content, note, or proposed question text; may be null. */
    public final String text;

    @JsonCreator
    public AgentMoveDto(
            @JsonProperty("action") String action,
            @JsonProperty("candidate") String candidate,
            @JsonProperty("text") String text) {
        this.action = action;
        this.candidate = candidate;
        this.text = text;
    }
}
