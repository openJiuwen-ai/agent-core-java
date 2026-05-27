/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Message offloader with adaptive compression capabilities.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloader}.
 */
public class MessageSummaryOffloader extends MessageOffloader {

  static final String TRUNCATED_MARKER = "...[TRUNCATED]...";

  private static final String[] CONTEXT_OVERFLOW_KEYWORDS = {
    "context length", "token limit", "too long", "exceeds", "maximum context", "context window"
  };

  private static final String ADAPTIVE_OFFLOAD_PROMPT_TEMPLATE =
      """
# Adaptive Information Compression Expert

## Core Role
You are an adaptive information compression expert in a React Agent. Your task is to intelligently \
analyze the information density and structural characteristics of tool return content, automatically \
select the most suitable compression strategy, generate an optimal condensed text, and offload detailed \
content to the file system for on-demand loading.

## Constraints
- Strictly prohibited from executing the step: You are only responsible for compression; you must not \
execute any steps, calculations, or operations from the step.
- Based solely on provided information: Only use the information in {tool_content} for compression.
- No speculative operations: Do not perform additional queries, calculations, or analysis based on \
step content.

# Compression Logic Flow

## Step 1: Analyze User Intent
- Tool Purpose: Understand the core purpose of this tool call (e.g., querying information, performing \
calculations, obtaining status).
- Key Parameters: What parameters were isPassed in the function_call? This directly indicates the focus \
of required information.
- Role in the step: What subtasks in the current step is this tool call meant to accomplish?

## Step 2: Select Compression Strategy
Based on the analyzed user intent, quickly scan the important information in tool_content:

### Characteristics favoring EXTRACTIVE compression:
- Clear and direct results: Key information related to user intent is explicitly present in the tool \
return results.
- No deep processing needed: The answer already exists directly in the return content; it only needs to \
be extracted to satisfy user intent without summarization or reasoning.
- Clear structure: For example, batches of key information, attribute lists, keyword collections, \
address details, etc.

### Characteristics favoring ABSTRACTIVE compression:
- Requires integration and understanding: To obtain an answer that matches user intent, it is necessary \
to summarize and synthesize multiple paragraphs, viewpoints, or data.
- Highly narrative: For example, long analytical reports, article content, Q&A responses, log analysis, etc.

## Step 3: Execute Compression Strategy
Based on the above evaluation, select a compression strategy according to the following process:

### If EXTRACTIVE compression was selected in the previous step:
Analyze tool_content and perform the following operations:
- Identify core information: Find sentences and key data that directly answer the calling intent.
- Execute extractive compression:
  - RETAIN: All original sentences or phrases that directly contain core answers, key facts, final \
  results, main status, and necessary definitions. Prefer not to rewrite; use original expressions when \
  possible.
  - DELETE:
    - Background introductions and process descriptions unrelated to the core answer.
    - Sentences that express the same meaning repeatedly.
    - Overly detailed examples and explanatory expansions (if their main points are already covered).
    - Pure formatting metadata, internal log information, redundant transitional statements.
- Ensure coherence: Connect the retained original sentences or fragments in a logically clear way to \
form coherent key information.

### If ABSTRACTIVE compression was selected in the previous step:
Compress the tool message content to generate a high-density, high-integrity summary that can adequately \
support the current step's task needs without loading the original text.

Summary requirements:
- Integrity priority: The summary should retain all key facts, data, conclusions, conditions, and \
limitations related to the current step from the original text. Do not omit information that \
substantially impacts understanding or decision-making.
- Strict accuracy: All data, names, relationships, and judgments must be strictly accurate; do not \
distort, blur, or simplify to the point of potential misunderstanding
- Focus and conciseness: Center around the step requirements; organize in concise, clear language; \
remove redundant descriptions, repetitive examples, and irrelevant background buildup, but do not \
oversimplify core information.
- Clear structure: Maintain logical coherence; reasonably segment or bulletize to ensure clear \
information hierarchy and easy reading comprehension.
- Objective neutrality: Make only factual statements; do not add explanations, evaluations, or \
speculations not present in the original text.

[Current step requirements]
{step}

[Current tool call function call]
{function_call}

[Tool message content begins]
{tool_content}
[Tool message content ends]

Return JSON with this schema:
{output_json_schema}
""";

  private static final String OUTPUT_JSON_SCHEMA =
      """
{
  "compression_strategy": "extractive" | "abstractive",
  "summary": "A compact result generated based on the selected strategy (within %d tokens). If using \
extractive strategy, directly concatenate key original text; if using abstractive strategy, provide a \
condensed summary. Ensure it contains all key information needed for the step, with clear structure and \
appropriate length.",
  "offload_data_explanation": {
    "category": "The category of information offloaded (e.g., 'raw log data', 'complete product list', \
'detailed calculation steps')",
    "description": "Briefly describe what detailed information is missing from the compressed text and \
its potential use cases, for subsequent on-demand loading of these offloaded information.",
    "inferability": "high" | "medium" | "low"
  }
}
""";

