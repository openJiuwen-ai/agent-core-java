/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.List;
import java.util.Map;

/**
 * Base configuration for result ranking in hybrid graph search.
 * <p>
 * Mirrors Python's {@code BaseRankConfig} in
 * {@code openjiuwen/core/foundation/store/graph/result_ranking.py}.
 */
public abstract class BaseRankConfig {

    private String name = "base";
    private boolean higherIsBetter = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    public void setHigherIsBetter(boolean higherIsBetter) {
        this.higherIsBetter = higherIsBetter;
    }

    public List<Integer> getIsActive() {
        return List.of(1, 1, 1);
    }

    public Object getRankerCls(String database) {
        return RankConfigRegistry.getRankerCls(database, name);
    }

    public abstract RankerArgs getArgs();

    public static final class RankerArgs {

        private final List<Object> positional;
        private final Map<String, Object> keyword;

        public RankerArgs(List<Object> positional, Map<String, Object> keyword) {
            this.positional = positional;
            this.keyword = keyword;
        }

        public List<Object> getPositional() {
            return positional;
        }

        public Map<String, Object> getKeyword() {
            return keyword;
        }
    }
}
