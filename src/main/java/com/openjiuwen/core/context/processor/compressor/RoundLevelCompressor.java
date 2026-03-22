/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compresses multiple consecutive dialogue rounds of the same compression level
 * into a single summarized round.
 * <p>
 * Mirrors Python's {@code RoundLevelCompressor} from
 * {@code processor/compressor/round_level_compressor.py}.
 */
public class RoundLevelCompressor extends ContextProcessor {

    private static final String COMPRESS_LEVEL = "compress_level";

    private static final String DEFAULT_ROUND_COMPRESSION_PROMPT = """
            You are a Context Conversation Round Compression Assistant.
            Your task: compress and summarize the following consecutive conversation rounds into **one complete round** (user + assistant):
            
            📌 Strict Requirements:
            1. For each user message: summarize the user's intentions, questions, and instructions.
            2. For each assistant message: summarize the AI's responses, decisions, action results, and tool calls.
            3. Preserve all specific information related to tasks, decisions, constraints, numerical values, and tool calls.
            4. Do not create any new information; all content must be traceable to the original messages.
            5. Compress and summarize into **one user message and one assistant message**.
            6. The total token count should be 30% of the original.
            7. Output must be valid JSON:
            {
                "user_summary": "<refined user content>",
                "assistant_summary": "<refined assistant content>"
            }
            """;

    private final int roundsThreshold;
    private final String prompt;
    private final boolean keepLastRound;
    private final Model model;

