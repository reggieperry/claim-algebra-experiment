package claimalgebra.extract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The structured-output carrier for the con-channel supersession extractor — the supersession
 * counterpart of {@link ExtractionDto}. The model CITES two spans rather than computing anything: a
 * span asserting that the balance-sheet figure is superseded/restated by the notes, and a span that
 * withdraws or disputes that supersession. Either may be absent (null). Each cited span is grounded
 * verbatim against the corpus before it is admitted, exactly as a figure is, so a hallucinated
 * citation is dropped rather than trusted.
 *
 * <p>A plain Java class, not a Scala case class, so the SDK's Jackson reflection maps it cleanly. The
 * algebra core never imports this boundary type.
 */
public final class SupersessionDto {

    /** A verbatim span asserting the balance-sheet figure is superseded by the notes, or null. */
    public final String supersedeSpan;

    /** A verbatim span withdrawing or disputing that supersession, or null. */
    public final String withdrawSpan;

    @JsonCreator
    public SupersessionDto(
            @JsonProperty("supersedeSpan") String supersedeSpan,
            @JsonProperty("withdrawSpan") String withdrawSpan) {
        this.supersedeSpan = supersedeSpan;
        this.withdrawSpan = withdrawSpan;
    }
}
