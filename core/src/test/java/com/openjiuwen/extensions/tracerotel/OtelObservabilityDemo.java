/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.tracer.InvokeType;
import com.openjiuwen.core.session.tracer.NodeStatus;
import com.openjiuwen.core.session.tracer.SpanManager;
import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import io.opentelemetry.api.trace.Tracer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OTel 观测驱动程序：模拟真实 Agent 调用链路，将 trace 通过 OTLP/HTTP 上报到 Jaeger (localhost:4318)。
 *
 * <p>运行方式：mvn test-compile -q && java -cp "target/classes;target/test-classes;$(mvn dependency:build-classpath -q -Dmdep.outputFile=cp.txt && type cp.txt)" com.openjiuwen.extensions.tracerotel.OtelObservabilityDemo</p>
 */
public final class OtelObservabilityDemo {

    private OtelObservabilityDemo() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== OTel 观测驱动程序启动 ===");

        // 1. 构建配置：OTLP/HTTP 上报到 localhost:4318
        OtelTracerConfig config = OtelTracerConfig.builder()
                .exporterType("otlp")
                .exporterEndpoint("http://localhost:4318")
                .protocol("http")
                .serviceName("openjiuwen-agent-demo")
                .serviceVersion("1.0.0")
                .sampleRate(1.0)
                .isRedactionEnabled(true)
                .shouldRedactPrompts(false)   // 演示用：prompt 不脱敏，便于在 Jaeger 查看原始内容
                .shouldRedactCompletions(true) // completion 脱敏
                .scheduleDelayMillis(1000) // 1 秒刷一次，便于演示
                .build();

        // 2. 初始化 OTel Tracer（OTLP/HTTP 导出器 + BatchSpanProcessor）
        Tracer otelTracer = OtelTracerSetup.initOtelTracer(config);
        System.out.println("[setup] OTel Tracer 已初始化，OTLP/HTTP -> http://localhost:4318/v1/traces");

        // 3. 创建 Agent Handler
        String traceId = "demo-trace-" + System.currentTimeMillis();
        OtelAgentHandler handler = new OtelAgentHandler(otelTracer, config, traceId);
        SpanManager spanManager = new SpanManager(traceId);

        // ================================================================
        // 场景 1：完整的 LLM 调用（成功路径）
        // ================================================================
        System.out.println("\n[scenario-1] LLM 调用（成功路径）");
        TraceAgentSpan llmSpan = spanManager.createAgentSpan(null);
        llmSpan.setInvokeType(InvokeType.LLM.getValue());
        llmSpan.setName("Qwen-Max");

        Map<String, Object> llmInputs = new LinkedHashMap<>();
        llmInputs.put("messa" +
                "ges", List.of(
                Map.of("role", "user", "content", "请用三句话介绍 OpenTelemetry")));
        Map<String, Object> llmInstanceInfo = instanceInfo("Qwen-Max");
        handler.onLlmStart(llmSpan, llmInputs, llmInstanceInfo);

        Thread.sleep(150); // 模拟模型推理耗时

        Map<String, Object> llmOutputs = new LinkedHashMap<>();
        llmOutputs.put("content", "OpenTelemetry 是一个开源的可观测性框架...");
        llmOutputs.put("usage", Map.of("prompt_tokens", 28, "completion_tokens", 45, "total_tokens", 73));
        handler.onLlmEnd(llmSpan, llmOutputs);
        System.out.println("  -> LLM span 已结束并上报");

        // ================================================================
        // 场景 2：Tool/Plugin 调用（成功路径）
        // ================================================================
        System.out.println("\n[scenario-2] Plugin/Tool 调用（成功路径）");
        TraceAgentSpan toolSpan = spanManager.createAgentSpan(null);
        toolSpan.setInvokeType(InvokeType.PLUGIN.getValue());
        toolSpan.setName("WeatherSearchTool");

        Map<String, Object> toolInputs = new LinkedHashMap<>();
        toolInputs.put("query", "北京今天天气");
        handler.onPluginStart(toolSpan, toolInputs, instanceInfo("WeatherSearchTool"));

        Thread.sleep(80); // 模拟工具执行耗时

        Map<String, Object> toolOutputs = new LinkedHashMap<>();
        toolOutputs.put("result", "晴，25°C，湿度40%");
        handler.onPluginEnd(toolSpan, toolOutputs);
        System.out.println("  -> Tool span 已结束并上报");

        // ================================================================
        // 场景 3：Chain 调用（错误路径）
        // ================================================================
        System.out.println("\n[scenario-3] Chain 调用（错误路径）");
        TraceAgentSpan chainSpan = spanManager.createAgentSpan(null);
        chainSpan.setInvokeType(InvokeType.CHAIN.getValue());
        chainSpan.setName("SummaryChain");

