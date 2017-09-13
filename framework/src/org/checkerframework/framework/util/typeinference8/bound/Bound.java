package org.checkerframework.framework.util.typeinference8.bound;

import java.util.Collection;
import org.checkerframework.framework.util.typeinference8.reduction.ReductionResult;
import org.checkerframework.framework.util.typeinference8.types.Variable;

/** https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.1.3 */
public abstract class Bound implements ReductionResult {
    public static Bound FALSE =
            new Bound() {
                @Override
                public Kind getKind() {
                    return Kind.FALSE;
                }
            };

    public enum Kind {
        /**
         * {@code S = T}, where at least one of S or T is an inference variable: S is the same as T.
         */
        EQUAL,

        /**
         * {@code S <: T}, where at least one of S or T is an inference variable: S is a subtype of
         * T.
         */
        SUBTYPE,

        /** false: No valid choice of inference variables exists. */
        FALSE,

        /**
         * {@code G<a1, ..., an> = capture(G<A1, ..., An>)}: The variables a1, ..., an represent the
         * result of capture conversion applied to {@code G<A1, ..., An>} (where A1, ..., An may be
         * types or wildcards and may mention inference variables).
         */
        CAPTURE,

        /** {@code throws a}: The inference variable a appears in a throws clause. */
        THROWS
    }

    public abstract Kind getKind();

    /**
     * Is the bound of the form {@code G<..., ai, ...> = capture(G<...>)} for some a in as?
     *
     * @param as
     */
    public boolean isCaptureMentionsAny(Collection<Variable> as) {
        return false;
    }
}
