/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.modelclients.OpenAiCompatibleModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * TC-PRF-04: compares tool selection accuracy between full and filtered tool sets using a real LLM.
 *
 * <p>Requires environment variables:
 *
 * <ul>
 *   <li>{@code TC_LLM_API_KEY} — LLM API key
 *   <li>{@code TC_LLM_API_BASE} — LLM base URL (e.g. https://api.openai.com/v1)
 *   <li>{@code TC_LLM_MODEL} — model name (e.g. gpt-4o-mini)
 * </ul>
 *
 * If not set, test is skipped.
 */
@Tag("issue196-w11")
@Tag("TC-PRF-04")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(600)
class TCPRF04ToolSelectionAccuracyTest {
    private static final Path LOG_FILE = Path.of("target", "tcprf04_accuracy.log");

    private static final int TOOL_COUNT = 20;
    private static final int FOCUSED_COUNT = 5;

    private static String apiKey;
    private static String apiBase;
    private static String modelName;
    private static Model.ModelClient modelClient;

    private static final List<TestCase> TEST_CASES =
            List.of(
                    new TestCase("Search for today's news headlines.", "tool_search"),
                    new TestCase("Draw a bar chart of sales data.", "tool_chart"),
                    new TestCase("Delete the temporary file at /tmp/old.log.", "tool_delete_file"),
                    new TestCase("Translate 'hello world' to French.", "tool_translate"),
                    new TestCase("Calculate the square root of 144.", "tool_calculator"),
                    new TestCase("Read the contents of /home/user/config.yaml.", "tool_read_file"),
                    new TestCase("Send an email to team@example.com.", "tool_send_email"),
                    new TestCase("List all files in the current directory.", "tool_list_files"),
                    new TestCase("Extract text from the PDF report.pdf.", "tool_extract_pdf"),
                    new TestCase(
                            "Create a new GitHub issue with title 'Bug report'.",
                            "tool_create_issue"));

    @BeforeAll
    static void setUp() {
        apiKey = System.getenv("TC_LLM_API_KEY");
        apiBase = System.getenv("TC_LLM_API_BASE");
        modelName = System.getenv("TC_LLM_MODEL");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(),
                "TC_LLM_API_KEY not set; skipping real LLM accuracy test");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                apiBase != null && !apiBase.isBlank(),
                "TC_LLM_API_BASE not set; skipping real LLM accuracy test");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                modelName != null && !modelName.isBlank(),
                "TC_LLM_MODEL not set; skipping real LLM accuracy test");

        ModelClientConfig clientConfig =
                ModelClientConfig.builder()
                        .apiKey(apiKey)
                        .apiBase(apiBase)
                        .clientProvider("OpenAI")
                        .build();
        ModelRequestConfig requestConfig =
                ModelRequestConfig.builder().modelName(modelName).temperature(0.0D).build();
        modelClient = new OpenAiCompatibleModelClient(requestConfig, clientConfig);
    }

    @Test
    @Order(1)
    void fullToolSet_selectionAccuracy() {
        List<ToolInfo> allTools = buildAllTools();
        int correct = 0;
        for (TestCase testCase : TEST_CASES) {
            String selectedTool = invokeAndExtractToolCall(testCase.prompt(), allTools);
            if (testCase.expectedTool().equals(selectedTool)) {
                correct++;
            }
        }
        double accuracy = accuracy(correct);
        int accuracyPercent =
                BigDecimal.valueOf(correct)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(TEST_CASES.size()), 0, RoundingMode.HALF_UP)
                        .intValue();
        System.out.printf(
                "[TC-PRF-04] Full tool set (%d tools): accuracy = %d/%d = %d%%%n",
                allTools.size(), correct, TEST_CASES.size(), accuracyPercent);
        assertThat(accuracy).isGreaterThanOrEqualTo(0.5D);
    }

    @Test
    @Order(2)
    void filteredToolSet_selectionAccuracy() {
        List<ToolInfo> allTools = buildAllTools();
        List<ToolInfo> filteredTools =
                allTools.stream()
                        .filter(
                                tool -> {
                                    String name = tool.getName();
                                    boolean inFocusedGroup =
                                            TEST_CASES.stream()
                                                    .map(TestCase::expectedTool)
                                                    .anyMatch(name::equals);
                                    return inFocusedGroup
                                            || "tool_10".equals(name)
                                            || "tool_11".equals(name);
                                })
                        .toList();
        int correct = 0;
        for (TestCase testCase : TEST_CASES) {
            String selectedTool = invokeAndExtractToolCall(testCase.prompt(), filteredTools);
            if (testCase.expectedTool().equals(selectedTool)) {
                correct++;
            }
        }
        double accuracy = accuracy(correct);
        int accuracyPercent =
                BigDecimal.valueOf(correct)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(TEST_CASES.size()), 0, RoundingMode.HALF_UP)
                        .intValue();
        System.out.printf(
                "[TC-PRF-04] Filtered tool set (%d tools): accuracy = %d/%d = %d%%%n",
                filteredTools.size(), correct, TEST_CASES.size(), accuracyPercent);
        assertThat(accuracy).isGreaterThanOrEqualTo(0.5D);
    }

    @Test
    @Order(3)
    void filteredAccuracy_notWorseThanFull() {
        List<ToolInfo> allTools = buildAllTools();
        List<ToolInfo> filteredTools =
                allTools.stream()
                        .filter(
                                tool -> {
                                    String name = tool.getName();
                                    boolean inFocusedGroup =
                                            TEST_CASES.stream()
                                                    .map(TestCase::expectedTool)
                                                    .anyMatch(name::equals);
                                    return inFocusedGroup
                                            || "tool_10".equals(name)
                                            || "tool_11".equals(name);
                                })
                        .toList();
        int fullCorrect = 0;
        int filteredCorrect = 0;
        for (TestCase testCase : TEST_CASES) {
            if (testCase.expectedTool()
                    .equals(invokeAndExtractToolCall(testCase.prompt(), allTools))) {
                fullCorrect++;
            }
            if (testCase.expectedTool()
                    .equals(invokeAndExtractToolCall(testCase.prompt(), filteredTools))) {
                filteredCorrect++;
            }
        }
        System.out.printf(
                "[TC-PRF-04] Comparison: full %d/%d vs filtered %d/%d%n",
                fullCorrect, TEST_CASES.size(), filteredCorrect, TEST_CASES.size());
        assertThat(filteredCorrect).isGreaterThanOrEqualTo(fullCorrect - 1);
    }

    private String invokeAndExtractToolCall(String prompt, List<ToolInfo> tools) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                List<Map<String, Object>> openAiTools =
                        tools.stream()
                                .map(TCPRF04ToolSelectionAccuracyTest::toOpenAiFormat)
                                .collect(Collectors.toList());
                List<BaseMessage> messages =
                        List.of(
                                new com.openjiuwen.core.foundation.llm.schema.SystemMessage(
                                        "You are a helpful assistant. Choose the most appropriate"
                                                + " tool to complete the task."),
                                new com.openjiuwen.core.foundation.llm.schema.UserMessage(prompt));
                CompletionStage<AssistantMessage> future =
                        modelClient.invoke(
                                messages, ModelInvokeOptions.builder().tools(openAiTools).build());
                AssistantMessage response = future.toCompletableFuture().get(30, TimeUnit.SECONDS);
                Map<String, Object> dump = response.modelDump();
                Object toolCallsObj = dump.get("tool_calls");
                if (toolCallsObj instanceof List<?> toolCallsList && !toolCallsList.isEmpty()) {
                    Object firstCall = toolCallsList.get(0);
                    if (firstCall instanceof Map<?, ?> callMap) {
                        Object functionObj = callMap.get("function");
                        if (functionObj instanceof Map<?, ?> functionMap) {
                            Object nameObj = functionMap.get("name");
                            return nameObj == null ? "" : nameObj.toString();
                        }
                    }
                }
                return "";
            } catch (ExecutionException | InterruptedException | TimeoutException exception) {
                log(
                        String.format(
                                Locale.ROOT,
                                "Attempt %d failed for '%s': %s",
                                attempt,
                                prompt,
                                exception.getMessage()));
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ignored) {
                        log("Retry wait interrupted for prompt: " + prompt);
                        return "";
                    }
                }
            }
        }
        return "";
    }

    private static void log(String message) {
        String line = java.time.LocalDateTime.now() + " " + message;
        Loggers.AGENT.info("[TC-PRF-04] {}", message);
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(
                    LOG_FILE,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            Loggers.COMMON.warning("Failed to append TC-PRF-04 log", exception);
        }
    }

    private static Map<String, Object> toOpenAiFormat(ToolInfo toolInfo) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolInfo.getName());
        function.put("description", toolInfo.getDescription());
        function.put("parameters", toolInfo.getParameters());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "function");
        result.put("function", function);
        return result;
    }

    private static double accuracy(int correct) {
        return BigDecimal.valueOf(correct)
                .divide(BigDecimal.valueOf(TEST_CASES.size()), 6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static List<ToolInfo> buildAllTools() {
        List<ToolInfo> tools = new ArrayList<>();
        tools.add(toolInfo("tool_search", "Search the web for information."));
        tools.add(toolInfo("tool_chart", "Draw charts and graphs from data."));
        tools.add(toolInfo("tool_delete_file", "Delete a file from the filesystem."));
        tools.add(toolInfo("tool_translate", "Translate text between languages."));
        tools.add(toolInfo("tool_calculator", "Perform mathematical calculations."));
        tools.add(toolInfo("tool_read_file", "Read the contents of a file."));
        tools.add(toolInfo("tool_send_email", "Send an email to a recipient."));
        tools.add(toolInfo("tool_list_files", "List files in a directory."));
        tools.add(toolInfo("tool_extract_pdf", "Extract text from PDF documents."));
        tools.add(toolInfo("tool_create_issue", "Create a new GitHub issue."));
        for (int i = 10; i < TOOL_COUNT; i++) {
            tools.add(toolInfo("tool_" + i, "Generic tool " + i + " for padding."));
        }
        return tools;
    }

    private static ToolInfo toolInfo(String name, String description) {
        return ToolInfo.builder()
                .type("function")
                .name(name)
                .description(description)
                .parameters(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .build();
    }

    private record TestCase(String prompt, String expectedTool) {}
}
