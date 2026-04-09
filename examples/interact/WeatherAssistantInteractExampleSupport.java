  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package examples.interact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.Start;
import examples.utils.SharedExampleApiConfigLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared implementation for the real-weather interact example.
 */
final class WeatherAssistantInteractExampleSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String WORKFLOW_ID = "weather_assistant_interact_java_example";
    private static final String DEFAULT_QUERY = "明天我要出差，帮我查天气并给出穿衣建议";
    private static final String DEFAULT_WEATHER_API_URL = "https://uapis.cn/api/v1/misc/weather";
    private static final double MOCK_BALANCE = 0.0080;
    private static final String SUMMARY_SYSTEM_PROMPT = "你是一个天气出行助手。"
            + "你只能基于给定的天气事实回答，不要编造不存在的天气信息。"
            + "请用简洁中文给出 2 到 3 句建议，优先覆盖天气结论、穿衣建议和出行提醒。";

    private WeatherAssistantInteractExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        Workflow workflow = buildWorkflow();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();
        String scriptedInput = args.length == 0 ? null : String.join(" ", args);
        String activeSessionId = null;

        printBanner();

        try {
            while (true) {
                String userQuery = readNextInput(reader, "user> ", scriptedInput);
                scriptedInput = null;

                if (userQuery == null) {
                    System.out.println();
                    System.out.println("输入流结束，示例退出。");
                    return;
                }

                userQuery = userQuery.trim();
                if (userQuery.isEmpty()) {
                    continue;
                }

                if (isExitCommand(userQuery)) {
                    System.out.println("示例结束。\n");
                    return;
                }

                String sessionId = UUID.randomUUID().toString().substring(0, 8);
                activeSessionId = sessionId;
                Object nextInputs = Map.of("query", userQuery);

                System.out.println();
                System.out.println("[session] " + sessionId);
                System.out.println("[config] apiconfig.json -> "
                        + SharedExampleApiConfigLoader.getModelProvider() + "/"
                        + SharedExampleApiConfigLoader.getModelName());

                while (true) {
                    try {
                        WorkflowOutput output = workflow.invoke(nextInputs, newSession(sessionId), null);

                        if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
                            InteractiveInput replyInputs = collectInteractiveReplies(
                                    reader,
                                    extractPendingInteractions(output.getResult()));
                            if (replyInputs == null) {
                                checkpointer.release(sessionId);
                                return;
                            }
                            nextInputs = replyInputs;
                            continue;
                        }

                        if (WorkflowExecutionState.COMPLETED.equals(output.getState())) {
                            displayCompleted(output.getResult());
                            checkpointer.release(sessionId);
                            activeSessionId = null;
                            break;
                        }

                        System.out.println("assistant> 未预期的 workflow state: " + output.getState());
                        checkpointer.release(sessionId);
                        activeSessionId = null;
                        break;
                    } catch (Exception e) {
                        System.out.println("assistant> " + extractFailureMessage(e));
                        String retryCommand = readNextInput(
                                reader,
                                "retry> ",
                                null);

                        if (retryCommand == null) {
                            checkpointer.release(sessionId);
                            return;
                        }

                        retryCommand = retryCommand.trim();
                        if (retryCommand.isEmpty() || "retry".equalsIgnoreCase(retryCommand)) {
                            nextInputs = new InteractiveInput("retry");
                            continue;
                        }

                        if (isExitCommand(retryCommand)) {
                            checkpointer.release(sessionId);
                            return;
                        }

                        System.out.println("assistant> 请输入 retry 继续同一会话，或输入 exit 结束示例。");
                    }
                }
            }
        } finally {
            if (activeSessionId != null && checkpointer.sessionExists(activeSessionId)) {
                checkpointer.release(activeSessionId);
            }
        }
    }

    private static Workflow buildWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id(WORKFLOW_ID)
                .name("Weather Assistant Interact")
                .description("Real weather + apiconfig + workflow interaction demo")
                .build();

        Workflow workflow = new Workflow(card);
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("check", new CheckBalanceComponent(), Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("ask_city", new AskCityComponent(), Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp("ask_unit", new AskUnitComponent(), Map.of("query", "${start.query}"), null);
        workflow.addWorkflowComp(
                "weather",
                new QueryWeatherComponent(),
                Map.of(
                        "query", "${start.query}",
                        "city", "${ask_city.city}",
                        "unit", "${ask_unit.unit}"),
                null);
        workflow.addWorkflowComp(
                "summarize",
                new SummarizeWeatherComponent(),
                Map.of(
                        "query", "${start.query}",
                        "city", "${weather.city}",
                        "unit", "${weather.unit}",
                        "fact_line", "${weather.fact_line}",
                        "detail_block", "${weather.detail_block}",
                        "api_url", "${weather.api_url}"),
                null);
        workflow.setEndComp(
                "end",
                new ResultComponent(),
                Map.of(
                        "report", "${summarize.report}",
                        "fact_line", "${weather.fact_line}",
                        "api_url", "${weather.api_url}"),
                null);

        workflow.addConnection("start", "check");
        workflow.addConnection("check", "ask_city");
        workflow.addConnection("check", "ask_unit");
        workflow.addConnection("ask_city", "weather");
        workflow.addConnection("ask_unit", "weather");
        workflow.addConnection("weather", "summarize");
        workflow.addConnection("summarize", "end");
        return workflow;
    }

    private static WorkflowSessionApi newSession(String sessionId) {
        return new WorkflowSessionApi(null, sessionId, Map.of());
    }

    private static InteractiveInput collectInteractiveReplies(
            BufferedReader reader,
            List<PendingInteraction> interactions) throws Exception {
        if (interactions.isEmpty()) {
            return new InteractiveInput("continue");
        }

        InteractiveInput interactiveInput = new InteractiveInput();
        for (int idx = 0; idx < interactions.size(); idx++) {
            PendingInteraction interaction = interactions.get(idx);
            System.out.println("assistant> " + interaction.promptText());
            String prompt = interactions.size() == 1
                    ? "reply> "
                    : "reply[" + (idx + 1) + "/" + interactions.size() + "]> ";
            String reply = readNextInput(reader, prompt, null);
            if (reply == null || isExitCommand(reply.trim())) {
                return null;
            }
            interactiveInput.update(interaction.nodeId(), reply.trim());
        }
        return interactiveInput;
    }

    private static List<PendingInteraction> extractPendingInteractions(Object result) {
        List<PendingInteraction> interactions = new ArrayList<>();
        if (!(result instanceof List<?> chunks)) {
            return interactions;
        }

        for (Object item : chunks) {
            if (!(item instanceof OutputSchema output)) {
                continue;
            }

            String outputType = output.getType();
            if (outputType == null || !outputType.toLowerCase().contains("interaction")) {
                continue;
            }

            InteractionOutput interaction = toInteractionOutput(output.getPayload());
            if (interaction == null) {
                interactions.add(new PendingInteraction("unknown", stringify(output.getPayload())));
                continue;
            }

            interactions.add(new PendingInteraction(
                    interaction.getId(),
                    stringify(interaction.getValue())));
        }
        interactions.sort(Comparator.comparing(PendingInteraction::nodeId));
        return interactions;
    }

    private static InteractionOutput toInteractionOutput(Object payload) {
        if (payload instanceof InteractionOutput interactionOutput) {
            return interactionOutput;
        }
        if (payload instanceof Map<?, ?> map) {
            Object id = map.get("id");
            Object value = map.get("value");
            return new InteractionOutput(id == null ? "unknown" : String.valueOf(id), value);
        }
        return null;
    }

    private static void displayCompleted(Object result) {
        if (!(result instanceof Map<?, ?> resultMap)) {
            System.out.println("assistant> " + stringify(result));
            return;
        }

        Object factLine = resultMap.get("fact_line");
        if (factLine != null) {
            System.out.println("[weather] " + factLine);
        }

        Object apiUrl = resultMap.get("api_url");
        if (apiUrl != null) {
            System.out.println("[weather-api] " + apiUrl);
        }

        Object report = resultMap.get("report");
        if (report != null) {
            System.out.println("assistant> " + report);
            System.out.println();
            return;
        }

        System.out.println("assistant> " + stringify(resultMap));
        System.out.println();
    }

    private static String extractFailureMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            current = current.getCause();
        }
        return "执行失败，但没有可显示的错误信息。";
    }

    private static String readNextInput(BufferedReader reader, String prompt, String scriptedInput) throws Exception {
        if (scriptedInput != null) {
            System.out.println(prompt + scriptedInput);
            return scriptedInput;
        }
        System.out.print(prompt);
        System.out.flush();
        return reader.readLine();
    }

    private static boolean isExitCommand(String input) {
        return "quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static void printBanner() {
        System.out.println("Weather Assistant (Interact) 示例");
        System.out.println("- 读取 examples/apiconfig.json 里的真实模型配置");
        System.out.println("- 使用与 ReActWeatherAgentExample 相同的天气 API");
        System.out.println("- 保留 workflow 的交互中断、并发提问和同 session 重试");
        System.out.println("- 输入 exit 或 quit 结束示例");
        System.out.println();
    }

    private static int nextAttempt(NodeSessionApi session, String key) {
        Object raw = session.getState(key);
        int next = raw instanceof Number number ? number.intValue() + 1 : 1;
        session.updateState(Map.of(key, next));
        return next;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castToListOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                results.add((Map<String, Object>) map);
            }
        }
        return results;
    }

    private static String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String normalizeUnit(String unit) {
        String normalized = unit == null ? "C" : unit.trim().toUpperCase();
        if (!"F".equals(normalized)) {
            return "C";
        }
        return normalized;
    }

    private static String formatTemperature(double celsius, String unit) {
        if ("F".equalsIgnoreCase(unit)) {
            long fahrenheit = Math.round(celsius * 9 / 5 + 32);
            return fahrenheit + "°F";
        }
        long rounded = Math.round(celsius);
        return rounded + "°C";
    }

    private static Model createSummaryModel() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(SharedExampleApiConfigLoader.getModelProvider())
                .apiKey(SharedExampleApiConfigLoader.getApiKey())
                .apiBase(SharedExampleApiConfigLoader.getApiBase())
                .timeout(120.0)
                .maxRetries(2)
                .verifySsl(SharedExampleApiConfigLoader.getSslVerify())
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .temperature(0.2)
                .topP(0.8)
                .maxTokens(256)
                .build();

        return new Model(clientConfig, requestConfig);
    }

    private static String resolveWeatherUrl() {
        String propertyValue = System.getProperty("openjiuwen.example.weatherUrl");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String envValue = System.getenv("WEATHER_URL");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return DEFAULT_WEATHER_API_URL;
    }

    private static Map<String, Object> invokeWeatherApi(String weatherUrl, String city) throws Exception {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String resolvedUrl = weatherUrl
                + (weatherUrl.contains("?") ? "&" : "?")
                + "city=" + encodedCity + "&lang=zh&forecast=true";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolvedUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("weather api returned HTTP " + response.statusCode());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", resolvedUrl);
        result.put("data", MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {
        }));
        return result;
    }

    private record PendingInteraction(String nodeId, String promptText) {
    }

    private static final class CheckBalanceComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            int run = nextAttempt(session, "run_count");
            System.out.println("[CheckBalance] session=" + session.getSessionId() + " run=" + run
                    + " mock_balance=$" + String.format("%.4f", MOCK_BALANCE));

            String confirm = session.interact(
                    "调用天气 API 需要 $0.01，当前示例固定模拟低余额，是否继续？（Yes/No）");
            if (!"yes".equalsIgnoreCase(confirm == null ? "" : confirm.trim())) {
                throw new RuntimeException("用户取消了天气查询，示例终止。");
            }
            return Map.of("balance_ok", true);
        }
    }

    private static final class AskCityComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            int run = nextAttempt(session, "run_count");
            System.out.println("[AskCity] session=" + session.getSessionId() + " run=" + run);
            String city = session.interact("请输入要查询的城市（如 Beijing）：");
            city = city == null ? "" : city.trim();
            if (city.isEmpty()) {
                city = "Beijing";
            }
            return Map.of("city", city);
        }
    }

    private static final class AskUnitComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            int run = nextAttempt(session, "run_count");
            System.out.println("[AskUnit] session=" + session.getSessionId() + " run=" + run);
            String unit = session.interact("温度单位选 Celsius 还是 Fahrenheit？（C/F）");
            return Map.of("unit", normalizeUnit(unit));
        }
    }

    private static final class QueryWeatherComponent extends WorkflowComponent {

        private static final Map<String, Integer> SESSION_ATTEMPTS = new ConcurrentHashMap<>();

        private final String weatherUrl = resolveWeatherUrl();

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = castToMap(inputs);
            String city = asString(inputMap.get("city"), "Beijing");
            String unit = normalizeUnit(asString(inputMap.get("unit"), "C"));
            int attempt = SESSION_ATTEMPTS.merge(session.getSessionId(), 1, Integer::sum);

            System.out.println("[QueryWeather] session=" + session.getSessionId()
                    + " attempt=" + attempt + " city=" + city + " unit=" + unit);

            if (attempt == 1) {
                throw new RuntimeException("模拟网络抖动：首次天气查询故意失败，请输入 retry 使用同一 session 恢复。");
            }

            Map<String, Object> response;
            try {
                response = invokeWeatherApi(weatherUrl, city);
            } catch (Exception e) {
                throw new RuntimeException("天气接口调用失败: " + e.getMessage(), e);
            }
            Map<String, Object> data = castToMap(response.get("data"));
            List<Map<String, Object>> forecast = castToListOfMap(data.get("forecast"));
            Map<String, Object> tomorrow = forecast.size() > 1 ? forecast.get(1)
                    : (forecast.isEmpty() ? Map.of() : forecast.get(0));

            String displayCity = asString(data.get("city"), city);
            String currentWeather = asString(data.get("weather"), "未知");
            String humidity = asString(data.get("humidity"), "未知");
            String windDirection = asString(data.get("wind_direction"), "未知");
            String windPower = asString(data.get("wind_power"), "未知");
            String date = asString(tomorrow.get("date"), asString(data.get("report_time"), "未知日期"));
            String week = asString(tomorrow.get("week"), "");
            String weatherDay = asString(tomorrow.get("weather_day"), currentWeather);
            String weatherNight = asString(tomorrow.get("weather_night"), currentWeather);
            String tempHigh = formatTemperature(asDouble(tomorrow.get("temp_max"), asDouble(data.get("temp_max"), 0)), unit);
            String tempLow = formatTemperature(asDouble(tomorrow.get("temp_min"), asDouble(data.get("temp_min"), 0)), unit);
            String currentTemp = formatTemperature(asDouble(data.get("temperature"), 0), unit);

            String factLine = displayCity + " " + date + " " + week
                    + "，白天" + weatherDay + "，夜间" + weatherNight
                    + "，气温 " + tempHigh + " / " + tempLow
                    + "，当前 " + currentTemp + "，湿度 " + humidity + "% 。";

            String detailBlock = "城市：" + displayCity + "\n"
                    + "用户原始问题：" + asString(inputMap.get("query"), DEFAULT_QUERY) + "\n"
                    + "温度单位：" + unit + "\n"
                    + "当前天气：" + currentWeather + "\n"
                    + "当前温度：" + currentTemp + "\n"
                    + "明日日期：" + date + " " + week + "\n"
                    + "明日白天：" + weatherDay + "\n"
                    + "明日夜间：" + weatherNight + "\n"
                    + "明日最高温：" + tempHigh + "\n"
                    + "明日最低温：" + tempLow + "\n"
                    + "湿度：" + humidity + "%\n"
                    + "风向风力：" + windDirection + " / " + windPower;

            System.out.println("[QueryWeather] weather_url=" + response.get("url"));
            System.out.println("[QueryWeather] facts=" + factLine);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("city", displayCity);
            output.put("unit", unit);
            output.put("fact_line", factLine);
            output.put("detail_block", detailBlock);
            output.put("api_url", asString(response.get("url"), weatherUrl));
            return output;
        }
    }

    private static final class SummarizeWeatherComponent extends WorkflowComponent {

        private final Model model = createSummaryModel();

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = castToMap(inputs);
            System.out.println("[SummarizeWeather] session=" + session.getSessionId()
                    + " model=" + SharedExampleApiConfigLoader.getModelName());

            String userPrompt = "用户请求：" + asString(inputMap.get("query"), DEFAULT_QUERY) + "\n"
                    + asString(inputMap.get("detail_block"), "") + "\n"
                    + "请给出一个简短天气结论，并补充穿衣或出行建议。";

            List<BaseMessage> messages = List.of(
                    SystemMessage.builder().content(SUMMARY_SYSTEM_PROMPT).build(),
                    UserMessage.builder().content(userPrompt).build());

            AssistantMessage response;
            try {
                response = model.invoke(
                        messages,
                        null,
                        0.2f,
                        0.8f,
                        null,
                        256,
                        null,
                        null,
                        null,
                        null);
            } catch (Exception e) {
                throw new RuntimeException("天气摘要模型调用失败: " + e.getMessage(), e);
            }

            String report = response == null ? "" : response.getContentAsString();
            if (report == null || report.isBlank()) {
                report = asString(inputMap.get("fact_line"), "天气查询完成，但模型没有返回摘要。");
            }

            return Map.of(
                    "report", report.trim(),
                    "api_url", asString(inputMap.get("api_url"), weatherUrlLabel(inputMap))
            );
        }

        private String weatherUrlLabel(Map<String, Object> inputMap) {
            return asString(inputMap.get("api_url"), DEFAULT_WEATHER_API_URL);
        }
    }

    private static final class ResultComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }
}