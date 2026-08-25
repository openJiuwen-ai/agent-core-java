/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_evolver_common.agent;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bounds model-visible tool results and retries classified transient model failures.
 *
 * @since 0.1.13
 */
public class EvolverModelReliabilityRail extends AgentRail {
    /** Maximum historical tool result visible to the next model call. */
    public static final int MAX_TOOL_RESULT_CODE_POINTS = 8_192;
    /** Tool result prefix retained during pruning. */
    public static final int TOOL_RESULT_HEAD_CODE_POINTS = 4_096;
    /** Tool result suffix retained during pruning. */
    public static final int TOOL_RESULT_TAIL_CODE_POINTS = 1_024;
    /** Maximum model-level retries for transient provider failures. */
    public static final int MAX_TRANSIENT_RETRIES = 2;
    /** Maximum compaction retry for one overflowing model call. */
    public static final int MAX_CONTEXT_RETRIES = 1;
    private static final String CONTEXT_RETRY_KEY = "evolver_context_retry";
    private static final Pattern SERVER_STATUS = Pattern.compile(
            "(?:http(?: status)?|status(?: code)?)[ :=]*(5\\d\\d)");
    private static final Logger LOGGER = LoggerFactory.getLogger(EvolverModelReliabilityRail.class);
    private final String modelName;

    /**
     * Create a reliability rail.
     *
     * @param modelName configured model name
     */
    public EvolverModelReliabilityRail(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        if (context.getRetryAttempt() == 0) {
            context.getExtra().remove(CONTEXT_RETRY_KEY);
        }
        if (!(context.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }
        int pruned = pruneToolResults(inputs);
        if (pruned > 0) {
            LOGGER.info("Pruned {} oversized tool result(s) from model context", pruned);
        }
    }

    @Override
    public void onModelException(AgentCallbackContext context) {
        FailureKind kind = classify(context.getException());
        int attempt = context.getRetryAttempt();
        if (kind == FailureKind.CONTEXT_OVERFLOW && isContextRetryAvailable(context)
                && compactForRetry(context)) {
            context.getExtra().put(CONTEXT_RETRY_KEY, Boolean.TRUE);
            LOGGER.warn("Retrying model call after context compaction: attempt={}", attempt + 1);
            context.requestRetry(0.0);
            return;
        }
        if (kind.isTransient() && attempt < MAX_TRANSIENT_RETRIES) {
            double delay = Math.min(10.0, 0.5 * Math.pow(2.0, attempt));
            LOGGER.warn("Retrying transient model failure: kind={}, attempt={}, delaySeconds={}",
                    kind, attempt + 1, delay);
            context.requestRetry(delay);
        }
    }

    private boolean isContextRetryAvailable(AgentCallbackContext context) {
        return MAX_CONTEXT_RETRIES > 0
                && !Boolean.TRUE.equals(context.getExtra().get(CONTEXT_RETRY_KEY));
    }

    private int pruneToolResults(ModelCallInputs inputs) {
        List<Object> messages = inputs.getMessages();
        int protectedIndex = firstTrailingToolIndex(messages);
        List<Object> bounded = new ArrayList<>(messages.size());
        int pruned = 0;
        for (int index = 0; index < messages.size(); index++) {
            Object message = messages.get(index);
            Object replacement = index < protectedIndex ? boundedToolMessage(message) : message;
            bounded.add(replacement);
            if (replacement != message) {
                pruned++;
            }
        }
        inputs.setMessages(bounded);
        return pruned;
    }

    private static int firstTrailingToolIndex(List<Object> messages) {
        int index = messages.size();
        while (index > 0 && isToolMessage(messages.get(index - 1))) {
            index--;
        }
        return index;
    }

    private static boolean isToolMessage(Object message) {
        return message instanceof ToolMessage
                || message instanceof Map<?, ?> supplied && "tool".equals(supplied.get("role"));
    }

    private static Object boundedToolMessage(Object message) {
        if (message instanceof ToolMessage toolMessage
                && toolMessage.getContent() instanceof String content
                && codePoints(content) > MAX_TOOL_RESULT_CODE_POINTS) {
            return new ToolMessage(toolMessage.getRole(), prune(content),
                    toolMessage.getName(), toolMessage.getMetadata(), toolMessage.getToolCallId());
        }
        if (message instanceof Map<?, ?> supplied && "tool".equals(supplied.get("role"))
                && supplied.get("content") instanceof String content
                && codePoints(content) > MAX_TOOL_RESULT_CODE_POINTS) {
            Map<Object, Object> copy = new LinkedHashMap<>(supplied);
            copy.put("content", prune(content));
            return copy;
        }
        return message;
    }

    private boolean compactForRetry(AgentCallbackContext context) {
        if (context.getContext() == null) {
            return false;
        }
        String result = context.getContext().compressContext(
                List.of("FullCompactProcessor"), Map.of("model", modelName));
        if (!"compressed".equals(result)) {
            return false;
        }
        refreshModelInputs(context);
        return true;
    }

    private static void refreshModelInputs(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }
        List<BaseMessage> systemMessages = leadingSystemMessages(inputs.getMessages());
        ContextWindow window = context.getContext().getContextWindow(
                systemMessages, inputs.getTools(), null, null);
        inputs.setMessages(new ArrayList<>(window.getMessages()));
        inputs.setTools(window.getToolList());
    }