        Map<String, Object> chainInputs = new LinkedHashMap<>();
        chainInputs.put("input", "请总结以下文档...");
        handler.onChainStart(chainSpan, chainInputs, instanceInfo("SummaryChain"));

        Thread.sleep(100); // 模拟执行耗时

        BaseError chainError = new BaseError(
                StatusCode.WORKFLOW_EXECUTION_ERROR,
                "SummaryChain 执行失败：下游服务超时",
                null, null);
        handler.onChainError(chainSpan, chainError);
        System.out.println("  -> Chain error span 已结束并上报");

        // ================================================================
        // 场景 4：Retriever 调用（成功路径）
        // ================================================================
        System.out.println("\n[scenario-4] Retriever 调用（成功路径）");
        TraceAgentSpan retrieverSpan = spanManager.createAgentSpan(null);
        retrieverSpan.setInvokeType(InvokeType.RETRIEVER.getValue());
        retrieverSpan.setName("VectorRetriever");

        Map<String, Object> retrieverInputs = new LinkedHashMap<>();
        retrieverInputs.put("query", "OTel 采样策略");
        handler.onRetrieverStart(retrieverSpan, retrieverInputs, instanceInfo("VectorRetriever"));

        Thread.sleep(60); // 模拟检索耗时

        Map<String, Object> retrieverOutputs = new LinkedHashMap<>();
        retrieverOutputs.put("documents", List.of(
                Map.of("id", "doc-1", "score", 0.95, "snippet", "parentBased + traceIdRatioBased..."),
                Map.of("id", "doc-2", "score", 0.87, "snippet", "OTLP 支持 gRPC 和 HTTP 协议...")));
        handler.onRetrieverEnd(retrieverSpan, retrieverOutputs);
        System.out.println("  -> Retriever span 已结束并上报");

        // ================================================================
        // 场景 5：父子 Span 层级关系（Agent 根 -> LLM 子）
        // ================================================================
        System.out.println("\n[scenario-5] 父子 Span 层级（Agent 根 -> LLM 子 -> Tool 子）");
        TraceAgentSpan rootSpan = spanManager.createAgentSpan(null);
        rootSpan.setInvokeType(InvokeType.CHAIN.getValue());
        rootSpan.setName("ReActAgent");
        handler.onChainStart(rootSpan, Map.of("query", "什么是 OpenTelemetry？"), instanceInfo("ReActAgent"));

        // 子 LLM span（父为 rootSpan）
        TraceAgentSpan childLlm = spanManager.createAgentSpan(rootSpan);
        childLlm.setInvokeType(InvokeType.LLM.getValue());
        childLlm.setName("Qwen-Max");
        handler.onLlmStart(childLlm, Map.of("messages", List.of(Map.of("role", "user", "content", "什么是 OTel"))), instanceInfo("Qwen-Max"));
        Thread.sleep(120);
        handler.onLlmEnd(childLlm, Map.of("content", "OTel 是 CNCF 的可观测性标准..."));
        System.out.println("  -> 子 LLM span 已结束");

        // 子 Tool span（父为 rootSpan）
        TraceAgentSpan childTool = spanManager.createAgentSpan(rootSpan);
        childTool.setInvokeType(InvokeType.PLUGIN.getValue());
        childTool.setName("SearchTool");
        handler.onPluginStart(childTool, Map.of("query", "OTel 文档"), instanceInfo("SearchTool"));
        Thread.sleep(50);
        handler.onPluginEnd(childTool, Map.of("result", "找到了 3 篇相关文档"));
        System.out.println("  -> 子 Tool span 已结束");

        // 结束根 span
        handler.onChainEnd(rootSpan, Map.of("answer", "OpenTelemetry 是 CNCF 的可观测性标准项目"));
        System.out.println("  -> 根 Agent span 已结束并上报");

        // ================================================================
        // 等待 BatchSpanProcessor 刷新
        // ================================================================
        System.out.println("\n[flush] 等待 BatchSpanProcessor 刷新（scheduleDelayMillis=1000）...");
        Thread.sleep(3000);

        System.out.println("\n=== 驱动程序完成 ===");
        System.out.println("请访问 Jaeger UI 查看 trace: http://localhost:16686");
        System.out.println("Service 名称: openjiuwen-agent-demo");
        System.out.println("共产生 5 个场景、7 个 span");

        // 强制退出（BatchSpanProcessor 后台线程非 daemon）
        System.exit(0);
    }

    private static Map<String, Object> instanceInfo(String className) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class_name", className);
        info.put("type", "agent");
        return info;
    }
}
