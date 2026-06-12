/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AgentContext;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AgentRunResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseAgentAdapter;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.DockerEnvironment;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.ExecResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.IterationResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.SkillDelta;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.SkillManager;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.Task;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JiuWenSwarm-backed evaluator pipeline agent adapter.
 *
 * <p>Mirrors Python's {@code JiuWenSwarmAgent} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/jiuwenswarm.py}.</p>
 */
public class JiuWenSwarmAgent extends BaseAgentAdapter {

    static {
        AdapterRegistry.registerAgent("jiuwenswarm", JiuWenSwarmAgent.class);
    }

    static final String SKILL_DIR = "/root/.jiuwenswarm/agent/workspace/skills";
    static final String CONFIG_DIR = "/root/.jiuwenswarm/config";
    static final String WORKSPACE_DIR = "/workspace";
    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String resolvedSkillName = "";
    private List<String> allSkillNames = new ArrayList<>();
    private final Map<String, String> capturedEvolutionJson = new LinkedHashMap<>();

    public JiuWenSwarmAgent() {
        this(null);
    }

    public JiuWenSwarmAgent(Map<String, Object> config) {
        super(config);
    }

    @Override
    public String name() {
        return "jiuwenswarm";
    }

    @Override
    public List<String> supportedSkillsModes() {
        return List.of("create", "read", "evolve");
    }

    @Override
    public String defaultModel() {
        return stringConfig("model_name").orElse("glm-5");
    }

    @Override
    public List<String> validateConfig() {
        List<String> errors = new ArrayList<>();
        if (stringConfig("api_key").orElse("").isBlank()) {
            errors.add("api_key is required (set DASHSCOPE_API_KEY or OPENAI_API_KEY)");
        }
        if (stringConfig("api_base").orElse("").isBlank()) {
            errors.add("api_base is required");
        }
        return errors;
    }

    @Override
    public Map<String, Object> getSourceFiles() {
        String installMode = stringConfig("install_mode").orElse("auto");
        String gitUrl = stringConfig("jiuwenswarm_git_url")
                .orElse("https://gitcode.com/openJiuwen/jiuwenswarm.git@develop");
        if ("git".equals(installMode)) {
            return Map.of(
                    "mode", "git",
                    "packages", List.of("git+" + gitUrl),
                    "requires_git", true
            );
        }
        if ("pypi".equals(installMode)) {
            return Map.of(
                    "mode", "pypi",
                    "packages", List.of("jiuwenswarm")
            );
        }

        Path projectRoot = resolveProjectRoot();
        Path jiuwenSwarmSrc = projectRoot.resolve("jiuwenswarm");
        if ("local".equals(installMode)) {
            if (Files.exists(jiuwenSwarmSrc)) {
                return Map.of(
                        "mode", "local",
                        "sources", Map.of("jiuwenswarm", jiuwenSwarmSrc)
                );
            }
            LOGGER.warning("  Warning: local mode but jiuwenswarm source not found: {}", jiuwenSwarmSrc);
            return Map.of(
                    "mode", "git",
                    "packages", List.of("git+" + gitUrl)
            );
        }

        if (Files.exists(jiuwenSwarmSrc)) {
            return Map.of(
                    "mode", "local",
                    "sources", Map.of("jiuwenswarm", jiuwenSwarmSrc)
            );
        }
        return Map.of(
                "mode", "git",
                "packages", List.of("git+" + gitUrl)
        );
    }

    @Override
    public void setSkillContext(String resolvedName, List<String> allNames) {
        this.resolvedSkillName = resolvedName != null ? resolvedName : "";
        this.allSkillNames = allNames != null ? new ArrayList<>(allNames) : new ArrayList<>();
    }