    private static List<BaseMessage> leadingSystemMessages(List<Object> messages) {
        List<BaseMessage> system = new ArrayList<>();
        for (Object message : messages) {
            if (!(message instanceof BaseMessage base) || !"system".equals(base.getRole())) {
                break;
            }
            system.add(base);
        }
        return system;
    }

    /**
     * Classify a model failure across its cause chain.
     *
     * @param failure model failure
     * @return classified failure kind
     */
    public static FailureKind classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            FailureKind kind = classifyOne(current);
            if (kind != FailureKind.OTHER) {
                return kind;
            }
        }
        return FailureKind.OTHER;
    }

    private static FailureKind classifyOne(Throwable failure) {
        if (failure instanceof EvolverGuardedModel.EmptyModelResponseException) {
            return FailureKind.EMPTY_RESPONSE;
        }
        if (failure instanceof SocketTimeoutException || failure instanceof HttpTimeoutException
                || failure instanceof InterruptedIOException) {
            return FailureKind.TIMEOUT;
        }
        String message = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (isContextOverflow(message)) {
            return FailureKind.CONTEXT_OVERFLOW;
        }
        if (message.contains("429") || message.contains("rate limit")
                || message.contains("too many requests")) {
            return FailureKind.RATE_LIMIT;
        }
        if (SERVER_STATUS.matcher(message).find()) {
            return FailureKind.SERVER;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return FailureKind.TIMEOUT;
        }
        if (failure instanceof IOException || isTransportFailure(message)) {
            return FailureKind.TRANSPORT;
        }
        return FailureKind.OTHER;
    }

    private static boolean isContextOverflow(String message) {
        return message.contains("context length") || message.contains("context window")
                || message.contains("maximum context") || message.contains("too many tokens")
                || message.contains("token limit") || message.contains("prompt too long");
    }

    private static boolean isTransportFailure(String message) {
        return message.contains("connection reset") || message.contains("connection refused")
                || message.contains("stream closed") || message.contains("unexpected eof")
                || message.contains("network is unreachable");
    }

    private static String prune(String content) {
        int count = codePoints(content);
        int headEnd = content.offsetByCodePoints(0, TOOL_RESULT_HEAD_CODE_POINTS);
        int tailStart = content.offsetByCodePoints(0, count - TOOL_RESULT_TAIL_CODE_POINTS);
        int omitted = count - TOOL_RESULT_HEAD_CODE_POINTS - TOOL_RESULT_TAIL_CODE_POINTS;
        return content.substring(0, headEnd)
                + "\n... [" + omitted + " code points omitted from model context] ...\n"
                + content.substring(tailStart);
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    /** Model failure kinds recognized by the shared reliability rail. */
    public enum FailureKind {
        EMPTY_RESPONSE(true),
        CONTEXT_OVERFLOW(false),
        TIMEOUT(true),
        RATE_LIMIT(true),
        SERVER(true),
        TRANSPORT(true),
        OTHER(false);

        private final boolean transientFailure;

        FailureKind(boolean transientFailure) {
            this.transientFailure = transientFailure;
        }

        private boolean isTransient() {
            return transientFailure;
        }
    }
}
