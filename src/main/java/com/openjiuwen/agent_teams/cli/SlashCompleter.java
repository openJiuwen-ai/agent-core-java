/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Two-level completion for Team CLI slash commands.
 *
 * <p>Mirrors Python's {@code SlashCompleter} in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
public class SlashCompleter {

    public List<CompletionItem> complete(String textBeforeCursor) {
        String text = textBeforeCursor == null ? "" : textBeforeCursor;
        List<CompletionItem> result = new ArrayList<>();
        if (!text.startsWith("/")) {
            return result;
        }
        if (!text.contains(" ")) {
            for (String command : TeamCliCommands.sortedSlashCommands()) {
                if ("/quit".equals(command)) {
                    continue;
                }
                if (command.startsWith(text)) {
                    result.add(new CompletionItem(command, TeamCliCommands.topLevelDescriptions().getOrDefault(command, "")));
                }
            }
            return result;
        }
        String[] parts = text.split(" ", 2);
        String head = parts[0];
        String tail = parts.length > 1 ? parts[1] : "";
        if (tail.contains(" ")) {
            return result;
        }
        Map<String, TeamCliCommandHandler> actions = TeamCliCommands.subActionTables().get(head);
        if (actions == null) {
            return result;
        }
        actions.keySet().stream()
                .sorted()
                .filter(action -> action.startsWith(tail))
                .map(action -> new CompletionItem(action, ""))
                .forEach(result::add);
        return result;
    }
}
