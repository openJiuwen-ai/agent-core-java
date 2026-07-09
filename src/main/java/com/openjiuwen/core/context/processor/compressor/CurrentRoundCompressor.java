/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compress the current round into protocolized memory blocks.
 * 
 * @since 0.1.7
 */
public class CurrentRoundCompressor extends ContextProcessor {
    static final String SUMMARY_MARKER = "[CURRENT_ROUND_MEMORY_BLOCK]";

    /**
     * Blocks.
     * 
     * @param rewrite rewrite
     * @return the result
     * @since 0.1.7
     */
    private static final String DEFAULT_COMPRESSION_PROMPT = """
        You are a **Task Data Preservation Expert**.

        Your role is to produce a **high-fidelity incremental memory block** for long-running agent tasks.

        Your output will:
        1. REPLACE the selected_messages section in the current context
        2. BE APPENDED to accumulated memory blocks
        3. PRESERVE continuity without rewriting prior memory

        ---

        ## CONTEXT STRUCTURE

        User Query
        ↓
        Accumulated Memory Blocks  (persistent memory; DO NOT rewrite)
        ↓
        Selected Messages  (THIS is the ONLY content to compress)
        ↓
        Recent Messages  (boundary context; DO NOT absorb unless required for interpretation)

        ---

        [User Intent Context - REFERENCE ONLY]:
        {prior_context_and_query}

        Rules:
        - This section contains: recent raw user requests, recent assistant replies without tool calls, and \
        the current query that triggered this round
        - Use ONLY to understand the user's intent and the context leading to selected_messages
        - Preserve the user's original requirements, constraints, acceptance criteria, and preferences as \
        completely as possible when they are needed to continue the ongoing work
        - Do NOT weaken or over-compress the user's original request unless absolutely necessary
        - Treat this as reference context for interpreting selected_messages, not as another compression target

        ---

        [Prior memory blocks - REFERENCE ONLY]:
        {accumulated_summaries}

        Rules:
        - Use ONLY to understand goals, constraints, prior decisions, and continuity
        - DO NOT restate, paraphrase, or duplicate their content
        - Only reference them when needed to correctly interpret selected_messages

        ---

        [Selected messages - TARGET]:
        {selected_messages}

        Rules:
        - This is the ONLY content you are compressing
        - Extract all new progress, changes, unresolved work, and state transitions from this span

        ---

        [Recent uncompressed messages - BOUNDARY CONTEXT]:
        {recent_messages}

        Rules:
        - Use ONLY to resolve ambiguity, references, or incomplete meaning in selected_messages
        - DO NOT include their standalone content in your output
        - If recent_messages already contain the latest explicit state, do NOT restate them
        - Only preserve the minimum handoff information needed to connect selected_messages to recent_messages

        ---

        ## CORE PRINCIPLE (CRITICAL)

        Treat this output as an **incremental memory block**, NOT a full snapshot.

        - Do NOT reconstruct the full global state
        - Do NOT repeat previously summarized information
        - ONLY capture what is NEW, UPDATED, or STILL OPEN in selected_messages

        ---

        ## INFORMATION PRIORITY (CRITICAL)

        Preserve information in this order:

        1. Task goals and user intent
        2. Critical factual basis for continuation
        3. Open work / unfinished work
        4. Work in progress at the handoff boundary
        5. Key decisions, constraints, changes
        6. Important files, artifacts, resources, and outputs
        7. Supporting details

        Never drop higher-priority information to preserve lower-priority details.

        ---

        ## FACTUAL BASIS PRESERVATION (CRITICAL)

        When preserving progress, always retain the factual basis required to correctly continue the task, \
        including:
        - key outputs
        - constraints
        - evidence
        - extracted findings
        - comparisons
        - conclusions
        - decisive intermediate results

        When selected_messages contain information that has already been verified, confirmed, validated, or \
        otherwise established with strong support, preserve that verified state explicitly.
        Do NOT weaken verified state into vague uncertainty such as "possible", "candidate", or "requires \
        re-evaluation" unless selected_messages contain real counter-evidence or unresolved conflict.

        Do NOT preserve action history without the information needed to understand why the action matters.

        ---

        ## EVIDENCE PRESERVATION (CRITICAL - DO NOT SUMMARIZE)

        For tasks where continuation depends on concrete evidence, verification, or reasoning trace, the \
        following types of evidence MUST be preserved IN FULL or with MINIMAL compression.
        This is especially important for debugging, bug-fixing, code modification, investigation, analysis, \
        and other evidence-driven work:

        1. **Test/Script Execution Results**:
           - Do NOT compress actual outputs when they contain the factual basis needed later (for example: \
           error messages, stack traces, SQL queries, log outputs, tool results, extracted values, comparison \
           outputs)
           - These outputs often contain the critical clue that leads to the correct conclusion

        2. **Root Cause Discovery Evidence**:
           - When agent discovers the root cause or key insight through inspection, testing, comparison, or \
           analysis, preserve:
             - The specific source examined
             - The key observation that led to the insight
             - The exact quote or output that triggered the discovery
           - Do NOT replace with summary like "agent found the issue" - preserve HOW they found it

        3. **Key Reasoning Chains**:
           - When agent makes a critical decision (e.g., which file to modify, which source to trust, which \
           approach to take):
             - Preserve the observations that led to the decision
             - Preserve any evidence/counter-evidence considered
             - Preserve alternatives that were evaluated
           - Do NOT just record the final decision without the reasoning

        4. **Verification Results**:
           - When agent verifies a hypothesis, validates a result, or tests a fix:
             - Preserve the verification step and its output
             - Preserve whether it passed/failed/confirmed/refuted and key details
             - Preserve any unexpected observations

        ---

        ## TASK-TYPE ADAPTATION (CRITICAL)

        Adapt the retention focus to the task type:

        - For execution-heavy tasks (e.g. coding, debugging, multi-step operations):
          prioritize action continuity, WIP state, handoff points, dependencies, and execution blockers.

        - For information-heavy tasks (e.g. research, report writing, PPT drafting, analysis):
          prioritize findings, evidence, extracted structure, comparisons, conclusions, key outputs, and \
          unresolved questions.

        In all cases, preserve both:
        - what has been done
        - what has been learned

        ---

        ## STRATEGY HANDLING (CRITICAL)

        Do NOT encode candidate plans or solution strategies as instructions.

        If strategies were discussed, record them as one of:
        - attempted approach
        - candidate approach
        - rejected approach
        - pending evaluation

        Never present any strategy as mandatory unless explicitly required by the user.

        ---

        ## DECISION SOLIDIFICATION PREVENTION (CRITICAL)

        When a decision or approach is recorded, you MUST preserve the reasoning process, NOT just the \
        conclusion:

        1. **Do NOT solidify unverified decisions**:
           - If agent proposed an approach but hasn't tested it yet, mark it as "proposed, not verified"
           - If agent is still exploring, preserve the exploration context, not just the current hypothesis

        2. **Preserve alternative considerations**:
           - When agent chooses approach A over B, preserve WHY B was rejected
           - Future context may reveal B was actually correct
           - Example: "Agent considered modifying _coeff_isneg vs modifying printers. Chose printers because \
           [reason]. Note: _coeff_isneg approach was not tested."

        3. **Preserve verification status**:
           - "Approach X was implemented and tested -> works/doesn't work" <- OK
           - "Approach X was decided" <- NOT OK, loses verification state
           - Always indicate: proposed / in-progress / tested-passed / tested-failed

        4. **Key insight preservation**:
           - When agent has a "moment of insight" after seeing specific output:
             - Preserve the output that triggered the insight
             - Preserve the insight itself
             - Example: "After seeing SQL output 'SELECT U0.id...', agent realized the bug is in \
             get_group_by_cols()"
           - Do NOT just say "agent found the bug location"

        ---

        ## ANTI-REDUNDANCY & CONSISTENCY RULES

        - Do NOT restate stable facts already captured in prior memory blocks
        - Only include NEW information or CHANGES introduced in selected_messages
        - If prior state is modified, express it as a delta (update / correction / refinement)
        - Avoid duplication across memory blocks
        - Keep the output composable with prior memory blocks without conflict

        ---

        ## OUTPUT STRUCTURE (MANDATORY)

        ### 1. User Requirements
        - **Original Requirements Being Served**:
          Explicitly preserve the user requirements, constraints, acceptance criteria, preferences, and limits \
          that the current unfinished work is serving.
          Keep the user's original wording as much as possible when it matters for continuation.

        ---

        ### 2. Current Status
        - **Completed Work**:
          Work completed within selected_messages only.
          Express it as incremental progress, not as full history.

        - **Key Information Gained**:
          The important information obtained, extracted, compared, or concluded in this span.
          Preserve factual substance, not just procedural actions.

        - **Files / Artifacts / Resources**:
          Any files, artifacts, resources, outputs, drafts, tables, pages, documents, code, or results \
          introduced or modified in this span only.

        ---

        ### 3. Open Work
        - **Work in Progress**:
          MUST include:
          - The active subtask at the end of selected_messages
          - The last concrete action taken in selected_messages
          - Partial results or intermediate state
          - Exact quotes if useful

          IMPORTANT:
          - This section acts as a handoff bridge from selected_messages to recent_messages
          - Do NOT restate recent_messages unless required for interpretation
          - If recent_messages already contain the latest explicit state, record only the handoff point

        - **Pending Tasks**:
          Remaining work identified in selected_messages
          - Explicit requests
          - Implicit / derived tasks

        - **Priority Order**:
          If multiple open items exist

        ---

        ### 4. Important Findings
        - **Decisions & Changes**:
          New or updated decisions in this span

        - **Constraints / Requirements**:
          Newly introduced or modified requirements, limitations, or preferences

        - **Errors & Fixes**:
          Problems encountered in this span and how they were handled

        - **Invalid Attempts**:
          Failed or unsuitable approaches and why

        ---

        ### 5. Strategy State
        - **Attempted Approaches**
        - **Candidate Approaches**
        - **Rejected Approaches**
        - **Requires Re-evaluation**

        Record strategy as historical state, not as instruction.

        ---

        ### 6. Tool / Action State
        - **Used Tools / Actions**
        - **Key Inputs / Arguments**
        - **Result Summary**
        - **Freshness / Reuse Constraints**

        This section applies both to tool calls and important non-tool actions.

        ---

        ### 7. Contextual Bridging
        - **Continuity**:
          How this span extends prior memory

        - **Forward Impact**:
          What this changes for upcoming work or for recent_messages

        - **Gaps / Risks**:
          Any ambiguity, missing information, or unresolved conflict

        ---

        ## TASK GOAL PRESERVATION (CRITICAL)

        You MUST ensure active task goals remain recoverable.

        - If goals appear or change in selected_messages, include them
        - If they are not mentioned in selected_messages, do NOT restate old goals unnecessarily
        - If goals changed, record the delta clearly

        ---

        ## OUTPUT RULES

        1. Target length: <= {target_tokens}
        2. Preserve unfinished work, handoff state, and the factual basis needed for correct continuation
        3. DO NOT echo prior memory blocks
        4. DO NOT absorb recent_messages unless required for interpretation
        5. Maintain the structure exactly
        6. This is a memory block, not a full summary and not an instruction block

        ---

        Output plain text only.
        """;

