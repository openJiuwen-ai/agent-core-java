/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

/**
 * ReMe retrieval prompt templates.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reme.prompt.ReMePrompts}.
 */
public class ReMeRetrievePrompts {

    /**
     * Prompt for reranking retrieved experiences based on relevance.
     */
    public static final String MEMORY_RERANK_PROMPT = """
You are an expert AI analyst tasked with reranking retrieved experiences based on their relevance to a specific query.

Your task is to analyze the candidates and rank them by relevance, considering:
● DIRECT RELEVANCE: How directly applicable the experience is to the current query
● SITUATION SIMILARITY: How similar the experience context is to the current situation
● ACTIONABILITY: How actionable and specific the experience is
● QUALITY: The overall quality and clarity of the experience

# Current Query
{query}

# Candidate Experiences (Total: {num_candidates})
{candidates}

OUTPUT FORMAT:
Provide a ranked list of candidate indices (0-based) from most relevant to least relevant:
```json
{
  "ranked_indices": [2, 0, 4, 1, 3],
  "reasoning": "Brief explanation of ranking rationale"
}
```

Note: Include ALL candidate indices in the ranking, even if some are less relevant.""";

    /**
     * Prompt for rewriting and reorganizing context content.
     */
    public static final String MEMORY_REWRITE_PROMPT = """
You are an expert AI assistant tasked with rewriting and reorganizing context content to make it more relevant and actionable for the current task.

Your task is to take the original context (containing multiple experiences) and rewrite it as a cohesive, task-specific guidance that directly addresses the current situation.

REWRITING GUIDELINES:
● RELEVANCE FOCUS: Emphasize the most relevant aspects of each experience. Prioritize the most relevant experiences. Use clear, direct language.
● ACTIONABLE INSIGHTS: Extract specific, actionable guidance. Make the context immediately actionable
● COHERENT NARRATIVE: Create a flowing narrative rather than disconnected tips
● SITUATIONAL AWARENESS: Adapt the guidance to the current situation

# Current Task/Query
{current_query}

# Original Context Content (Multiple Experiences)
{original_context}

OUTPUT FORMAT:
Provide the rewritten context:
```json
{
  "rewritten_context": "A cohesive, task-specific context message that reorganizes and adapts the original experiences for the current task. This should be written as a unified guidance rather than separate experience items.",
}
```

Guidelines:
- Rewrite as a unified, flowing guidance
- Adapt terminology and examples to match the current task domain
- Consolidate overlapping insights into coherent recommendations
- Prioritize experiences most relevant to the current situation
- Make the guidance feel custom-written for this specific task""";

    /**
     * Default instance with standard prompts.
     */
    public static final ReMeRetrievePrompts INSTANCE = new ReMeRetrievePrompts();

    private final String rerankPrompt;
    private final String rewritePrompt;

    /**
     * Default constructor with standard prompts.
     */
    public ReMeRetrievePrompts() {
        this(MEMORY_RERANK_PROMPT, MEMORY_REWRITE_PROMPT);
    }

    /**
     * Constructor with custom prompts.
     *
     * @param rerankPrompt custom rerank prompt template
     * @param rewritePrompt custom rewrite prompt template
     */
    public ReMeRetrievePrompts(String rerankPrompt, String rewritePrompt) {
        this.rerankPrompt = rerankPrompt;
        this.rewritePrompt = rewritePrompt;
    }

    /**
     * Get the rerank prompt.
     *
     * @return rerank prompt template
     */
    public String getRerankPrompt() {
        return rerankPrompt;
    }

    /**
     * Get the rewrite prompt.
     *
     * @return rewrite prompt template
     */
    public String getRewritePrompt() {
        return rewritePrompt;
    }

    /**
     * Format the rerank prompt with query and candidates.
     *
     * @param query the current query
     * @param candidates formatted candidate experiences
     * @param numCandidates number of candidates
     * @return formatted prompt
     */
    public String formatRerankPrompt(String query, String candidates, int numCandidates) {
        return rerankPrompt
            .replace("{query}", query)
            .replace("{candidates}", candidates)
            .replace("{num_candidates}", String.valueOf(numCandidates));
    }

    /**
     * Format the rewrite prompt with query and original context.
     *
     * @param currentQuery the current query
     * @param originalContext the original context content
     * @return formatted prompt
     */
    public String formatRewritePrompt(String currentQuery, String originalContext) {
        return rewritePrompt
            .replace("{current_query}", currentQuery)
            .replace("{original_context}", originalContext);
    }
}