    @Override
    public Map<String, String> getCapturedEvolutionJson() {
        return Map.copyOf(capturedEvolutionJson);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> setup(DockerEnvironment env) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Setting up JiuWenSwarm...");
            ExecResult importResult = awaitResult(env.exec("python3 -c 'import jiuwenswarm; print(\"OK\")'", 30, null, null));
            if (!importResult.getStdout().contains("OK")) {
                LOGGER.warning("  JiuWenSwarm not found in container");
                return false;
            }

            LOGGER.info("  JiuWenSwarm verified");
            awaitResult(env.exec("which uv || python3 -m pip install --break-system-packages uv==0.9.7", 60, null, null));
            awaitResult(env.exec("uv --version", 30, null, null));
            LOGGER.info("  uv verified");

            awaitResult(env.exec("mkdir -p " + CONFIG_DIR, 10, null, null));
            awaitResult(env.exec("export PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple", 10, null, null));
            awaitResult(env.exec("export PIP_TIMEOUT=120", 10, null, null));
            awaitResult(env.exec("export PIP_DEFAULT_TIMEOUT=120", 10, null, null));

            String apiBase = stringConfig("api_base").orElse("");
            String apiKey = stringConfig("api_key").orElse("");
            String modelName = defaultModel();
            boolean evolutionEnabled = booleanConfig("evolution_enabled", true);

            String envContent = "API_BASE=" + apiBase + "\n"
                    + "API_KEY=" + apiKey + "\n"
                    + "MODEL_NAME=" + modelName + "\n"
                    + "MODEL_PROVIDER=" + (apiBase.contains("dashscope") ? "DashScope" : "openai") + "\n\n"
                    + "EVOLUTION_AUTO_SCAN=" + Boolean.toString(evolutionEnabled) + "\n"
                    + "EVOLUTION_AUTO_SAVE=" + Boolean.toString(evolutionEnabled) + "\n\n"
                    + "PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple\n"
                    + "PIP_TIMEOUT=120\n"
                    + "PIP_DEFAULT_TIMEOUT=120\n";
            copyTempFile(env, "jiuwenswarm_env", envContent, CONFIG_DIR + "/.env");
            LOGGER.info("  Created .env file (evolution={})", evolutionEnabled ? "enabled" : "disabled");

            String configYaml = "preferred_language: en\n\n"
                    + "models:\n"
                    + "  default:\n"
                    + "    model_client_config:\n"
                    + "      api_base: ${API_BASE}\n"
                    + "      api_key: ${API_KEY}\n"
                    + "      model_name: ${MODEL_NAME:-" + modelName + "}\n"
                    + "      client_provider: ${MODEL_PROVIDER:-openai}\n"
                    + "      timeout: 1800\n"
                    + "      verify_ssl: false\n"
                    + "      custom_headers: {}\n"
                    + "    model_config_obj:\n"
                    + "      temperature: 0.7\n\n"
                    + "sandbox:\n"
                    + "  enabled: false\n\n"
                    + "react:\n"
                    + "  max_iterations: 50\n"
                    + "  evolution:\n"
                    + "    enabled: " + Boolean.toString(evolutionEnabled) + "\n"
                    + "    auto_scan: " + Boolean.toString(evolutionEnabled) + "\n"
                    + "    auto_save: " + Boolean.toString(evolutionEnabled) + "\n"
                    + "    skill_base_dir: " + SKILL_DIR + "\n\n"
                    + "memory:\n"
                    + "  engine: none\n";
            copyTempFile(env, "jiuwenswarm_config", configYaml, CONFIG_DIR + "/config.yaml");
            LOGGER.info("  Created config.yaml (evolution={})", evolutionEnabled ? "enabled" : "disabled");
            return true;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Integer> loadSkills(
            DockerEnvironment env,
            Map<String, String> skills,
            Map<String, String> evolutions,
            Map<String, Map<String, String>> evolutionFiles) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Map<String, String> safeEvolutions = evolutions != null ? evolutions : Map.of();
            Map<String, Map<String, String>> safeEvolutionFiles = evolutionFiles != null ? evolutionFiles : Map.of();
            int loaded = 0;
            for (Map.Entry<String, String> entry : skills.entrySet()) {
                boolean ok = loadSingleSkill(
                        env,
                        entry.getKey(),
                        entry.getValue(),
                        safeEvolutions.get(entry.getKey()),
                        safeEvolutionFiles.get(entry.getKey()));
                if (ok) {
                    loaded++;
                }
            }
            this.allSkillNames = new ArrayList<>(skills.keySet());
            if (!skills.isEmpty() && resolvedSkillName.isBlank()) {
                resolvedSkillName = skills.keySet().iterator().next();
            }
            LOGGER.info("  Loaded {}/{} skills into container", loaded, skills.size());
            return loaded;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<List<String>> loadSkillsFromDir(DockerEnvironment env, Path skillsDir) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (skillsDir == null || !Files.isDirectory(skillsDir)) {
                LOGGER.warning("  No skills directory found: {}", skillsDir);
                return List.of();
            }
            List<String> loadedSkills = new ArrayList<>();
            try (var children = Files.list(skillsDir)) {
                for (Path skillSubdir : children.filter(Files::isDirectory).sorted().collect(Collectors.toList())) {
                    Path skillMd = skillSubdir.resolve("SKILL.md");
                    if (!Files.exists(skillMd)) {
                        continue;
                    }
                    String skillName = skillSubdir.getFileName().toString();
                    String containerSkillDir = SKILL_DIR + "/" + skillName;
                    awaitResult(env.exec("mkdir -p " + containerSkillDir, 10, null, null));
                    copyExistingFile(env, skillMd, containerSkillDir + "/SKILL.md");
                    loadedSkills.add(skillName);
                    LOGGER.info("    Loaded skill: {}", skillName);

                    try (var extraFiles = Files.list(skillSubdir)) {
                        for (Path extra : extraFiles.filter(Files::isRegularFile).collect(Collectors.toList())) {
                            if ("SKILL.md".equals(extra.getFileName().toString())) {
                                continue;
                            }
                            copyExistingFile(env, extra, containerSkillDir + "/" + extra.getFileName());
                        }
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load skills from dir " + skillsDir, exception);
            }
            this.allSkillNames = loadedSkills;
            if (!loadedSkills.isEmpty()) {
                this.resolvedSkillName = loadedSkills.get(0);
            }
            return loadedSkills;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<AgentRunResult> run(
            DockerEnvironment env,
            Task task,
            AgentContext context) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            int iteration = context.getIteration();
            boolean hasSkill = context.isHasSkill();
            String evolutionSuggestions = context.getEvolutionSuggestions();
            IterationResult previousResult = context.getPreviousResult();

            LOGGER.info("Running JiuWenSwarm (iteration {})...", iteration);
            if (hasSkill) {
                LOGGER.info("  Using existing skill from previous iteration");
                if (evolutionSuggestions != null && !evolutionSuggestions.isBlank()) {
                    LOGGER.info("  Evolution suggestions provided");
                }
            } else if (booleanConfig("evolution_enabled", false)) {
                LOGGER.info("  No existing skill, will create new skill");
            } else {
                LOGGER.info("  Single-run mode: executing task without skill evolution");
            }

            copyTempFile(env, "jiuwenswarm_instruction", task.getInstruction(), "/tmp/jiuwenswarm_instruction.txt");
            String systemMessage = buildSystemMessage(iteration, hasSkill, evolutionSuggestions, previousResult);
            if (!systemMessage.isBlank()) {
                copyTempFile(env, "jiuwenswarm_system_message", systemMessage, "/tmp/jiuwenswarm_system_message.txt");
            }
            copyTempFile(env, "jiuwenswarm_runner", getRunnerScript(), "/tmp/jiuwenswarm_runner.py");

            long start = System.nanoTime();
            int evolutionWait = hasSkill ? intConfig("evolution_wait_time", 60) : 0;
            int agentTimeout = intConfig("agent_timeout", 880);
            ExecResult result = awaitResult(env.exec(
                    "JIUWENSWARM_EVOLUTION_WAIT=" + evolutionWait
                            + " JIUWENSWARM_AGENT_TIMEOUT=" + agentTimeout
                            + " python3 /tmp/jiuwenswarm_runner.py",
                    agentTimeout + evolutionWait + 30,
                    null,
                    null));
            double executionTime = (System.nanoTime() - start) / 1_000_000_000.0;

            String rawOutput = result.getStdout();
            String stderr = result.getStderr();
            Path debugDir = Path.of(System.getProperty("java.io.tmpdir"), "jiuwenswarm_debug");
            try {
                Files.createDirectories(debugDir);
                Files.writeString(debugDir.resolve("raw_output.txt"), rawOutput, StandardCharsets.UTF_8);
                if (stderr != null && !stderr.isBlank()) {
                    Files.writeString(debugDir.resolve("stderr.txt"), stderr, StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // Debug artifacts are best-effort only.
            }

            List<Map<String, Object>> trajectory = new ArrayList<>();
            String finalResponse = "";
            int tokensUsed = 0;
            List<Map<String, Object>> evolutionEvents = new ArrayList<>();
            Map<String, Object> metadata = new LinkedHashMap<>();

            Map<String, Object> parsed = parseOutput(rawOutput);
            if (parsed != null) {
                trajectory = castMessageList(parsed.get("messages"));
                finalResponse = String.valueOf(parsed.getOrDefault("final_response", ""));
                evolutionEvents = castMessageList(parsed.get("evolution_events"));
                metadata = castObjectMap(parsed.get("metadata"));
                tokensUsed = estimateTokens(trajectory);
                LOGGER.info("  Parsed trajectory: {} messages", trajectory.size());
                if (!evolutionEvents.isEmpty()) {
                    LOGGER.info("  Evolution events: {}", evolutionEvents.size());
                }
                if (!metadata.isEmpty()) {
                    LOGGER.info("  Metadata captured: {} keys", metadata.keySet().size());
                }
            } else {
                LOGGER.warning("  Warning: Failed to parse JiuWenSwarm output");
                LOGGER.debug("  Raw output length: {} chars", rawOutput.length());
                LOGGER.debug("  Stderr length: {} chars", stderr != null ? stderr.length() : 0);
            }

            Map<String, Object> logsInfo = new LinkedHashMap<>();
            Map<String, String> llmLogsFound = captureLlmLogs(env);
            if (!llmLogsFound.isEmpty()) {
                logsInfo.put("llm", llmLogsFound);
            }
            if (!logsInfo.isEmpty()) {
                metadata.put("logs", logsInfo);
            }

            AgentRunResult runResult = new AgentRunResult();
            runResult.setFinalResponse(finalResponse);
            runResult.setTrajectory(trajectory);
            runResult.setExecutionTime(executionTime);
            runResult.setTokensUsed(tokensUsed);
            runResult.setRawOutput(rawOutput);
            runResult.setStderr(stderr);
            runResult.setEvolutionEvents(evolutionEvents);
            runResult.setMetadata(metadata);
            runResult.setLlmLogs(llmLogsFound.isEmpty() ? null : llmLogsFound);
            return runResult;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<SkillDelta> captureSkills(DockerEnvironment env) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Capturing created skills...");
            Map<String, String> evolutionContents = new LinkedHashMap<>();
            Map<String, Map<String, String>> capturedEvolutionFiles = new LinkedHashMap<>();

            ExecResult evoResult = awaitResult(env.exec("find " + SKILL_DIR + " -name 'evolutions.json' 2>/dev/null", 30, null, null));
            if (evoResult.isSuccess() && !evoResult.getStdout().isBlank()) {
                List<String> evoFiles = nonBlankLines(evoResult.getStdout());
                LOGGER.info("  Found {} evolutions.json files", evoFiles.size());
                for (String evoFile : evoFiles) {
                    String skillName = Path.of(evoFile).getParent().getFileName().toString();
                    ExecResult readResult = awaitResult(env.exec("cat " + evoFile, 30, null, null));
                    if (readResult.isSuccess()) {
                        evolutionContents.put(skillName, readResult.getStdout());
                        LOGGER.info("  {}/evolutions.json: {} chars", skillName, readResult.getStdout().length());
                    }
                }
            }

            capturedEvolutionJson.clear();
            ExecResult evoJsonResult = awaitResult(env.exec("find " + WORKSPACE_DIR + " -name 'evolution.json' 2>/dev/null", 30, null, null));
            if (evoJsonResult.isSuccess() && !evoJsonResult.getStdout().isBlank()) {
                List<String> evoJsonFiles = nonBlankLines(evoJsonResult.getStdout());
                LOGGER.info("  Found {} evolution.json files", evoJsonFiles.size());
                for (String evoJsonFile : evoJsonFiles) {
                    ExecResult readResult = awaitResult(env.exec("cat " + evoJsonFile, 30, null, null));
                    if (readResult.isSuccess()) {
                        String filename = Path.of(evoJsonFile).getFileName().toString();
                        capturedEvolutionJson.put(filename, readResult.getStdout());
                        LOGGER.info("  Captured {}: {} chars", evoJsonFile, readResult.getStdout().length());
                    }
                }
            }

            ExecResult evoMdResult = awaitResult(env.exec("find " + SKILL_DIR + " -path '*/evolution/*.md' 2>/dev/null", 30, null, null));
            if (evoMdResult.isSuccess() && !evoMdResult.getStdout().isBlank()) {
                List<String> evoMdFiles = nonBlankLines(evoMdResult.getStdout());
                LOGGER.info("  Found {} evolution/*.md files", evoMdFiles.size());
                for (String mdFile : evoMdFiles) {
                    Path mdPath = Path.of(mdFile);
                    String skillName = mdPath.getParent().getParent().getFileName().toString();
                    String filename = mdPath.getFileName().toString();
                    ExecResult readResult = awaitResult(env.exec("cat " + mdFile, 30, null, null));
                    if (readResult.isSuccess()) {
                        capturedEvolutionFiles.computeIfAbsent(skillName, ignored -> new LinkedHashMap<>())
                                .put(filename, readResult.getStdout());
                        LOGGER.info("  {}/evolution/{}: {} chars", skillName, filename, readResult.getStdout().length());
                    }
                }
            }

            ExecResult skillResult = awaitResult(env.exec("find " + SKILL_DIR + " -name 'SKILL.md' 2>/dev/null", 30, null, null));
            if (!skillResult.isSuccess() || skillResult.getStdout().isBlank()) {
                SkillDelta delta = new SkillDelta();
                delta.setEvolutions(evolutionContents);
                delta.setEvolutionFiles(capturedEvolutionFiles);
                return delta;
            }

            Map<String, String> createdSkills = new LinkedHashMap<>();
            for (String skillFile : nonBlankLines(skillResult.getStdout())) {
                String skillName = Path.of(skillFile).getParent().getFileName().toString();
                ExecResult readResult = awaitResult(env.exec("cat " + skillFile, 30, null, null));
                if (readResult.isSuccess()) {
                    createdSkills.put(skillName, readResult.getStdout());
                }
            }

            boolean changed = !createdSkills.isEmpty() || !evolutionContents.isEmpty() || !capturedEvolutionFiles.isEmpty();
            LOGGER.info(
                    "Captured {} skills, {} evolutions, {} evolution files",
                    createdSkills.size(),
                    evolutionContents.size(),
                    capturedEvolutionFiles.values().stream().mapToInt(Map::size).sum());

            SkillDelta delta = new SkillDelta();
            delta.setSkills(createdSkills);
            delta.setEvolutions(evolutionContents);
            delta.setEvolutionFiles(capturedEvolutionFiles);
            delta.setChanged(changed);
            return delta;
        });
    }

    String buildSystemMessage(
            int iteration,
            boolean hasSkill,
            String evolutionSuggestions,
            IterationResult previousResult) {
        List<String> parts = new ArrayList<>();
        parts.add(basePrompt());
        if (!hasSkill) {
            parts.add(skillCreationPrompt());
        } else {
            parts.add(skillReadingPrompt(evolutionSuggestions));
        }
        if (previousResult != null
                && previousResult.getEvalResult() != null
                && !previousResult.getEvalResult().isPassed()) {
            parts.add(testFeedbackPrompt(previousResult));
        }
        return String.join("\n", parts);
    }

    static Map<String, Object> parseOutput(String rawOutput) {
        String startMarker = "===JIUWENSWARM_OUTPUT_START===";
        String endMarker = "===JIUWENSWARM_OUTPUT_END===";
        int startIdx = rawOutput.indexOf(startMarker);
        int endIdx = rawOutput.indexOf(endMarker);
        if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx) {
            return null;
        }
        String json = rawOutput.substring(startIdx + startMarker.length(), endIdx).trim();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (IOException exception) {
            return null;
        }
    }

    static int estimateTokens(List<Map<String, Object>> messages) {
        int total = 0;
        for (Map<String, Object> message : messages) {
            Object content = message.get("content");
            if (content instanceof String text) {
                total += text.length() / 4;
            } else if (content instanceof List<?> parts) {
                for (Object item : parts) {
                    if (item instanceof Map<?, ?> map && map.get("text") != null) {
                        total += String.valueOf(map.get("text")).length() / 4;
                    }
                }
            }
        }
        return total;
    }

    static String getRunnerScript() {
        return """
                import sys
                import os
                import json
                import traceback
                import subprocess
                import time
                import uuid
                import urllib.request
                import urllib.error
                import asyncio
                                
                os.environ["PYTHONIOENCODING"] = "utf-8"
                os.environ.setdefault("EVOLUTION_AUTO_SCAN", "true")
                os.environ.setdefault("EVOLUTION_AUTO_SAVE", "true")
                                
                _ACP_STDOUT = open(sys.stdout.fileno(), "w", closefd=False)
                _AGENT_TIMEOUT = int(os.environ.get("JIUWENSWARM_AGENT_TIMEOUT", "800"))
                                
                def _error_result(err_msg):
                    return {"final_response": "", "messages": [], "failed": True, "partial": False, "error": err_msg}
                                
                _EVOLUTION_WAIT_SECONDS = int(os.environ.get("JIUWENSWARM_EVOLUTION_WAIT", "60"))
                                
                def _wait_for_ws_port(host, port, timeout=60):
                    t0 = time.time()
                    while time.time() - t0 < timeout:
                        try:
                            conn = urllib.request.urlopen(f"http://{host}:{port}/", timeout=2)
                            conn.close()
                            return True
                        except urllib.error.HTTPError:
                            return True
                        except Exception:
                            time.sleep(0.5)
                    return False
                                
                async def _run_agent_async():
                    agent_proc = None
                    gateway_proc = None
                    final_response = ""
                    try:
                        agent_log = open("/tmp/jiuwenswarm_agent_server.log", "w")
                        agent_proc = subprocess.Popen(
                            [sys.executable, "-m", "jiuwenswarm.server.app_agentserver"],
                            stdin=subprocess.DEVNULL,
                            stdout=agent_log,
                            stderr=subprocess.STDOUT,
                        )
                        agent_host = os.environ.get("AGENT_SERVER_HOST", "127.0.0.1")
                        agent_port = int(os.environ.get("AGENT_SERVER_PORT", "18092"))
                        if not _wait_for_ws_port(agent_host, agent_port, timeout=60):
                            agent_log.close()
                            err_detail = ""
                            try:
                                with open("/tmp/jiuwenswarm_agent_server.log", "r", errors="replace") as f:
                                    err_detail = f.read()
                            except Exception:
                                pass
                            return _error_result(f"AgentServer failed to start on port {agent_port}: {err_detail}")
                                
                        gateway_log = open("/tmp/jiuwenswarm_gateway.log", "w")
                        gateway_proc = subprocess.Popen(
                            [sys.executable, "-m", "jiuwenswarm.gateway.app_gateway"],
                            stdin=subprocess.DEVNULL,
                            stdout=gateway_log,
                            stderr=subprocess.STDOUT,
                        )
                        gateway_host = os.environ.get("GATEWAY_HOST", "127.0.0.1")
                        gateway_port = int(os.environ.get("GATEWAY_PORT", "19000"))
                        if not _wait_for_ws_port(gateway_host, gateway_port, timeout=60):
                            gateway_log.close()
                            err_detail = ""
                            try:
                                with open("/tmp/jiuwenswarm_gateway.log", "r", errors="replace") as f:
                                    err_detail = f.read()
                            except Exception:
                                pass
                            return _error_result(f"Gateway failed to start on port {gateway_port}: {err_detail}")
                                
                        with open("/tmp/jiuwenswarm_instruction.txt", "r", encoding="utf-8") as f:
                            instruction = f.read().strip()
                                
                        if not instruction:
                            return _error_result("Empty instruction")
                                
                        system_message = ""
                        try:
                            with open("/tmp/jiuwenswarm_system_message.txt", "r", encoding="utf-8") as f:
                                system_message = f.read().strip()
                        except FileNotFoundError:
                            pass
                                
                        full_instruction = instruction
                        if system_message:
                            full_instruction = system_message + "\\n\\n---\\n\\n" + instruction
                                
                        try:
                            from websockets.legacy.client import connect as ws_connect
                        except ImportError:
                            from websockets import connect as ws_connect
                                
                        ws_url = f"ws://{gateway_host}:{gateway_port}/ws"
                                
                        async with ws_connect(ws_url, max_size=8 * 2**20) as ws:
                            session_id = f"harbor_{uuid.uuid4().hex[:8]}"
                            init_req_id = f"init_{uuid.uuid4().hex[:8]}"
                            init_frame = {
                                "type": "req",
                                "id": init_req_id,
                                "method": "initialize",
                                "params": {"session_id": session_id},
                            }
                            await ws.send(json.dumps(init_frame, ensure_ascii=False))
                            init_resp = None
                            t0 = time.time()
                            while (time.time() - t0) < 30:
                                try:
                                    raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                                except asyncio.TimeoutError:
                                    continue
                                data = json.loads(raw)
                                if data.get("type") == "res" and data.get("id") == init_req_id:
                                    init_resp = data
                                    break
                                if data.get("type") == "event" and data.get("event") == "connection.ack":
                                    break
                                
                            session_req_id = f"session_{uuid.uuid4().hex[:8]}"
                            session_frame = {
                                "type": "req",
                                "id": session_req_id,
                                "method": "session.create",
                                "params": {"session_id": session_id},
                            }
                            await ws.send(json.dumps(session_frame, ensure_ascii=False))
                                
                            session_resp = None
                            t0 = time.time()
                            while (time.time() - t0) < 30:
                                try:
                                    raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                                except asyncio.TimeoutError:
                                    continue
                                data = json.loads(raw)
                                if data.get("type") == "res" and data.get("id") == session_req_id:
                                    session_resp = data
                                    break
                            if session_resp and not session_resp.get("ok"):
                                err = session_resp.get("error", "unknown")
                                return _error_result(f"session.create failed: {err}")
                                
                            chat_req_id = f"chat_{uuid.uuid4().hex[:8]}"
                            chat_frame = {
                                "type": "req",
                                "id": chat_req_id,
                                "method": "chat.send",
                                "params": {
                                    "session_id": session_id,
                                    "content": full_instruction,
                                    "mode": "agent.plan",
                                },
                            }
                            await ws.send(json.dumps(chat_frame, ensure_ascii=False))
                                
                            final_response = ""
                            done = False
                            t0 = time.time()
                            messages = []
                            current_assistant_msg = {"role": "assistant", "content": ""}
                            current_tool_calls = []
                            tool_results_buffer = {}
                            evolution_events = []
                                
                            def _flush_current_round():
                                nonlocal current_assistant_msg, current_tool_calls
                                if current_assistant_msg.get("content") or current_tool_calls:
                                    if current_tool_calls:
                                        current_assistant_msg["tool_calls"] = current_tool_calls.copy()
                                    messages.append(current_assistant_msg.copy())
                                    for tool_call in current_tool_calls:
                                        tool_id = tool_call.get("id", "")
                                        tool_result = tool_results_buffer.get(tool_id, "")
                                        tool_msg = {
                                            "role": "tool",
                                            "tool_call_id": tool_id,
                                            "content": tool_result
                                        }
                                        messages.append(tool_msg)
                                current_assistant_msg = {"role": "assistant", "content": ""}
                                current_tool_calls = []
                                
                            iteration_count = 0
                            evolution_wait_start = None
                            while (time.time() - t0) < _AGENT_TIMEOUT:
                                iteration_count += 1
                                if done and evolution_wait_start is None:
                                    evolution_wait_start = time.time()
                                if evolution_wait_start is not None:
                                    evolution_elapsed = time.time() - evolution_wait_start
                                    if evolution_elapsed >= _EVOLUTION_WAIT_SECONDS:
                                        break
                                try:
                                    raw = await asyncio.wait_for(ws.recv(), timeout=1.0)
                                except asyncio.TimeoutError:
                                    continue
                                except Exception:
                                    break
                                if not raw:
                                    continue
                                try:
                                    data = json.loads(raw)
                                except json.JSONDecodeError:
                                    continue
                                frame_type = data.get("type")
                                if frame_type == "res":
                                    req_id = data.get("id")
                                    if req_id == chat_req_id and not data.get("ok"):
                                        err = data.get("error", "unknown")
                                        return _error_result(f"chat.send failed: {err}")
                                    continue
                                if frame_type == "event":
                                    event_name = data.get("event", "")
                                    payload = data.get("payload", {})
                                    if event_name == "chat.delta":
                                        if current_tool_calls and current_assistant_msg.get("content"):
                                            _flush_current_round()
                                        content = payload.get("content", "")
                                        if content:
                                            final_response += content
                                            current_assistant_msg["content"] += content
                                    elif event_name == "chat.tool_call":
                                        tool_call_info = payload.get("tool_call", {})
                                        tool_id = tool_call_info.get("tool_call_id", tool_call_info.get("id", ""))
                                        if not tool_id:
                                            tool_id = f"tool_{len(current_tool_calls)}"
                                        tool_name = tool_call_info.get("name", "unknown")
                                        tool_args = tool_call_info.get("arguments", {})
                                        tool_call_entry = {
                                            "id": tool_id,
                                            "type": "function",
                                            "function": {
                                                "name": tool_name,
                                                "arguments": json.dumps(tool_args) if isinstance(tool_args, dict) else str(tool_args)
                                            }
                                        }
                                        current_tool_calls.append(tool_call_entry)
                                    elif event_name == "chat.tool_result":
                                        tool_id = payload.get("tool_call_id", "")
                                        tool_result = payload.get("result", "")
                                        if tool_id:
                                            tool_results_buffer[tool_id] = str(tool_result)
                                    elif event_name == "chat.final":
                                        done = True
                                    elif event_name == "evolution_status":
                                        evolution_events.append({
                                            "event": "evolution_status",
                                            "status": payload.get("status"),
                                            "skill_name": payload.get("skill_name"),
                                            "request_id": payload.get("request_id"),
                                        })
                                    elif event_name == "ask_user_question":
                                        evolution_events.append({
                                            "event": "ask_user_question",
                                            "request_id": payload.get("request_id"),
                                            "is_evolution_approval": payload.get("is_evolution_approval", False),
                                        })
                                
                            _flush_current_round()
                            trajectory = [{"role": "user", "content": full_instruction}]
                            trajectory.extend(messages)
                            return {
                                "final_response": final_response,
                                "messages": trajectory,
                                "evolution_events": evolution_events,
                            }
                    except Exception as e:
                        traceback.print_exc(file=sys.stderr)
                        return _error_result(str(e))
                    finally:
                        if final_response and _EVOLUTION_WAIT_SECONDS > 0:
                            time.sleep(_EVOLUTION_WAIT_SECONDS)
                        for p in [gateway_proc, agent_proc]:
                            if p:
                                p.terminate()
                                try:
                                    p.wait(timeout=5)
                                except Exception:
                                    p.kill()
                                
                result = asyncio.run(_run_agent_async())
                _ACP_STDOUT.write("===JIUWENSWARM_OUTPUT_START===\\n")
                _ACP_STDOUT.write(json.dumps(result, ensure_ascii=False, default=str) + "\\n")
                _ACP_STDOUT.write("===JIUWENSWARM_OUTPUT_END===\\n")
                _ACP_STDOUT.flush()
                """;
    }

    String basePrompt() {
        return """
                You are an AI assistant tasked with solving command-line tasks in a Linux environment.
                                
                ## Response Format
                                
                Structure your responses clearly with these sections:
                                
                1. **Analysis**: What is the current state? What has been accomplished?
                2. **Plan**: What will you do next? Be specific about expected outcomes.
                3. **Actions**: What commands will you execute?
                4. **Status**: Is the task complete or in progress?
                                
                ## Command Execution Guidelines
                                
                - End bash commands with a newline to execute them
                - Use appropriate wait times:
                  - 0.1s: Quick commands (ls, cat, cd, echo)
                  - 1-5s: Moderate commands (pip install, git clone, npm install)
                  - 10s+: Slow commands (make, compilation, large downloads)
                - Use Ctrl+C (C-c) to interrupt stuck processes
                - Use '&&' to chain dependent commands
                - Use '2>&1' to capture stderr along with stdout
                                
                ## Error Handling
                                
                When encountering errors:
                1. Read error messages carefully
                2. Check if dependencies are installed (use 'which' or '--version')
                3. Verify file paths and permissions
                4. Try alternative approaches
                5. If stuck, explain what you've tried and what's blocking
                                
                ## Task Completion
                                
                Before marking task complete:
                1. Verify all requirements are met
                2. Check output files exist and are valid
                3. Run any provided tests if available
                4. Include "TASK COMPLETE" in your final response when done
                """;
    }

    String skillCreationPrompt() {
        return """
                                
                ## CRITICAL: Create Skills Before Solving
                                
                You must create skill documents that capture domain knowledge needed for this task.
                                
                ### Skill Creation Process
                                
                1. **Analyze the task** - What knowledge is needed?
                2. **Create focused skills** - 1-3 skills (quality over quantity)
                3. **Use bash commands to create skill files**:
                                
                ```bash
                mkdir -p ~/.jiuwenswarm/agent/workspace/skills/<skill-name>
                                
                cat > ~/.jiuwenswarm/agent/workspace/skills/<skill-name>/SKILL.md << 'EOF'
                ---
                name: <skill-name>
                description: <what this skill does in one line>
                ---
                # <Skill Title>
                                
                ## Overview
                <Brief description>
                                
                ## Steps
                1. <Step 1 with explanation>
                2. <Step 2 with explanation>
                                
                ## Code Examples
                ```language
                <example code>
                ```
                                
                ## Common Pitfalls
                - <Pitfall 1 and how to avoid>
                - <Pitfall 2 and how to avoid>
                EOF
                ```
                                
                **IMPORTANT**: Choose a descriptive skill name that reflects the skill's purpose.
                                
                ### After Creating Skills
                                
                1. **Verify**: Check the skill file exists
                2. **Use**: Follow the skill's guidance to solve the task
                3. **Iterate**: Update skills if you find better approaches
                """;
    }

    String skillReadingPrompt(String evolutionSuggestions) {
        List<String> parts = new ArrayList<>();
        if (evolutionSuggestions != null && !evolutionSuggestions.isBlank()) {
            parts.add("\n## Evolution Suggestions from Previous Iteration\n\n"
                    + "Based on the previous execution, the following improvements are recommended:\n\n"
                    + evolutionSuggestions
                    + "\n\nYou MUST address these suggestions by reading the skill and its evolution experiences.\n");
        }
        List<String> skillNames = allSkillNames.isEmpty() ? List.of(resolvedSkillName) : allSkillNames;
        if (skillNames.size() == 1) {
            parts.add(singleSkillReadingPrompt());
        } else {
            parts.add(multiSkillReadingPrompt(skillNames));
        }
        return String.join("\n", parts);
    }

    String singleSkillReadingPrompt() {
        return "\n## CRITICAL: Read Skill Before Solving\n\n"
                + "A skill has been loaded for this task. You MUST read it before starting any work.\n\n"
                + "**Step 1**: Read the skill document:\n"
                + "```bash\n"
                + "cat ~/.jiuwenswarm/agent/workspace/skills/" + resolvedSkillName + "/SKILL.md\n"
                + "```\n\n"
                + "**Step 2**: Read the evolution files for troubleshooting tips:\n"
                + "```bash\n"
                + "cat ~/.jiuwenswarm/agent/workspace/skills/" + resolvedSkillName + "/evolution/*.md\n"
                + "```\n\n"
                + "**Step 3**: Follow the skill's guidance and the evolution experiences to solve the task.\n\n"
                + "**Step 4**: After solving, update the skill based on test failures and new insights.\n\n"
                + "**Evolution is enabled**: The skill will be automatically evolved based on your execution experience.\n\n"
                + "**WARNING**: If you see an Experience Index in SKILL.md but do NOT read the linked\n"
                + "evolution files, you will miss critical details such as exact commands, parameter values,\n"
                + "and error workarounds.\n";
    }

    String multiSkillReadingPrompt(List<String> skillNames) {
        String skillList = skillNames.stream()
                .map(name -> "  - `" + name + "`: ~/.jiuwenswarm/agent/workspace/skills/" + name + "/SKILL.md")
                .collect(Collectors.joining("\n"));
        return "\n## CRITICAL: Read ALL Skills Before Solving\n\n"
                + skillNames.size() + " skills have been loaded for this task:\n\n"
                + skillList + "\n\n"
                + "**Step 1**: Read ALL skill documents and their evolution files.\n\n"
                + "**Step 2**: Follow ALL skills' guidance and evolution experiences to solve the task.\n\n"
                + "**Step 3**: After solving, update skills based on test failures and new insights.\n\n"
                + "**Evolution is enabled**: Skills will be automatically evolved based on your execution experience.\n\n"
                + "**WARNING**: If you see an Experience Index in SKILL.md but do NOT read the linked\n"
                + "evolution files, you will miss critical details.\n";
    }

    String testFeedbackPrompt(IterationResult previousResult) {
        var evalResult = previousResult.getEvalResult();
        double passRate = evalResult.getPassRate();
        List<String> failedTests = evalResult.getFailedTests();
        String testOutput = evalResult.getTestOutput();
        Map<String, String> specificErrors = SkillManager.extractSpecificErrors(testOutput);

        StringBuilder feedback = new StringBuilder("""
                                
                ## Previous Iteration Test Results
                                
                **The previous iteration did NOT pass all tests.** Pass rate: """);
        feedback.append(String.format(Locale.ROOT, "%.1f", passRate * 100)).append("%.\n\n");
        feedback.append("**Failed Tests**: ").append(failedTests.size()).append('\n');
        if (!specificErrors.isEmpty()) {
            feedback.append("\n**Specific Failure Details**:\n");
            specificErrors.entrySet().stream().limit(5).forEach(entry -> feedback
                    .append("\n### ").append(entry.getKey()).append("\n```\n")
                    .append(entry.getValue()).append("\n```\n"));
        } else if (!failedTests.isEmpty()) {
            feedback.append("\n**Failed Test Cases**:\n");
            failedTests.stream().limit(5).forEach(test -> feedback.append("- ").append(test).append('\n'));
        }
        if (testOutput != null && !testOutput.isBlank() && specificErrors.isEmpty()) {
            int start = Math.max(0, testOutput.length() - 800);
            feedback.append("\n**Test Output** (last 800 chars):\n```\n")
                    .append(testOutput.substring(start))
                    .append("\n```\n");
        }
        feedback.append("\n**You MUST read the skill and evolution experiences to fix these failures.**\n");
        return feedback.toString();
    }

    private boolean loadSingleSkill(
            DockerEnvironment env,
            String skillName,
            String skillContent,
            String evolutionContent,
            Map<String, String> evolutionFiles) {
        String skillDir = SKILL_DIR + "/" + skillName;
        awaitResult(env.exec("mkdir -p " + skillDir, 10, null, null));
        if (!copyTempFile(env, "skill_" + skillName, skillContent, skillDir + "/SKILL.md")) {
            LOGGER.error("  Failed to load skill: {}", skillName);
            return false;
        }
        LOGGER.info("  Skill loaded: {}/SKILL.md", skillDir);

        if (evolutionContent != null) {
            if (copyTempFile(env, "evolutions_" + skillName, evolutionContent, skillDir + "/evolutions.json")) {
                LOGGER.info("  Evolutions loaded: {}/evolutions.json ({} chars)", skillDir, evolutionContent.length());
            }
        }
        if (evolutionFiles != null && !evolutionFiles.isEmpty()) {
            String evolutionDir = skillDir + "/evolution";
            awaitResult(env.exec("mkdir -p " + evolutionDir, 10, null, null));
            for (Map.Entry<String, String> entry : evolutionFiles.entrySet()) {
                if (copyTempFile(env, "evolution_" + skillName + "_" + sanitizeFileName(entry.getKey()),
                        entry.getValue(), evolutionDir + "/" + entry.getKey())) {
                    LOGGER.info("  Evolution file loaded: {}/{}", evolutionDir, entry.getKey());
                }
            }
        }
        return true;
    }

    private Map<String, String> captureLlmLogs(DockerEnvironment env) {
        Map<String, String> llmLogsFound = new LinkedHashMap<>();
        List<String> llmSearchPaths = List.of(
                "./logs/llm.log",
                WORKSPACE_DIR + "/logs/llm.log",
                "/root/logs/llm.log",
                "/app/logs/llm.log",
                "/workspace/logs/llm.log",
                "/home/logs/llm.log",
                "~/.jiuwenswarm/logs/logs/llm.log",
                "~/.jiuwenswarm/agent/.logs/llm.log",
                "~/.jiuwenswarm/llm.log"
        );
        for (String llmPath : llmSearchPaths) {
            ExecResult result = awaitResult(env.exec("cat " + llmPath + " 2>/dev/null | tail -4000", 10, null, null));
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                llmLogsFound.put("llm.log", result.getStdout());
                LOGGER.info("  Found llm.log from {} ({} chars)", llmPath, result.getStdout().length());
                return llmLogsFound;
            }
        }
        captureLogsFromFind(env, "find ~/.jiuwenswarm -name 'llm.log' -type f 2>/dev/null", llmLogsFound);
        if (!llmLogsFound.isEmpty()) {
            return llmLogsFound;
        }
        captureLogsFromFind(env, "find " + WORKSPACE_DIR + " -name 'llm.log' -type f 2>/dev/null", llmLogsFound);
        if (!llmLogsFound.isEmpty()) {
            return llmLogsFound;
        }
        for (String ojPath : List.of(
                WORKSPACE_DIR + "/logs/run/jiuwen.log",
                WORKSPACE_DIR + "/logs/run/jiuwen.jsonl",
                "./logs/run/jiuwen.log",
                "./logs/run/jiuwen.jsonl",
                "/root/logs/run/jiuwen.log",
                "/root/logs/run/jiuwen.jsonl",
                "/app/logs/run/jiuwen.log",
                "/app/logs/run/jiuwen.jsonl")) {
            ExecResult result = awaitResult(env.exec("cat " + ojPath + " 2>/dev/null | tail -4000", 10, null, null));
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String ojName = Path.of(ojPath).getFileName().toString();
                llmLogsFound.put("llm_" + ojName, result.getStdout());
                LOGGER.info("  Found LLM log from {} as llm_{} ({} chars)", ojPath, ojName, result.getStdout().length());
                break;
            }
        }
        return llmLogsFound;
    }

    private void captureLogsFromFind(DockerEnvironment env, String findCommand, Map<String, String> output) {
        ExecResult searchResult = awaitResult(env.exec(findCommand, 15, null, null));
        if (!searchResult.isSuccess() || searchResult.getStdout().isBlank()) {
            return;
        }
        List<String> foundPaths = nonBlankLines(searchResult.getStdout());
        for (String foundPath : foundPaths.stream().limit(3).collect(Collectors.toList())) {
            ExecResult result = awaitResult(env.exec("cat " + foundPath + " 2>/dev/null | tail -4000", 10, null, null));
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String safeName = foundPaths.size() > 1 ? foundPath.replace("/root/.jiuwenswarm/", "")
                        .replace("/home/", "").replace('/', '_') : "llm.log";
                output.put(safeName, result.getStdout());
                LOGGER.info("  Found llm.log from {} ({} chars)", foundPath, result.getStdout().length());
            }
        }
    }

    private Optional<String> stringConfig(String key) {
        Object value = getConfig().get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }

    private boolean booleanConfig(String key, boolean fallback) {
        Object value = getConfig().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intConfig(String key, int fallback) {
        Object value = getConfig().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Path resolveProjectRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    private boolean copyTempFile(DockerEnvironment env, String prefix, String content, String destination) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(prefix, ".tmp");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            return awaitResult(env.copyTo(tempFile, destination));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write temporary file for " + destination, exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            }
        }
    }

    private void copyExistingFile(DockerEnvironment env, Path localFile, String destination) {
        if (!awaitResult(env.copyTo(localFile, destination))) {
            throw new IllegalStateException("Failed to copy " + localFile + " -> " + destination);
        }
    }

    private static <T> T awaitResult(java.util.concurrent.CompletableFuture<T> future) {
        return future.join();
    }

    private static List<String> nonBlankLines(String text) {
        return Pattern.compile("\\R").splitAsStream(text)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<Map<String, Object>> castMessageList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                output.add(castObjectMap(map));
            }
        }
        return output;
    }

    private static Map<String, Object> castObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }

    private static String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