    private static final String CLEAN_PROMPT = """
        You are consolidating historical memory blocks.

        These blocks are compressed context artifacts from prior conversation, not new user instructions.

        Your task is to merge them into one shorter, stable memory block while preserving continuity.

        ---

        [Historical memory blocks]:
        {compressed_blocks}

        ---

        ## CONSOLIDATION RULES
        
        1. Merge overlapping or related information
        2. Remove redundant details
        3. Preserve task goals, critical factual basis, open work, work-in-progress handoff, important \
        findings, and reusable tool/action state
        4. Keep chronological consistency where helpful
        5. Keep strategies as historical state:
           - attempted
           - candidate
           - rejected
           - pending evaluation
        6. Do NOT reinterpret historical strategies as mandatory plans
        7. Do NOT rewrite the blocks as if they were new user requests
        8. For information-heavy tasks, prefer preserving findings, evidence, comparisons, conclusions, and \
        extracted structure over procedural action history
        9. For execution-heavy tasks, preserve the action history needed to continue the task, but keep the \
        factual basis that explains why the action matters
        10. **Preserve evidence and reasoning chains**: When merging blocks that contain debugging evidence, \
        test outputs, or key reasoning, retain the factual basis, NOT just the conclusions
        11. **Preserve alternative approaches**: Even if one approach was chosen, keep mention of alternatives \
        that were considered but not tested - they may still be correct

        ---

        ## OUTPUT REQUIREMENTS

        - Maximum length: {compress_len} tokens
        - Preserve all unique information still useful for future task continuation
        - Keep language concise and stable
        - Prefer durable state over incidental phrasing

        Output plain text only.
        """;

