package claimalgebra.society;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The structured-output carrier for the EXPERIMENTER'S TRUTHFUL ORACLE (fallible-oracle experiment):
 * the correct yes/no/unknown answer to a player's question about the sealed target. A plain Java
 * class (Jackson reflection), mirroring {@code DefinitionDto} / {@code AgentMoveDto}. Deliberately
 * FLAT and single-field (scala-llm.md).
 *
 * <p>This is the HELD-FIXED ground truth the error model corrupts — NOT part of the society's
 * reasoning (the society never sees it). The harness re-validates {@code answer} to an
 * {@code OracleAnswer}, fail-closed to {@code unknown} (a gap) on a null or unrecognized value. The
 * algebra core never imports this type.
 */
public final class TruthDto {

    /** One of: {@code yes}, {@code no}, {@code unknown}; anything else is treated as {@code unknown}. */
    public final String answer;

    @JsonCreator
    public TruthDto(@JsonProperty("answer") String answer) {
        this.answer = answer;
    }
}
