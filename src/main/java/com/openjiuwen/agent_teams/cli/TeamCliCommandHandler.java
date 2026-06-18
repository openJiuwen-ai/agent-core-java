/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Async slash-command handler.
 *
 * <p>Mirrors Python's {@code CommandHandler} alias in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
@FunctionalInterface
public interface TeamCliCommandHandler {

    CompletionStage<Void> handle(CommandContext context, List<String> args);
}
