/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Bash tool metadata provider.
 *
 * <p>Aligned with Python openjiuwen's harness.prompts.tools.bash.
 *
 * @since 0.1.12
 */
public final class BashMetadataProvider implements ToolMetadataProvider {
  private static final String DESCRIPTION_CN =
      "执行 Shell 命令并返回输出。\n\n"
          + "工作目录在命令之间保持不变，但 Shell 状态（变量、函数、alias）不保留。"
          + "Shell 环境从用户的 profile（bash 或 zsh）初始化。\n\n"
          + "重要：避免使用本工具执行 `find`、`grep`、`cat`、`head`、`tail`、`sed`、`awk` 或 `echo` "
          + "命令，除非明确指示或确认专用工具无法完成任务。请使用对应的专用工具，以获得更好的体验。";

  private static final String DESCRIPTION_EN =
      "Executes a given bash command and returns its output.\n\n"
          + "The working directory persists between commands, but shell state (variables,"
          + " functions, aliases) does not. The shell environment is initialized from the user's"
          + " profile (bash or zsh).\n\n"
          + "IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`,"
          + " `awk`, or `echo` commands, unless explicitly instructed or after you have verified"
          + " that a dedicated tool cannot accomplish your task.";

  @Override
  /** Auto-generated for codecheck compliance. */
  public String getName() {
    return "bash";
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public String getDescription(String language) {
    return ToolSchemaSupport.localized(language, DESCRIPTION_CN, DESCRIPTION_EN);
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getInputParams(String language) {
    return ToolSchemaSupport.objectSchema(
        ToolSchemaSupport.properties(
            new Object[] {
              "command",
                  ToolSchemaSupport.property(
                      "string", text(language, "要执行的命令", "The command to execute")),
              "timeout",
                  ToolSchemaSupport.property(
                      "integer",
                      text(
                          language,
                          "可选超时时间（秒），默认 300，上限 3600",
                          "Optional timeout in seconds, default 300, max 3600")),
              "description",
                  ToolSchemaSupport.property(
                      "string",
                      text(
                          language,
                          "用简洁的主动语态描述该命令的作用",
                          "Clear, concise description of what this command does in active voice")),
              "run_in_background",
                  ToolSchemaSupport.property(
                      "boolean",
                      text(
                          language,
                          "设为 true 以后台运行命令，仅在不需要立即获取结果时使用",
                          "Set to true to run this command in the background")),
              "workdir",
                  ToolSchemaSupport.property(
                      "string",
                      text(
                          language,
                          "执行目录（相对或绝对路径），默认为工作区根目录；不能越出工作区沙箱",
                          "Working directory, relative or absolute, defaults to workspace root")),
              "max_output_chars",
                  ToolSchemaSupport.property(
                      "integer",
                      text(
                          language,
                          "最大输出字符数，默认 8000（上限 20000），防止超大输出撑爆上下文",
                          "Max output characters, default 8000 and max 20000")),
              "shell_type",
                  ToolSchemaSupport.enumProperty(
                      "string",
                      List.of("auto", "cmd", "powershell", "bash", "sh"),
                      text(
                          language,
                          "指定 Shell 类型，可选值：auto/cmd/powershell/bash/sh，默认 auto（自动检测）",
                          "Shell to use: auto/cmd/powershell/bash/sh, default auto"))
            }),
        List.of("command"));
  }

  private String text(String language, String cn, String en) {
    return ToolSchemaSupport.localized(language, cn, en);
  }
}