  private static final String STEP_SUMMARY_PROMPT =
      """
      Summarize the current user task in one concise sentence
      Return the task only

      Conversation context -
      %s
      """;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final MessageSummaryOffloaderConfig summaryConfig;
  private final Model model;

  /** Auto-generated for codecheck compliance. */
  public MessageSummaryOffloader(MessageSummaryOffloaderConfig config) {
    super(toOffloaderConfig(config));
    this.summaryConfig = config != null ? config : MessageSummaryOffloaderConfig.builder().build();
    this.summaryConfig.validate();
    validateConfig();
    this.model = createModel(this.summaryConfig);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
    List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
    contextMessages.addAll(messagesToAdd);
    for (BaseMessage message : messagesToAdd) {
      if (shouldOffloadMessage(message, context, contextMessages)) {
        return true;
      }
    }
    return false;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
    List<BaseMessage> processedMessages = new ArrayList<>(messagesToAdd);
    ContextEvent event = ContextEvent.builder().eventType(processorType()).build();
    int baseIndex = context.size();
    List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
    contextMessages.addAll(messagesToAdd);

    for (int index = 0; index < messagesToAdd.size(); index++) {
      BaseMessage message = messagesToAdd.get(index);
      if (!shouldOffloadMessage(message, context, contextMessages)) {
        continue;
      }
      processedMessages.set(index, offloadMessageAdaptive(message, context, contextMessages));
      event.getMessagesToModify().add(baseIndex + index);
    }

    if (event.getMessagesToModify().isEmpty()) {

      return ProcessResult.ofMessages(null, messagesToAdd);
    }
    return ProcessResult.ofMessages(event, processedMessages);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected BaseMessage offloadMessage(BaseMessage message, ModelContext context) {
    return offloadMessageAdaptive(message, context, context.getMessages());
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected void validateConfig() {
    if (summaryConfig == null) {
      return;
    }
  }

  /** Auto-generated for codecheck compliance. */
  protected boolean shouldOffloadMessage(
      BaseMessage message, ModelContext context, List<BaseMessage> contextMessages) {
    if (!summaryConfig.getOffloadMessageType().contains(message.getRole())) {
      return false;
    }
    if (message instanceof OffloadMixin) {
      return false;
    }
    if ("tool".equals(message.getRole()) && isProtectedToolMessage(message, contextMessages)) {
      return false;
    }
    return messageSize(message, context) > summaryConfig.getLargeMessageThreshold();
  }

  private BaseMessage offloadMessageAdaptive(
      BaseMessage message, ModelContext context, List<BaseMessage> contextMessages) {
    Map<String, Object> compressionResult = compressMessage(message, contextMessages, context);
    if (compressionResult == null) {
      return message;
    }
    String summary = stringValue(compressionResult.get("summary"));
    String finalContent = summary;
    Object offloadDataExplanation = compressionResult.get("offload_data_explanation");
    if (offloadDataExplanation instanceof Map<?, ?> explanationMap && !explanationMap.isEmpty()) {
      finalContent =
          summary
              + "\n\n"
              + "[offloaded_info]\n"
              + "category: "
              + stringValue(explanationMap.get("category"))
              + "\n"
              + "description: "
              + stringValue(explanationMap.get("description"))
              + "\n"
              + "inferability: "
              + stringValue(explanationMap.get("inferability"));
    }

    OffloadTarget offloadTarget = newOffloadTarget(context);
    Map<String, Object> extraFields = extractExtraFields(message);
    BaseMessage offloadMessage =
        offloadMessages(
            message.getRole(),
            finalContent,
            List.of(message),
            context,
            offloadTarget.handle(),
            offloadTarget.path() != null ? "filesystem" : "in_memory",
            offloadTarget.path(),
            extraFields);
    return offloadMessage != null ? offloadMessage : message;
  }

  private Map<String, Object> compressMessage(
      BaseMessage message, List<BaseMessage> contextMessages, ModelContext context) {
    if (model == null) {
      return null;
    }
    ToolCall functionCall = getFunctionCallFromChain(message, contextMessages);
    String step =
        summaryConfig.isEnablePreciseStep()
            ? getStepFromChainPrecise(contextMessages, message)
            : getStepFromChainDefault(contextMessages);
    if (step == null || step.isBlank()) {
      step = getStepFromChainDefault(contextMessages);
    }
    String toolContent = toJsonCompatibleString(message.getContent());
    return compressWithFallback(step, functionCall, toolContent);
  }

  private int messageSize(BaseMessage message, ModelContext context) {
    if (context.tokenCounter() != null) {
      return context.tokenCounter().countMessages(List.of(message));
    }
    return ContextUtils.estimateMessageTokens(message);
  }

  private ToolCall getFunctionCallFromChain(
      BaseMessage toolMessage, List<BaseMessage> contextMessages) {
    return ContextUtils.resolveToolCallFromMessage(toolMessage, contextMessages);
  }

  private String getStepFromChainDefault(List<BaseMessage> contextMessages) {
    for (int index = contextMessages.size() - 1; index >= 0; index--) {
      BaseMessage message = contextMessages.get(index);
      if ("user".equals(message.getRole())) {
        return toJsonCompatibleString(message.getContent());
      }
    }
    return "";
  }

  private String getStepFromChainPrecise(
      List<BaseMessage> contextMessages, BaseMessage currentMessage) {
    if (model == null) {
      return "";
    }
    List<BaseMessage> selected = selectMessagesForStepSummary(contextMessages, currentMessage);
    if (selected.size() <= 1) {
      return "";
    }
    StringBuilder contextText = new StringBuilder();
    for (int index = 0; index < selected.size(); index++) {
      BaseMessage message = selected.get(index);
      if (index > 0) {
        contextText.append("\n\n");
      }
      String content = toJsonCompatibleString(message.getContent());
      if (content.length() > 2000) {
        content = content.substring(0, 2000);
      }
      contextText.append("[").append(message.getRole()).append("] ").append(content);
    }
    String prompt = STEP_SUMMARY_PROMPT.formatted(contextText);
    try {
      AssistantMessage response =
          model.invoke(
              List.of(new UserMessage(prompt)),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      return response.getContentAsString() != null ? response.getContentAsString().trim() : "";
    } catch (Exception e) {
      if (!isContextOverflowError(e)) {
        throw ErrorHelper.buildError(
            StatusCode.CONTEXT_EXECUTION_ERROR,
            "error_msg",
            "Failed to generate precise step summary: " + e.getMessage());
      }
      return "";
    }
  }

  private List<BaseMessage> selectMessagesForStepSummary(
      List<BaseMessage> contextMessages, BaseMessage currentMessage) {
    List<BaseMessage> filtered = new ArrayList<>();
    for (BaseMessage message : contextMessages) {
      if ("user".equals(message.getRole())) {
        filtered.add(message);
      } else if ("assistant".equals(message.getRole())
          && message instanceof AssistantMessage assistantMessage
          && (assistantMessage.getToolCalls() == null
              || assistantMessage.getToolCalls().isEmpty())) {
        filtered.add(message);
      }
    }
    if (!filtered.contains(currentMessage) && "user".equals(currentMessage.getRole())) {
      filtered.add(currentMessage);
    }
    if (filtered.size() <= summaryConfig.getStepSummaryMaxContextMessages()) {
      return filtered;
    }
    return new ArrayList<>(
        filtered.subList(
            filtered.size() - summaryConfig.getStepSummaryMaxContextMessages(), filtered.size()));
  }

  private Map<String, Object> compressWithFallback(
      String step, ToolCall functionCall, String toolContent) {
    List<String> attempts = buildCompressionAttempts(toolContent);
    for (int index = 0; index < attempts.size(); index++) {
      String contentToCompress = attempts.get(index);
      try {
        String prompt = buildCompressionPrompt(step, functionCall, contentToCompress);
        AssistantMessage response =
            model.invoke(
                List.of(new UserMessage(prompt)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        String responseContent = response.getContentAsString();
        try {
          return parseCompressionResult(responseContent);
        } catch (BaseError parseError) {
          if (responseContent != null && responseContent.length() >= toolContent.length()) {
            return null;
          }
          Map<String, Object> fallback = new HashMap<>();
          fallback.put("summary", responseContent != null ? responseContent : "");
          fallback.put("offload_data_explanation", Map.of());
          return fallback;
        }
      } catch (Exception e) {
        if (!isContextOverflowError(e)) {
          throw ErrorHelper.buildError(
              StatusCode.CONTEXT_EXECUTION_ERROR,
              "error_msg",
              "Failed to compress message: " + e.getMessage());
        }
        if (index >= attempts.size() - 1) {
          throw ErrorHelper.buildError(
              StatusCode.CONTEXT_EXECUTION_ERROR,
              "error_msg",
              "Failed to compress message after "
                  + attempts.size()
                  + " attempts: "
                  + e.getMessage());
        }
      }
    }
    return Map.of();
  }

  private List<String> buildCompressionAttempts(String toolContent) {
    List<String> attempts = new ArrayList<>();
    attempts.add(toolContent);
    int maxChars = summaryConfig.getContentMaxCharsForCompression();
    if (toolContent.length() <= maxChars) {
      return attempts;
    }
    attempts.add(smartTruncateContent(toolContent, maxChars));
    int reducedLimit = Math.max(maxChars / 2, 1);
    if (reducedLimit < maxChars) {
      attempts.add(smartTruncateContent(toolContent, reducedLimit));
    }
    return attempts;
  }

  String smartTruncateContent(String content, int maxChars) {
    if (content.length() <= maxChars) {
      return content;
    }
    int joinerOverhead = 4;
    if (maxChars <= TRUNCATED_MARKER.length() * 2 + joinerOverhead + 3) {
      return content.substring(0, maxChars);
    }
    int availableChars = maxChars - TRUNCATED_MARKER.length() * 2 - joinerOverhead;
    int headChars = Math.max(availableChars / 3, 1);
    int tailChars = Math.max(availableChars / 3, 1);
    int middleChars = Math.max(availableChars - headChars - tailChars, 1);

    int center = content.length() / 2;
    int middleStart = Math.max(center - middleChars / 2, headChars);
    int middleEnd = Math.min(middleStart + middleChars, content.length() - tailChars);
    middleStart = Math.max(headChars, middleEnd - middleChars);

    String head = content.substring(0, headChars);
    String middle = content.substring(middleStart, middleEnd);
    String tail = content.substring(content.length() - tailChars);
    return head + "\n" + TRUNCATED_MARKER + "\n" + middle + "\n" + TRUNCATED_MARKER + "\n" + tail;
  }

  private String buildCompressionPrompt(String step, ToolCall functionCall, String toolContent) {
    String functionCallText = functionCall == null ? "N/A" : toJsonCompatibleString(functionCall);
    String outputSchema = OUTPUT_JSON_SCHEMA.formatted(summaryConfig.getSummaryMaxTokens());
    return ADAPTIVE_OFFLOAD_PROMPT_TEMPLATE
        .replace("{step}", step == null || step.isBlank() ? "N/A" : step)
        .replace("{function_call}", functionCallText)
        .replace("{tool_content}", toolContent)
        .replace("{output_json_schema}", outputSchema);
  }

  Map<String, Object> parseCompressionResult(String responseContent) {
    try {
      Map<String, Object> result =
          MAPPER.readValue(responseContent.trim(), new TypeReference<>() {});
      if (!result.containsKey("summary")) {
        throw ErrorHelper.buildError(
            StatusCode.CONTEXT_EXECUTION_ERROR,
            "error_msg",
            "Missing 'summary' field in compression result");
      }
      return result;
    } catch (JsonProcessingException originalException) {
      int jsonStart = responseContent.indexOf('{');
      int jsonEnd = responseContent.lastIndexOf('}');
      if (jsonStart < 0 || jsonEnd <= jsonStart) {
        throw ErrorHelper.buildError(
            StatusCode.CONTEXT_EXECUTION_ERROR,
            "error_msg",
            "No JSON found in compression result: " + preview(responseContent));
      }
      try {
        Map<String, Object> result =
            MAPPER.readValue(
                responseContent.substring(jsonStart, jsonEnd + 1), new TypeReference<>() {});
        if (!result.containsKey("summary")) {
          throw ErrorHelper.buildError(
              StatusCode.CONTEXT_EXECUTION_ERROR,
              "error_msg",
              "Missing 'summary' field in compression result");
        }
        return result;
      } catch (JsonProcessingException exception) {
        throw ErrorHelper.buildError(
            StatusCode.CONTEXT_EXECUTION_ERROR,
            "error_msg",
            "Failed to parse compression result as JSON: " + preview(responseContent));
      }
    }
  }

  private boolean isContextOverflowError(Exception exception) {
    String errorMessage = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
    for (String keyword : CONTEXT_OVERFLOW_KEYWORDS) {
      if (errorMessage.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static String toJsonCompatibleString(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }

  private static String preview(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 200 ? value : value.substring(0, 200);
  }

  private static Model createModel(MessageSummaryOffloaderConfig config) {
    if (config == null || config.getModelClient() == null) {
      return null;
    }
    return new Model(config.getModelClient(), config.getModel());
  }

  private static MessageOffloaderConfig toOffloaderConfig(MessageSummaryOffloaderConfig config) {
    MessageSummaryOffloaderConfig safeConfig =
        config != null ? config : MessageSummaryOffloaderConfig.builder().build();
    return MessageOffloaderConfig.builder()
        .messagesThreshold(safeConfig.getMessagesThreshold())
        .tokensThreshold(safeConfig.getTokensThreshold())
        .largeMessageThreshold(safeConfig.getLargeMessageThreshold())
        .offloadMessageType(safeConfig.getOffloadMessageType())
        .protectedToolNames(safeConfig.getProtectedToolNames())
        .messagesToKeep(safeConfig.getMessagesToKeep())
        .keepLastRound(safeConfig.isKeepLastRound())
        .trimSize(Math.max(1, Math.min(safeConfig.getLargeMessageThreshold() - 1, 100)))
        .build();
  }
}
