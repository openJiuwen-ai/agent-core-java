/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RLHFDataset variant tailored for agent-RL training.
 *
 * <p>Disables prompt filtering and adds the `index` / `fake_ids` fields required by the
 * Python caller contract.</p>
 *
 * <p>Mirrors Python's {@code AgentDataset} in
 * {@code openjiuwen/agent_evolving/agent_rl/dataset.py}.</p>
 *
 * <p>Java adaptation note: tensor-like runtime values are represented with Java collections
 * because PyTorch / Verl are not present in the current target.</p>
 */
public class AgentDataset {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private boolean filterOverlongPrompts = false;
    private Object dataFiles;
    private Object dataframe;
    private Object tokenizer;
    private Object processor;
    private Object config;

    public AgentDataset() {
        this(null, null, null, null);
    }

    public AgentDataset(Object dataFiles, Object tokenizer, Object processor, Object config) {
        this.dataFiles = dataFiles;
        this.tokenizer = tokenizer;
        this.processor = processor;
        this.config = config;
        if (isInMemoryDataframe(dataFiles)) {
            this.dataframe = dataFiles;
        }
        this.filterOverlongPrompts = false;
    }

    public Map<String, Object> getItem(int index) {
        Map<String, Object> rowDict = rowAt(index);
        Object extraInfo = rowDict.get("extra_info");
        if (extraInfo instanceof String text) {
            extraInfo = parseExtraInfo(text);
        }

        int dataIndex = 0;
        if (extraInfo instanceof Map<?, ?> infoMap) {
            dataIndex = toInt(infoMap.get("index"), 0);
        }

        rowDict.put("index", dataIndex);
        rowDict.put("fake_ids", List.of(1));
        rowDict.put("fakeIds", List.of(1));
        return rowDict;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowAt(int index) {
        Object source = dataframe;
        if (source == null) {
            throw new IllegalStateException("dataframe is not initialized; provide in-memory rows or setDataframe first");
        }
        Object row;
        if (source instanceof List<?> rows) {
            row = rows.get(index);
        } else if (source instanceof Object[] rows) {
            row = rows[index];
        } else if (source instanceof Map<?, ?> rows) {
            row = rows.containsKey(index) ? rows.get(index) : rows.get(String.valueOf(index));
        } else {
            throw new IllegalStateException("unsupported dataframe type: " + source.getClass().getName());
        }
        if (!(row instanceof Map<?, ?> rowMap)) {
            throw new IllegalStateException("dataset row must be a map");
        }
        return (Map<String, Object>) rowMap;
    }

    private static boolean isInMemoryDataframe(Object dataFiles) {
        return dataFiles instanceof List<?> || dataFiles instanceof Object[] || dataFiles instanceof Map<?, ?>;
    }

    private static Map<String, Object> parseExtraInfo(String extraInfo) {
        if (extraInfo == null || extraInfo.isBlank()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(extraInfo, MAP_TYPE);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public boolean isFilterOverlongPrompts() {
        return filterOverlongPrompts;
    }

    public void setFilterOverlongPrompts(boolean filterOverlongPrompts) {
        this.filterOverlongPrompts = filterOverlongPrompts;
    }

    public Object getDataFiles() {
        return dataFiles;
    }

    public void setDataFiles(Object dataFiles) {
        this.dataFiles = dataFiles;
        if (isInMemoryDataframe(dataFiles)) {
            this.dataframe = dataFiles;
        }
    }

    public Object getDataframe() {
        return dataframe;
    }

    public void setDataframe(Object dataframe) {
        this.dataframe = Objects.requireNonNull(dataframe, "dataframe");
    }

    public Object getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    public Object getProcessor() {
        return processor;
    }

    public void setProcessor(Object processor) {
        this.processor = processor;
    }

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
    }
}