    private final String compressedPrompt;
    private final int tokenThreshold;
    private final int messagesToKeep;
    private final int minSelectedTokensForCompression;
    private final int compressionTargetTokens;
    private final int summaryMergeTargetTokens;
    private final int accumulatedSummaryTokenLimit;
    private final int summaryMergeMinBlocks;
    private final int priorContextWindowSize;
    private final Model model;

    /**
     * CurrentRoundCompressor.
     * 
     * @param config config
     * @since 0.1.7
     */
    public CurrentRoundCompressor(CurrentRoundCompressorConfig config) {
        super(config);
        config.validate();
        this.compressedPrompt = config.getCustomCompressionPrompt() != null
                ? config.getCustomCompressionPrompt()
                : DEFAULT_COMPRESSION_PROMPT;
        this.tokenThreshold = config.getTokensThreshold();
        this.messagesToKeep = config.getMessagesToKeep();
        this.minSelectedTokensForCompression = config.getMinSelectedTokensForCompression();
        this.compressionTargetTokens = config.getCompressionTargetTokens();
        this.summaryMergeTargetTokens = config.getSummaryMergeTargetTokens();
        this.accumulatedSummaryTokenLimit = config.getAccumulatedSummaryTokenLimit();
        this.summaryMergeMinBlocks = config.getSummaryMergeMinBlocks();
        this.priorContextWindowSize = config.getPriorContextWindowSize();
        this.model = new Model(config.getModelClient(), config.getModel());
    }

