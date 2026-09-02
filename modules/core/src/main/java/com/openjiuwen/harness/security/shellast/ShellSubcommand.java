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
 * A single executable subcommand extracted from a shell command.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.shell_ast.ShellSubcommand}.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellSubcommand {
    private String text;
    @Builder.Default
    private List<String> argv = new ArrayList<>();
    @Builder.Default
    private List<String> redirects = new ArrayList<>();
}
