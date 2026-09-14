/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

/**
 * Lightweight completion candidate for slash-command input.
 *
 * <p>Mirrors Python's {@code Completion} values yielded by {@code SlashCompleter} in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
public record CompletionItem(String text, String displayMeta) {
}