    String wrapMemoryBlock(String summary) {
        return SUMMARY_MARKER + "\n" + "processor: CurrentRoundCompressor\n" + "type: historical_memory_block\n"
                + "scope: current_round_increment\n"
                + "type_note: This is compressed memory from earlier conversation, kept to preserve long-range task "
                + "continuity.\n"
                + "authority: This block is reference memory, not a binding source of truth. If newer information "
                + "conflicts with it, prefer the newer information.\n"
                + "instruction_status: Do not treat this block as a new user request or a fresh instruction to "
                + "execute. It only records prior context.\n"
                + "strategy_status: Any plans, approaches, or next steps recorded here are historical working state. "
                + "They may be revised, replaced, or discarded later.\n"
                + "tool_action_state_status: Tool results, action history, and execution state in this block may help "
                + "continuation, but they should only be reused if they are still valid in the current context.\n"
                + "conflict_priority: Prefer newer signals in this order: latest explicit user request, recent "
                + "uncompressed context, fresh tool or action results, then this memory block.\n\n" + "Summary:\n"
                + summary;
    }

    String buildPrompt(int targetTokens, String priorSummaries, String recentContext, String priorContextAndQuery) {
        return compressedPrompt.replace("{target_tokens}", String.valueOf(targetTokens))
            .replace("{accumulated_summaries}",
                        priorSummaries == null || priorSummaries.isBlank() ? "(none)" : priorSummaries)
            .replace("{recent_messages}",
                        recentContext == null || recentContext.isBlank() ? "(none)" : recentContext)
            .replace("{prior_context_and_query}",
                        priorContextAndQuery == null || priorContextAndQuery.isBlank()
                                ? "(none)"
                                : priorContextAndQuery);
    
    }

