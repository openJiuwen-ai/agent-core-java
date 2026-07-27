/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.memory.lite.MemoryIndexManager;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.CodingMemoryPromptSections;
import com.openjiuwen.harness.prompts.sections.MemoryPromptSections;
import com.openjiuwen.harness.rails.CodingMemoryRail;
import com.openjiuwen.harness.rails.MemoryRail;
import com.openjiuwen.harness.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Team member memory manager. */
public class TeamMemoryManager {
  /** Auto-generated for codecheck compliance. */
  public static final String SECTION_NAME = "team_memory";

  private static final int MAX_PERSONAL_MEMORY_BYTES = 10 * 1024;

  private final TeamMemoryManagerParams params;
  private Workspace workspace;
  private MemberMemoryToolkit toolkit;
  private SharedMemoryManager sharedManager;
  private String cachedPromptBlock;
  private PromptSection cachedBaseSection;
  private final Set<String> ownedToolNames = new LinkedHashSet<>();
  private final Set<String> ownedToolIds = new LinkedHashSet<>();
  private DeepAgent deepAgentForCleanup;

  /** Auto-generated for codecheck compliance. */
  public TeamMemoryManager(TeamMemoryManagerParams params) {
    this.params = params;
    if (params.getReadOnlySourceWorkspace() != null
        && !params.getReadOnlySourceWorkspace().isBlank()) {
      this.workspace = new Workspace(params.getReadOnlySourceWorkspace(),
          params.getLanguage() != null ? params.getLanguage().getValue() : "cn");
    } else {
      this.workspace = params.getWorkspace();
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean initToolkit() throws IOException {
    if (toolkit != null && toolkit.isInitialized()) {
      return true;
    }
    if (workspace == null) {
      return false;
    }
    toolkit =
        new MemberMemoryToolkit(
            params.getMemberName(),
            params.getTeamName(),
            workspace,
            params.getScenario() != null ? params.getScenario().getValue() : "general",
            convertEmbeddingConfig(params.getEmbeddingConfig()),
            params.getSysOperation() instanceof SysOperation sysOperation ? sysOperation : null,
            params.getReadOnlySourceWorkspace() != null
                && !params.getReadOnlySourceWorkspace().isBlank());
    boolean isInitialized = toolkit.initialize();
    if (isInitialized
        && params.getTeamMemoryDir() != null
        && !params.getTeamMemoryDir().isBlank()) {
      sharedManager = new SharedMemoryManager(params.getTeamMemoryDir(), params.getSysOperation());
      sharedManager.ensureDir();
    }
    return isInitialized;
  }

  /** Auto-generated for codecheck compliance. */
  public List<LocalFunction> registerTools() {
    return toolkit != null ? toolkit.getTools() : List.of();
  }

  /** Auto-generated for codecheck compliance. */
  public void registerTools(DeepAgent deepAgent) {
    if (!ownedToolNames.isEmpty() || toolkit == null || deepAgent == null) {
      return;
    }
    int railsRemoved = stripMemoryRailsFromDeepAgent(deepAgent);
    if (railsRemoved > 0) {
      Loggers.MEMORY.info(
          "[TeamMemoryManager] Stripped {} memory rail(s) for {}.{}",
          railsRemoved,
          params.getTeamName(),
          params.getMemberName());
    }
    deepAgentForCleanup = deepAgent;
    for (LocalFunction tool : toolkit.getTools()) {
      if (tool.getCard() == null
          || tool.getCard().getId() == null
          || tool.getCard().getId().isBlank()) {
        continue;
      }
      String toolId = tool.getCard().getId();
      try {
        Object existing = Runner.resourceMgr().getTool(toolId);
        if (!(existing instanceof Tool)) {
          Result<?> added = Runner.resourceMgr().addTool(tool, tool.getCard().getId());
          if (added.isOk()) {
            ownedToolIds.add(toolId);
          }
        }
        deepAgent.getAbilityManager().add(tool.getCard());
        ownedToolNames.add(tool.getCard().getName());
      } catch (IllegalStateException | IllegalArgumentException e) {
        Loggers.MEMORY.warning(
            "[TeamMemoryManager] Failed to register tool {}: {}", toolId, e.getMessage());
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public String loadAndInject(String query) throws IOException {
    PromptSection base = getOrBuildBaseSection();
    String language = languageKey();
    StringBuilder builder = new StringBuilder(base.render(language));
    String personalMemory = fetchPersonalMemoryForPrompt(query);
    if (personalMemory != null && !personalMemory.isBlank()) {
      builder
          .append(isCn() ? "\n\n## 你的相关记忆\n\n" : "\n\n## Your relevant memories\n\n")
          .append(personalMemory);
    }
    if (sharedManager != null) {
      String teamSummary = sharedManager.readTeamSummary();
      if (!teamSummary.isBlank()) {
        builder
            .append(isCn() ? "\n\n## 团队共享记忆\n\n" : "\n\n## Team shared memory\n\n")
            .append(teamSummary);
      }
    }
    cachedPromptBlock = builder.toString().trim();
    return cachedPromptBlock;
  }

  /** Auto-generated for codecheck compliance. */
  public void loadAndInject(DeepAgent deepAgent, String query) throws IOException {
    String promptBlock = loadAndInject(query);
    if (deepAgent == null || promptBlock.isBlank()) {
      return;
    }
    // Inject via context values; the prompt builder is accessed through the agent's rails.
    // Store the section for the DeepAgent to pick up during its next model call.
    deepAgent.getAbilityManager().add(com.openjiuwen.core.foundation.tool.ToolCard.builder()
        .id(SECTION_NAME)
        .name(SECTION_NAME)
        .description(promptBlock)
        .build());
  }

  /** Auto-generated for codecheck compliance. */
  public void extractAfterRound(String entry) throws IOException {
    if (sharedManager == null || !params.isAutoExtractEnabled()) {
      return;
    }
    if (entry != null && !entry.isBlank()) {
      sharedManager.appendEntry(entry);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void extractAfterRound() throws IOException {
    if (!params.isAutoExtractEnabled()
        || params.getLifecycle() != TeamLifecycle.PERSISTENT
        || params.getRole() != TeamRole.LEADER
        || params.getTeamMemoryDir() == null
        || params.getTeamMemoryDir().isBlank()
        || params.getDb() == null) {
      return;
    }
    TeamMemoryExtractor.extractTeamMemories(
        params.getTeamName(),
        params.getDb(),
        params.getTaskManager(),
        params.getTeamMemoryDir(),
        (SysOperation) params.getSysOperation(),
        params.getExtractionModel(),
        params.getTimezoneOffsetHours());
  }

  /** Auto-generated for codecheck compliance. */
  public void close() {
    if (deepAgentForCleanup != null) {
      for (String toolName : ownedToolNames) {
        deepAgentForCleanup.getAbilityManager().remove(toolName);
      }
    }
    for (String toolId : ownedToolIds) {
      try {
        Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
      } catch (IllegalStateException | IllegalArgumentException e) {
        Loggers.MEMORY.warning(
            "[TeamMemoryManager] remove_tool({}) failed: {}", toolId, e.getMessage());
      }
    }
    deepAgentForCleanup = null;
    ownedToolNames.clear();
    ownedToolIds.clear();
    if (toolkit != null) {
      toolkit.close();
      toolkit = null;
    }
    cachedBaseSection = null;
    cachedPromptBlock = null;
  }

  /** Auto-generated for codecheck compliance. */
  public MemberMemoryToolkit getToolkit() {
    return toolkit;
  }

  /** Auto-generated for codecheck compliance. */
  public SharedMemoryManager getSharedManager() {
    return sharedManager;
  }

  /** Auto-generated for codecheck compliance. */
  public String getCachedPromptBlock() {
    return cachedPromptBlock;
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> getOwnedToolNames() {
    return Set.copyOf(ownedToolNames);
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> getOwnedToolIds() {
    return Set.copyOf(ownedToolIds);
  }

  private int stripMemoryRailsFromDeepAgent(DeepAgent deepAgent) {
    int removed = 0;
    for (Object rail : new ArrayList<>(deepAgent.getRails())) {
      if (rail instanceof MemoryRail || rail instanceof CodingMemoryRail) {
        try {
          if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepAgentRail) {
            deepAgentRail.uninit(deepAgent);
          }
          deepAgent.getRails().remove(rail);
          removed++;
        } catch (IllegalStateException | IllegalArgumentException e) {
          Loggers.MEMORY.warning(
              "[TeamMemoryManager] Failed to strip memory rail: {}", e.getMessage());
        }
      }
    }
    return removed;
  }

  private String fetchPersonalMemoryForPrompt(String query) throws IOException {
    if (toolkit == null || toolkit.getMemoryDir() == null) {
      return null;
    }
    MemoryIndexManager indexManager = toolkit.getManager();
    if (indexManager != null && query != null && !query.isBlank()) {
      List<String> parts = new ArrayList<>();
      int totalBytes = 0;
      List<Map<String, Object>> searchResults = indexManager.search(query, Map.of("max_results", 5)).join();
      for (var result : searchResults) {
        String path = String.valueOf(result.get("path"));
        if (path.endsWith("MEMORY.md")) {
          continue;
        }
        Map<String, Object> fileContent = indexManager.readFile(path, null, null).join();
        String content = String.valueOf(fileContent.getOrDefault("text", ""));
        if (content.isBlank()) {
          continue;
        }
        int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (totalBytes + contentBytes > MAX_PERSONAL_MEMORY_BYTES) {
          int remaining = MAX_PERSONAL_MEMORY_BYTES - totalBytes;
          if (remaining > 200) {
            parts.add(
                "### "
                    + path
                    + "\n\n"
                    + content.substring(0, Math.min(content.length(), remaining))
                    + "\n... (truncated)");
          }
          break;
        }
        parts.add("### " + path + "\n\n" + content);
        totalBytes += contentBytes;
      }
      if (!parts.isEmpty()) {
        return String.join("\n\n---\n\n", parts);
      }
    }
    Path indexPath = toolkit.getMemoryDir().resolve("MEMORY.md");
    if (!Files.exists(indexPath)) {
      return null;
    }
    String content = Files.readString(indexPath, StandardCharsets.UTF_8);
    if (content.isBlank()) {
      return null;
    }
    return content.strip();
  }

  private PromptSection getOrBuildBaseSection() {
    if (cachedBaseSection != null) {
      return cachedBaseSection;
    }
    String language = languageKey();
    boolean isReadOnly =
        params.getReadOnlySourceWorkspace() != null
            && !params.getReadOnlySourceWorkspace().isBlank();
    String scenario = params.getScenario() != null ? params.getScenario().getValue() : "general";
    if ("coding".equals(scenario)) {
      String memoryDir =
          workspace != null ? workspace.getNodePath("coding_memory").toString() : "coding_memory/";
      cachedBaseSection =
          CodingMemoryPromptSections.buildCodingMemorySection(language, isReadOnly, memoryDir);
    } else {
      cachedBaseSection =
          MemoryPromptSections.buildMemorySection(
              language, isReadOnly, params.getPromptMode() == PromptMode.PROACTIVE);
    }
    return cachedBaseSection;
  }

  private String languageKey() {
    return params.getLanguage() != null
        ? params.getLanguage().getValue()
        : com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder.DEFAULT_LANGUAGE;
  }

  private boolean isCn() {
    return "cn".equalsIgnoreCase(languageKey());
  }

  private static EmbeddingConfig convertEmbeddingConfig(
          com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig source) {
    if (source == null) {
      return null;
    }
    return EmbeddingConfig.builder()
        .modelName(source.getModelName())
        .baseUrl(source.getBaseUrl())
        .apiKey(source.getApiKey())
        .build();
  }

  /** Auto-generated for codecheck compliance. */
  public void setExtractionModel(Model extractionModel) {
    params.setExtractionModel(extractionModel);
  }

  /** Auto-generated for codecheck compliance. */
  public Model getExtractionModel() {
    return params.getExtractionModel();
  }
}
