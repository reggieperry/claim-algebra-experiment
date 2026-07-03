package claimalgebra.extract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The structured-output carrier — the wire schema the SDK derives from this type, and the type the
 * model's response deserializes into (scala-llm.md). A plain Java class, not a Scala case class, so
 * the SDK's Jackson reflection maps it cleanly: public fields for schema generation and a
 * {@code @JsonCreator} constructor for deserialization (a Scala case class hides its field behind an
 * accessor Jackson does not recognize).
 *
 * <p>The model's one job is to CITE — never to author the signed value. Two citation shapes share
 * this carrier:
 * <ul>
 *   <li>the plain span path ({@code quote} only): the model cites the verbatim span and the harness
 *       grounds it whole;
 *   <li>the anchor-and-extract path ({@code quote} = the value, {@code anchor} = a short verbatim
 *       snippet of the document that contains the value): the harness grounds the value as a whole
 *       figure located inside the anchor. This lets the model wrap or reformat freely around the
 *       value without failing closed, since only the anchor and the value need be verbatim.
 * </ul>
 * Either way a wrong number cannot be injected, only a wrong span/anchor, which grounding catches.
 * {@code anchor} is optional (null on the plain path). The algebra core never imports this type.
 */
public final class ExtractionDto {

    /** The verbatim span the model cites (plain path), or the value to sign (anchor path). */
    public final String quote;

    /** A short verbatim snippet of the document containing the value (anchor path; null otherwise). */
    public final String anchor;

    @JsonCreator
    public ExtractionDto(
            @JsonProperty("quote") String quote,
            @JsonProperty("anchor") String anchor) {
        this.quote = quote;
        this.anchor = anchor;
    }

    /** Convenience for the plain-span path (no anchor); not the Jackson creator. */
    public ExtractionDto(String quote) {
        this(quote, null);
    }
}
