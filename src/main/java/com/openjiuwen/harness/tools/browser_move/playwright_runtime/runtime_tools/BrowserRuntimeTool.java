package com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class BrowserRuntimeTool extends Tool {
    protected final BrowserAgentRuntime runtime;
    protected BrowserRuntimeTool(BrowserAgentRuntime runtime, ToolCard card) {
        super(card);
        this.runtime = runtime;
    }

    protected static ToolCard card(String name) {
        ToolCard card = new ToolCard();
        try {
            java.lang.reflect.Field id = findField(ToolCard.class, "id");
            java.lang.reflect.Field n = findField(ToolCard.class, "name");
            java.lang.reflect.Field d = findField(ToolCard.class, "description");
            id.setAccessible(true); n.setAccessible(true); d.setAccessible(true);
            id.set(card, "browser." + name);
            n.set(card, name);
            d.set(card, name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return card;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return List.of(invoke(inputs, kwargs)).iterator();
    }
}