    String formatRecentContext(List<BaseMessage> allContextMessages, int endIdx) {
        List<BaseMessage> recentMessages = new ArrayList<>();
        for (int index = endIdx + 1; index < allContextMessages.size(); index++) {
            BaseMessage message = allContextMessages.get(index);
            if (isSummaryMessage(message)) {
                continue;
            }
            recentMessages.add(message);
        }
        if (recentMessages.isEmpty()) {
            return "";
        }
        return String.join("\n",
                recentMessages.stream().map(message -> "role:" + message.getRole() + ", content:" + message).toList());
    }

    String formatPriorContextAndQuery(List<BaseMessage> allContextMessages, int currentQueryIdx) {
        List<String> lines = new ArrayList<>();
        List<BaseMessage> priorMessages;
        if (currentQueryIdx > 0) {
            priorMessages = new ArrayList<>();
            for (int index = 0; index < currentQueryIdx; index++) {
                BaseMessage message = allContextMessages.get(index);
                boolean isPlainUser = message instanceof UserMessage && !isSummaryMessage(message);
                boolean isPlainAssistant = message instanceof AssistantMessage assistantMessage
                        && (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty());
                if (isPlainUser || isPlainAssistant) {
                    priorMessages.add(message);
                }
            }
            int from = Math.max(priorMessages.size() - priorContextWindowSize, 0);
            priorMessages = new ArrayList<>(priorMessages.subList(from, priorMessages.size()));
        } else {
            priorMessages = List.of();
        }
        for (BaseMessage message : priorMessages) {
            lines.add("role:" + message.getRole() + ", content:" + message);
        }
        if (currentQueryIdx >= 0 && currentQueryIdx < allContextMessages.size()) {
            BaseMessage queryMessage = allContextMessages.get(currentQueryIdx);
            lines.add("\n--- Current User Intent ---\nrole:" + queryMessage.getRole() + ", content:" + queryMessage);
        }
        return String.join("\n", lines);
    }

