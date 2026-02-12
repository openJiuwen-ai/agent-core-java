package com.openjiuwen.core.common.schema.workflow;

/**
 * Functional interface for state transformation.
 * 
 * <p>Transforms a readable state into any type of result.
 * This interface is used for custom state querying and transformation logic
 * in workflow and session state management.
 * 
 * <p><strong>Note</strong>: This is a base interface that provides a generic
 * transform method. For type-safe usage with specific state types, see
 * {@code com.openjiuwen.core.session.state.Transformer}.
 * 
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * Transformer sumTransformer = state -> {
 *     Integer a = (Integer) getFromState(state, "a");
 *     Integer b = (Integer) getFromState(state, "b");
 *     return (a != null ? a : 0) + (b != null ? b : 0);
 * };
 * }</pre>
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
@FunctionalInterface
public interface Transformer {
    
    /**
     * Transforms the given state into a result.
     * 
     * @param state the state object to transform (typically a ReadableStateLike or Map)
     * @return the transformation result
     */
    Object transform(Object state);
}