    public RoundLevelCompressor(RoundLevelCompressorConfig config) {
        super(config);
        config.validate();
        this.roundsThreshold = config.getRoundsThreshold();
        this.prompt = config.getCustomizedCompressionPrompt() != null
                ? config.getCustomizedCompressionPrompt()
                : DEFAULT_ROUND_COMPRESSION_PROMPT;
        this.keepLastRound = config.isKeepLastRound();
        this.model = config.getModelClient() != null ? new Model(config.getModelClient(), config.getModel()) : null;
    }

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        RoundLevelCompressorConfig config = getConfig();
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);

        List<DialogueRound> rounds = iterRounds(allMessages);
        List<DialogueRound> filteredRounds = filterOutLatestRound(rounds, keepLastRound);

        TokenCounter tokenCounter = context.tokenCounter();
        int tokens = 0;
        boolean exceedTokenLimit = false;
        if (tokenCounter != null) {
            int contextToken = tokenCounter.countMessages(context.getMessages());
            int addToken = tokenCounter.countMessages(messagesToAdd);
            tokens = contextToken + addToken;
        }
        if (tokens > config.getTokensThreshold()) {
            exceedTokenLimit = true;
        }

        return !findBestRoundWindow(filteredRounds).isEmpty() && exceedTokenLimit;
    }

    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);

        List<DialogueRound> rounds = iterRounds(allMessages);
        List<DialogueRound> filteredRounds = filterOutLatestRound(rounds, keepLastRound);
        List<List<DialogueRound>> targetWindows = findBestRoundWindow(filteredRounds);

        if (targetWindows.isEmpty()) {
            Loggers.CONTEXT_ENGINE.warning(
                    "[RoundLevelCompressor] trigger fired but no compressible window found");
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        CompressResult compressResult = compressRounds(allMessages, targetWindows, context);
        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();
        for (int i = 0; i < compressResult.allStarts.size(); i++) {
            int start = compressResult.allStarts.get(i);
            int end = compressResult.allEnds.get(i);
            for (int j = start; j <= end; j++) {
                event.getMessagesToModify().add(j);
            }
        }
        context.setMessages(compressResult.messages);
        return ProcessResult.ofMessages(event, Collections.emptyList());
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    // ==================== Dialogue Round Helpers ====================

    /**
     * A single user-assistant dialogue round.
     */
    record DialogueRound(
            BaseMessage user,
            BaseMessage ai,
            Integer level,
            int startIdx,
            int endIdx
    ) {
    }

    private record CompressResult(
            List<BaseMessage> messages,
            List<Integer> allStarts,
            List<Integer> allEnds
    ) {
    }

    private List<DialogueRound> iterRounds(List<BaseMessage> messages) {
        List<DialogueRound> result = new ArrayList<>();
        int i = 0;
        while (i < messages.size() - 1) {
            BaseMessage u = messages.get(i);
            BaseMessage a = messages.get(i + 1);
            if (isValidDialogueRound(u, a)) {
                result.add(new DialogueRound(u, a, getCompressLevel(a), i, i + 1));
                i += 2;
            } else {
                i += 1;
            }
        }
        return result;
    }

    private static boolean isValidDialogueRound(BaseMessage u, BaseMessage a) {
        if (!"user".equals(u.getRole()) || !"assistant".equals(a.getRole())) {
            return false;
        }
        if (a instanceof AssistantMessage am) {
            return am.getToolCalls() == null || am.getToolCalls().isEmpty();
        }
        return true;
    }

    private List<List<DialogueRound>> findBestRoundWindow(List<DialogueRound> rounds) {
        List<List<DialogueRound>> allQualifiedWindows = new ArrayList<>();
        List<DialogueRound> window = new ArrayList<>();

        for (DialogueRound r : rounds) {
            if (window.isEmpty()) {
                window.add(r);
                continue;
            }

            DialogueRound last = window.get(window.size() - 1);
            if (r.startIdx() != last.endIdx() + 1) {
                window = new ArrayList<>();
                window.add(r);
                continue;
            }

            if (!java.util.Objects.equals(r.level(), last.level())) {
                window = new ArrayList<>();
                window.add(r);
                continue;
            }

            window.add(r);

            if (window.size() >= roundsThreshold) {
                List<DialogueRound> candidate = new ArrayList<>(
                        window.subList(window.size() - roundsThreshold, window.size()));
                allQualifiedWindows.add(candidate);
                window = new ArrayList<>();
            }
        }
        return allQualifiedWindows;
    }

    private static List<DialogueRound> filterOutLatestRound(
            List<DialogueRound> rounds, boolean preserve) {
        if (!preserve || rounds.size() <= 1) {
            return rounds;
        }
        return new ArrayList<>(rounds.subList(0, rounds.size() - 1));
    }

    private CompressResult compressRounds(
            List<BaseMessage> messages,
            List<List<DialogueRound>> targetWindows,
            ModelContext context) {

        List<BaseMessage> newMessages = new ArrayList<>(messages);
        List<Integer> allStarts = new ArrayList<>();
        List<Integer> allEnds = new ArrayList<>();

        // Process windows in reverse order to maintain correct indices
        for (int w = targetWindows.size() - 1; w >= 0; w--) {
            List<DialogueRound> window = targetWindows.get(w);
            int baseLevel = window.get(0).level() != null ? window.get(0).level() : 0;
            int newLevel = baseLevel + 1;

            BaseMessage[] pair = compressRoundPairs(window, context);
            if (pair == null || pair[0] == null || pair[1] == null) {
                Loggers.CONTEXT_ENGINE.warning(
                        "[RoundLevelCompressor] Compression failed, return original messages");
                allStarts.add(window.get(0).startIdx());
                allEnds.add(window.get(window.size() - 1).endIdx());
                continue;
            }

            BaseMessage newUser = pair[0];
            BaseMessage newAi = pair[1];

            // Set compression level metadata on offload messages
            if (newUser instanceof OffloadMixin offloadUser) {
                if (offloadUser.getMetadata() != null) {
                    offloadUser.getMetadata().put(COMPRESS_LEVEL, newLevel);
                }
            }
            if (newAi instanceof OffloadMixin offloadAi) {
                if (offloadAi.getMetadata() != null) {
                    offloadAi.getMetadata().put(COMPRESS_LEVEL, newLevel);
                }
            }

            int start = window.get(0).startIdx();
            int end = window.get(window.size() - 1).endIdx();
            newMessages = ContextUtils.replaceMessages(newMessages, List.of(newUser, newAi), start, end);

            allStarts.add(start);
            allEnds.add(end);
        }

        Collections.reverse(allStarts);
        Collections.reverse(allEnds);
        return new CompressResult(newMessages, allStarts, allEnds);
    }

    private BaseMessage[] compressRoundPairs(List<DialogueRound> rounds, ModelContext context) {
        try {
            List<Map<String, Object>> conversationPairs = new ArrayList<>();
            for (DialogueRound r : rounds) {
                conversationPairs.add(Map.of(
                        "user", r.user().getContentAsString(),
                        "assistant", r.ai().getContentAsString()));
            }

            List<BaseMessage> messages = List.of(
                    new SystemMessage(prompt),
                    new UserMessage("conversation_rounds:" + conversationPairs));

            AssistantMessage response = model.invoke(
                    messages, null, null, null, null, null, null,
                    new JsonOutputParser(), null, null);

            Object parserContent = response.getParserContent();
            if (parserContent instanceof Map<?, ?> summaryMap) {
                Object userObj = summaryMap.get("user_summary");
                Object aiObj = summaryMap.get("assistant_summary");
                String userSummary = userObj != null ? String.valueOf(userObj) : "";
                String assistantSummary = aiObj != null ? String.valueOf(aiObj) : "";

                if (!userSummary.isEmpty() && !assistantSummary.isEmpty()) {
                    List<BaseMessage> userMsgs = rounds.stream().map(DialogueRound::user).toList();
                    List<BaseMessage> aiMsgs = rounds.stream().map(DialogueRound::ai).toList();

                    BaseMessage newUser = offloadMessages("user", userSummary, userMsgs, context);
                    BaseMessage newAi = offloadMessages("assistant", assistantSummary, aiMsgs, context);
                    return new BaseMessage[]{newUser, newAi};
                }
            }

            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] Round pair compression failed");
            return null;
        } catch (Exception e) {
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] compression error: " + e.getMessage());
            return null;
        }
    }

    private static int getCompressLevel(BaseMessage message) {
        if (!(message instanceof OffloadMixin offloadMsg)) {
            return 0;
        }
        Map<String, Object> metadata = offloadMsg.getMetadata();
        if (metadata == null) {
            return 0;
        }
        Object level = metadata.get(COMPRESS_LEVEL);
        return level instanceof Number num ? num.intValue() : 0;
    }

    /**
     * Compress a list of messages of a single role into one summarized message.
     * <p>
     * Mirrors Python's {@code RoundLevelCompressor._compress_messages(messages, role, context)}.
     *
     * @param messages the messages to compress
     * @param role     the role for the resulting compressed message
     * @param context  the model context (for offloading)
     * @return the compressed offload message, or null on failure
     */
    private BaseMessage compressMessages(List<BaseMessage> messages, String role, ModelContext context) {
        try {
            List<BaseMessage> processed = new ArrayList<>();
            for (BaseMessage m : messages) {
                processed.add(new UserMessage("role:" + m.getRole() + ", content:" + m.getContentAsString()));
            }

            List<BaseMessage> invokeMessages = new ArrayList<>();
            invokeMessages.add(new SystemMessage(prompt));
            invokeMessages.addAll(processed);

            AssistantMessage response = model.invoke(
                    invokeMessages, null, null, null, null, null, null,
                    new JsonOutputParser(), null, null);

            Object parserContent = response.getParserContent();
            if (parserContent instanceof Map<?, ?> summaryMap) {
                Object summaryObj = summaryMap.get("summary");
                String summary = summaryObj != null ? String.valueOf(summaryObj) : "";
                if (!summary.isEmpty()) {
                    return offloadMessages(role, summary, messages, context);
                }
            }
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] Invalid summary from model");
            return null;
        } catch (Exception e) {
            Loggers.CONTEXT_ENGINE.warning("[RoundLevelCompressor] compression error: " + e.getMessage());
            return null;
        }
    }
}
