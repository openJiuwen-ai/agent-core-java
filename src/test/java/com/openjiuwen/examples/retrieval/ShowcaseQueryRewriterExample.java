/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase Query Rewriter Example.
 *
 * Mirrors Python's {@code showcase_query_rewriter} in
 * {@code examples.retrieval.showcase_query_rewriter}.
 */
public final class ShowcaseQueryRewriterExample {

    public static final int COMPRESS_RANGE = 4;
    public static final String PROMPT_LANG = "zh";

    public static final List<Turn> EXAMPLE_TURNS = List.of(
            new Turn("user", "What is our project's tech stack?"),
            new Turn("assistant",
                    "The project uses Python 3.11, FastAPI, LangChain and Chroma. Frontend plans to use React."),
            new Turn("user", "What about deployment?"),
            new Turn("assistant",
                    "Deployment uses Docker containers, Kubernetes in production, and GitHub Actions for CI."),
            new Turn("user", "Does it support multi-tenancy?"),
            new Turn("assistant", "Multi-tenancy is not implemented yet; a future release may add it."),
            new Turn("user", "What about logging and monitoring?"),
            new Turn("assistant", "Logging uses ELK; monitoring uses Prometheus + Grafana.")
    );

    public static final String FINAL_QUERY = "Can you summarize that again?";
    public static final String FINAL_ASSISTANT_RESPONSE =
            "Tech stack: Python/FastAPI/LangChain/Chroma, React planned. "
                    + "Deployment: Docker/K8s, CI with GitHub Actions. "
                    + "Multi-tenancy not implemented yet.";

    private ShowcaseQueryRewriterExample() {
    }

    public static ModelConfig sampleQrModelConfig() {
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .apiKey("qr-key")
                .apiBase("https://qr.example/v1")
                .modelName("qr-model")
                .temperature(0.0d)
                .topP(0.1d)
                .timeout(60)
                .build();
        return new ModelConfig("OpenAI", modelInfo);
    }

    /**
     * Run the complete QR example with a deterministic offline rewriter.
     */
    public static DemoReport runExample() {
        return runExample(sampleQrModelConfig(), new DeterministicQueryRewriter(COMPRESS_RANGE));
    }

    public static DemoReport runExample(ModelConfig config, ExampleQueryRewriter rewriter) {
        if (config == null) {
            return DemoReport.missingConfigReport();
        }
        InMemoryContext ctx = new InMemoryContext();
        List<RewriteStep> steps = new ArrayList<>();
        for (int i = 0; i < EXAMPLE_TURNS.size(); i++) {
            Turn turn = EXAMPLE_TURNS.get(i);
            if ("user".equals(turn.role())) {
                RewriteResult result = runSingleRewrite(rewriter, ctx, turn.content(), "Turn " + (i / 2 + 1));
                steps.add(new RewriteStep("Turn " + (i / 2 + 1), turn.content(), result, ctx.size()));
                ctx.addMessage(Message.user(turn.content()));
            } else {
                ctx.addMessage(Message.assistant(turn.content()));
            }
        }

        RewriteResult finalResult = runSingleRewrite(rewriter, ctx, FINAL_QUERY, "Final");
        steps.add(new RewriteStep("Final", FINAL_QUERY, finalResult, ctx.size()));
        ctx.addMessage(Message.user(FINAL_QUERY));
        ctx.addMessage(Message.assistant(FINAL_ASSISTANT_RESPONSE));

        return new DemoReport(
                false,
                config.modelProvider(),
                config.modelInfo().getModelName(),
                COMPRESS_RANGE,
                PROMPT_LANG,
                steps,
                ctx.messages(),
                ctx.size(),
                steps.stream().anyMatch(step -> step.result().compressionTriggered())
        );
    }

    public static RewriteResult runSingleRewrite(
            ExampleQueryRewriter rewriter,
            InMemoryContext ctx,
            String userQuery,
            String turnLabel
    ) {
        return rewriter.rewrite(userQuery, ctx, turnLabel);
    }

    public static void main(String[] args) {
        DemoReport report = runExample();
        if (report.missingConfig()) {
            System.out.println("QR LLM config missing. Set QR_LLM_API_BASE, QR_LLM_API_KEY, QR_LLM_MODEL.");
            return;
        }
        System.out.println("Query Rewriter (QR) example: multi-turn + standalone query for retrieval");
        System.out.println("compress_range=" + report.compressRange());
        for (RewriteStep step : report.steps()) {
            System.out.println("[" + step.turnLabel() + "] User: " + step.userQuery());
            System.out.println("  before (raw):     " + step.result().before());
            System.out.println("  standalone_query: " + step.result().standaloneQuery());
            System.out.println("  intention:        " + step.result().intention());
            if (step.result().compressionTriggered()) {
                System.out.println("  [Compression triggered; history replaced with summary]");
            }
        }
        System.out.println("Context message count after example: " + report.finalContextCount());
    }

    public interface ExampleQueryRewriter {
        RewriteResult rewrite(String userQuery, InMemoryContext ctx, String turnLabel);
    }

    /**
     * Deterministic QR stand-in preserving context and compression semantics.
     */
    public static final class DeterministicQueryRewriter implements ExampleQueryRewriter {
        private final int compressRange;

