/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing a shell command for permission checks.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.shell_ast.ShellAstParseResult}.
 * The {@code kind} is one of:
 * <ul>
 *   <li>{@code simple} — trustworthy subcommands are available for per-subcommand evaluation</li>
 *   <li>{@code too_complex} — a parser backend succeeded but the command should not be trusted</li>
 *   <li>{@code parse_unavailable} — no trustworthy backend; callers must fail closed</li>
 * </ul>
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellAstParseResult {
    private String kind;
    @Builder.Default
    private List<ShellSubcommand> subcommands = new ArrayList<>();
    @Builder.Default
    private ShellStructureFlags flags = ShellStructureFlags.builder().build();
    private String reason;
    @Builder.Default
    private String backend = "fallback";
}