    /**
     * onAddMessages.
     * 
     * @param context context
     * @param messagesToAdd messagesToAdd
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            contextMessages.addAll(messagesToAdd);
        }
        int lastUserIdx = getCompressIdx(contextMessages);
        if (lastUserIdx == -1) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }
        int keepStartIdx = Math.max(0, contextMessages.size() - messagesToKeep);
        int endIdx = keepStartIdx - 1;

        try {
            CompressResult compressResult = multiCompress(contextMessages, lastUserIdx, endIdx, context);
            if (compressResult.messages != null) {
                ContextEvent event = ContextEvent.builder()
                        .eventType(processorType())
                    .messagesToModify(compressResult.modifiedIndices).build();
                context.setMessages(compressResult.messages);
                return ProcessResult.ofMessages(event, List.of());
            }
            return ProcessResult.ofMessages(null, messagesToAdd);
        } catch (Exception exception) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "compress messages failed: " + exception.getMessage());
        }
    }

    /**
     * triggerAddMessages.
     * 
     * @param context context
     * @param messagesToAdd messagesToAdd
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        int messageSize = context.size() + (messagesToAdd != null ? messagesToAdd.size() : 0);
        if (messageSize < messagesToKeep) {
            return false;
        }
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        if (messagesToAdd != null) {
            allMessages.addAll(messagesToAdd);
        }
        int tokens = countMessagesTokens(allMessages, context.tokenCounter());
        if (tokens > tokenThreshold) {
            CurrentRoundCompressorConfig config = getConfig();
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context tokens " + tokens
                    + " exceeds threshold of " + config.getTokensThreshold());
            return true;
        }
        return false;
    }

    int getCompressIdx(List<BaseMessage> messages) {
        int compressedIdx = -1;
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage) {
                compressedIdx = index;
                break;
            }
        }
        if (compressedIdx == messages.size() - 1) {
            return -1;
        }
        if (compressedIdx < 0) {
            return -1;
        }
        int keepIndex = messages.size() - messagesToKeep;
        if (compressedIdx >= keepIndex) {
            return -1;
        }
        return compressedIdx;
    }

    CompressResult multiCompress(List<BaseMessage> contextMessages, int lastUserIdx, int endIdx, ModelContext context) {
        boolean isUpdated = false;
        List<Integer> modifiedIndices = new ArrayList<>();
        List<BaseMessage> workingMessages = contextMessages;
        int startIdx = lastUserIdx + 1;
        int actualEndIdx = endIdx;
        if (actualEndIdx >= startIdx) {
            actualEndIdx = findLastCompletedApiRoundEndIdx(workingMessages, startIdx, actualEndIdx);
        }
        if (actualEndIdx >= startIdx) {
            List<BaseMessage> messagesToCompress = new ArrayList<>(workingMessages.subList(startIdx, actualEndIdx + 1));
            BaseMessage compressedMessage =
                compress(messagesToCompress, context, workingMessages, actualEndIdx, lastUserIdx);
            if (compressedMessage != null) {
                workingMessages =
                    ContextUtils.replaceMessages(workingMessages, List.of(compressedMessage), startIdx, actualEndIdx);
                for (int index = startIdx; index <= actualEndIdx; index++) {
                    modifiedIndices.add(index);
                }
                isUpdated = true;
            }
        }

        for (int[] range : iterSummaryMergeRanges(workingMessages, summaryMergeMinBlocks)) {
            List<BaseMessage> oldCompressMessages = new ArrayList<>(workingMessages.subList(range[0], range[1] + 1));
            BaseMessage compressedMessage = mergeSummaryBlocks(context, oldCompressMessages);
            if (compressedMessage != null) {
                workingMessages =
                    ContextUtils.replaceMessages(workingMessages, List.of(compressedMessage), range[0], range[1]);
                for (int index = range[0]; index <= range[1]; index++) {
                    modifiedIndices.add(index);
                }
                isUpdated = true;
                break;
            }
        }
        return new CompressResult(isUpdated ? workingMessages : null, modifiedIndices);
    }

    BaseMessage compress(List<BaseMessage> messagesToCompress, ModelContext context,
            List<BaseMessage> allContextMessages, Integer compressEndIdx, Integer currentQueryIdx) {
        TokenCounter tokenCounter = context.tokenCounter();
        int inputTokens = countMessagesTokens(messagesToCompress, tokenCounter);
        if (inputTokens < minSelectedTokensForCompression) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] Skipping: selected span tokens (" + inputTokens
                    + ") < min_selected_tokens_for_compression (" + minSelectedTokensForCompression + ")");
            return null;
        }

        String priorSummaries = "";
        String recentContext = "";
        String priorContextAndQuery = "";
        if (allContextMessages != null) {
            List<Integer> summaryIndices = collectSummaryIndices(allContextMessages);
            if (!summaryIndices.isEmpty()) {
                priorSummaries = String.join("\n---\n", summaryIndices.stream()
                    .map(index -> allContextMessages.get(index).getContentAsString()).toList());
            }
            if (compressEndIdx != null) {
                recentContext = formatRecentContext(allContextMessages, compressEndIdx);
            }
            if (currentQueryIdx != null && currentQueryIdx >= 0) {
                priorContextAndQuery = formatPriorContextAndQuery(allContextMessages, currentQueryIdx);
            }
        }

        String filledPrompt = buildPrompt(compressionTargetTokens, priorSummaries, recentContext, priorContextAndQuery);
        String processedMessages = String.join("\n", messagesToCompress.stream()
            .map(message -> "role:" + message.getRole() + ", content:" + message).toList());
        filledPrompt = filledPrompt.replace("{selected_messages}", processedMessages);

        String summary = invokeModel(filledPrompt, "current-round compression");
        if (summary == null || summary.isBlank()) {
            return null;
        }
        int compressedTokens = countMessagesTokens(List.of(new UserMessage(summary)), tokenCounter);
        if (compressedTokens >= inputTokens) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] Skipping: compressed tokens (" + compressedTokens
                    + ") >= original (" + inputTokens + "), no benefit.");
            return null;
        }
        return new UserMessage(wrapMemoryBlock(summary));
    }

    BaseMessage mergeSummaryBlocks(ModelContext context, List<BaseMessage> oldCompressMessages) {
        TokenCounter tokenCounter = context.tokenCounter();
        int totalTokens = countMessagesTokens(oldCompressMessages, tokenCounter);
        if (totalTokens <= accumulatedSummaryTokenLimit) {
            return null;
        }
        List<String> mergedBlocks = new ArrayList<>();
        for (int index = 0; index < oldCompressMessages.size(); index++) {
            mergedBlocks
                .add("[MEMORY_BLOCK_" + (index + 1) + "]\n" + oldCompressMessages.get(index).getContentAsString());
        }
        String filledPrompt = CLEAN_PROMPT.replace("{compress_len}", String.valueOf(summaryMergeTargetTokens))
            .replace("{compressed_blocks}", mergedBlocks.isEmpty() ? "(none)" : String.join("\n\n", mergedBlocks));

        String summary = invokeModel(filledPrompt, "summary merge");
        if (summary == null || summary.isBlank()) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] failed to compress " + oldCompressMessages.size()
                    + " old compressed messages");
            return null;
        }
        Loggers.CONTEXT_ENGINE.info("[" + processorType() + "] compressed " + oldCompressMessages.size()
                + " old compressed messages into one");
        return new UserMessage(wrapMemoryBlock(summary));
    }

    /**
     * invokeModel.
     * 
     * @param prompt prompt
     * @param phase phase
     * @return the result
     * @since 0.1.7
     */
    private String invokeModel(String prompt, String phase) {
        try {
            AssistantMessage response =
                model.invoke(List.of(new UserMessage(prompt)), null, null, null, null, null, null, null, null, null);
            return response != null ? response.getContentAsString() : "";
        } catch (Exception exception) {
            Loggers.CONTEXT_ENGINE.warning("[" + processorType() + "] compression model invoke failed during " + phase
                    + ", skip current processor and continue remaining processors: " + exception.getMessage());
            return null;
        }
    }