        public DeterministicQueryRewriter(int compressRange) {
            this.compressRange = Math.max(1, compressRange);
        }

        @Override
        public RewriteResult rewrite(String userQuery, InMemoryContext ctx, String turnLabel) {
            if (userQuery == null || userQuery.isBlank()) {
                throw new IllegalArgumentException("query must be non-empty");
            }
            boolean compressed = false;
            if (ctx.size() >= compressRange) {
                ctx.setMessages(List.of(Message.system(summarize(ctx.messages()))));
                compressed = true;
            }
            String standalone = standaloneQuery(userQuery);
            String intention = intention(userQuery);
            return new RewriteResult(userQuery, standalone, intention, List.of(), compressed);
        }

        private static String standaloneQuery(String userQuery) {
            return switch (userQuery) {
                case "What is our project's tech stack?" -> userQuery;
                case "What about deployment?" -> "What is our project's deployment approach?";
                case "Does it support multi-tenancy?" -> "Does the project support multi-tenancy?";
                case "What about logging and monitoring?" ->
                        "What logging and monitoring stack does the project use?";
                case FINAL_QUERY -> "Can you summarize the project's tech stack, deployment, and multi-tenancy again?";
                default -> userQuery;
            };
        }

        private static String intention(String userQuery) {
            String lower = userQuery.toLowerCase();
            if (lower.contains("deployment")) {
                return "deployment";
            }
            if (lower.contains("multi-tenancy")) {
                return "multi-tenancy";
            }
            if (lower.contains("logging") || lower.contains("monitoring")) {
                return "observability";
            }
            if (lower.contains("summarize")) {
                return "summary";
            }
            return "tech stack";
        }

        private static String summarize(List<Message> messages) {
            Map<String, String> facts = new LinkedHashMap<>();
            for (Message message : messages) {
                String text = message.content();
                if (text.contains("Python 3.11") || text.contains("FastAPI")) {
                    facts.put("stack", "Python 3.11, FastAPI, LangChain, Chroma, React planned");
                }
                if (text.contains("Docker") || text.contains("Kubernetes")) {
                    facts.put("deployment", "Docker containers, Kubernetes, GitHub Actions");
                }
                if (text.contains("Multi-tenancy")) {
                    facts.put("multi-tenancy", "not implemented yet");
                }
                if (text.contains("ELK") || text.contains("Prometheus")) {
                    facts.put("observability", "ELK logging, Prometheus + Grafana monitoring");
                }
            }
            return "QR summary: " + String.join("; ", facts.values());
        }
    }

    public static final class InMemoryContext {
        private final ArrayList<Message> messages = new ArrayList<>();

        public InMemoryContext() {
        }

        public InMemoryContext(List<Message> initialMessages) {
            messages.addAll(initialMessages == null ? List.of() : initialMessages);
        }

        public int size() {
            return messages.size();
        }

        public List<Message> getMessages(Integer size, boolean withHistory) {
            if (size == null) {
                return messages();
            }
            if (size <= 0) {
                return List.of();
            }
            int start = Math.max(0, messages.size() - size);
            return List.copyOf(messages.subList(start, messages.size()));
        }

        public void setMessages(List<Message> replacement) {
            messages.clear();
            messages.addAll(replacement == null ? List.of() : replacement);
        }

        public List<Message> popMessages(int size) {
            if (size <= 0 || messages.isEmpty()) {
                return List.of();
            }
            int start = Math.max(0, messages.size() - size);
            List<Message> popped = new ArrayList<>(messages.subList(start, messages.size()));
            messages.subList(start, messages.size()).clear();
            return popped;
        }

        public void clearMessages() {
            messages.clear();
        }

        public List<Message> addMessage(Message message) {
            messages.add(message);
            return List.of(message);
        }

        public List<Message> addMessages(List<Message> toAdd) {
            messages.addAll(toAdd == null ? List.of() : toAdd);
            return toAdd == null ? List.of() : List.copyOf(toAdd);
        }

        public ContextStats statistic() {
            return new ContextStats(messages.size());
        }

        public String sessionId() {
            return "qr_example_session";
        }

        public String contextId() {
            return "qr_example_context";
        }

        public List<Message> messages() {
            return List.copyOf(messages);
        }
    }

    public record Turn(String role, String content) {
    }

    public record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }

        public static Message system(String content) {
            return new Message("system", content);
        }
    }

    public record ContextStats(int totalMessages) {
    }

    public record RewriteResult(
            String before,
            String standaloneQuery,
            String intention,
            List<String> typo,
            boolean compressionTriggered
    ) {
    }

    public record RewriteStep(String turnLabel, String userQuery, RewriteResult result, int contextSizeBeforeAppend) {
    }

    public record DemoReport(
            boolean missingConfig,
            String provider,
            String modelName,
            int compressRange,
            String promptLang,
            List<RewriteStep> steps,
            List<Message> finalMessages,
            int finalContextCount,
            boolean compressionObserved
    ) {
        public static DemoReport missingConfigReport() {
            return new DemoReport(true, "", "", COMPRESS_RANGE, PROMPT_LANG, List.of(), List.of(), 0, false);
        }
    }
}
