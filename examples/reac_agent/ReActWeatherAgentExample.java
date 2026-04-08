/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.reac_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;

/**
 * Java version of the Python examples/react_agent notebook.
 */
public final class ReActWeatherAgentExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "react_agent_java_example";
    private static final String TOOL_ID = "weather_tool";
    private static final String DEFAULT_CONVERSATION_ID = "react_agent_example_013";
    private static final String DEFAULT_QUERY = "查询北京明天天气，并给出简短建议";
    private static final String DEFAULT_WEATHER_API_URL = "https://uapis.cn/api/v1/misc/weather";
    private static final String SYSTEM_PROMPT = "你是一个天气查询助手。"
            + "当用户询问天气时，必须先调用 WeatherReporter 工具获取天气信息，再基于工具结果总结回答。"
            + "工具会返回实时天气和未来几天预报。"
            + "每次只调用一次工具，不要重复调用。";

    private ReActWeatherAgentExample() {
    }

    public static void main(String[] args) throws Exception {
        Tool weatherTool = null;

        try {
            String weatherUrl = resolveWeatherUrl();

            ReActAgent agent = createAgent();
            weatherTool = createWeatherTool(weatherUrl);
            registerTool(agent, weatherTool);

            String query = args.length == 0 ? DEFAULT_QUERY : String.join(" ", args);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                    agent,
                    Map.of(
                            "query", query,
                            "conversation_id", DEFAULT_CONVERSATION_ID
                    ),
                    null,
                    null
            );

            System.out.println("Agent result:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            if (weatherTool != null) {
                Runner.resourceMgr().removeTool(weatherTool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
            }
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    private static ReActAgent createAgent() {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("天气查询助手")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(3)
                .build()
                .configureModelClient(
                        ExampleApiConfigLoader.getModelProvider(),
                        ExampleApiConfigLoader.getApiKey(),
                        ExampleApiConfigLoader.getApiBase(),
                        ExampleApiConfigLoader.getModelName(),
                        ExampleApiConfigLoader.getSslVerify()
                );

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        requestConfig.setTemperature(0.6);
        requestConfig.setTopP(0.8);
        requestConfig.setMaxTokens(256);

        agent.configure(config);
        return agent;
    }

    private static Tool createWeatherTool(String weatherUrl) {
        RestfulApiCard card = RestfulApiCard.builder()
                .id(TOOL_ID)
                .name("WeatherReporter")
            .description("天气查询插件，输入 city 获取实时天气和未来几天预报；city 可传中文或英文城市名")
                .url(weatherUrl)
                .method("GET")
                .timeout(10.0)
            .queries(Map.of(
                "lang", "zh",
                "forecast", true
            ))
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                    "city", Map.of(
                                        "type", "string",
                        "description", "城市名称，支持中文（北京）和英文（Tokyo）",
                                        "location", "query"
                                )
                )
                ))
                .build();

        return new RestfulApi(card);
    }

    private static void registerTool(ReActAgent agent, Tool tool) {
        Runner.resourceMgr().removeTool(tool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(tool.getCard());
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
}