    static boolean isSummaryMessage(BaseMessage message) {
        return message instanceof UserMessage && message.getContent() instanceof String content
                && content.startsWith(SUMMARY_MARKER);
    }

    static List<Integer> collectSummaryIndices(List<BaseMessage> messages) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (isSummaryMessage(messages.get(index))) {
                indices.add(index);
            }
        }
        return indices;
    }

    static int countMessagesTokens(List<BaseMessage> messages, TokenCounter tokenCounter) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (IllegalStateException exception) {
                Loggers.CONTEXT_ENGINE.warning("[CurrentRoundCompressor] token_counter failed, "
                        + "fallback to char-based estimate: " + exception.getMessage());
            }
        }
        return messages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
    }

    static int findLastCompletedApiRoundEndIdx(List<BaseMessage> messages, int startIdx, int endIdx) {
        if (endIdx < startIdx) {
            return endIdx;
        }
        List<BaseMessage> candidateMessages = messages.subList(startIdx, endIdx + 1);
        List<int[]> completedRounds = SessionMemoryManager.groupCompletedApiRounds(candidateMessages);
        if (completedRounds.isEmpty()) {
            return startIdx - 1;
        }
        int completedEnd = completedRounds.get(completedRounds.size() - 1)[1];
        return startIdx + completedEnd - 1;
    }

    static List<int[]> iterSummaryMergeRanges(List<BaseMessage> messages, int minBlocks) {
        List<int[]> ranges = new ArrayList<>();
        Integer startIdx = null;
        Integer previousIdx = null;
        for (int index = 0; index < messages.size(); index++) {
            if (isSummaryMessage(messages.get(index))) {
                if (startIdx == null) {
                    startIdx = index;
                }
                previousIdx = index;
                continue;
            }
            if (startIdx != null && previousIdx != null) {
                if (previousIdx - startIdx + 1 >= minBlocks) {
                    ranges.add(new int[]{startIdx, previousIdx});
                }
                startIdx = null;
                previousIdx = null;
            }
        }
        if (startIdx != null && previousIdx != null && previousIdx - startIdx + 1 >= minBlocks) {
            ranges.add(new int[]{startIdx, previousIdx});
        }
        return ranges;
    }

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
     */
    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    /**
     * saveState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    record CompressResult(List<BaseMessage> messages, List<Integer> modifiedIndices) {
    }
}
