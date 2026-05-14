/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of callback chain execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainResult {

    /** Final action taken by the chain. */
    private ChainAction action;

    /** Final result value. */
    private Object result;

    /** The chain execution context. */
    private ChainContext context;

    /** Exception if chain failed. */
    private Exception error;

    public static ChainResultBuilder builder() {
        return new ChainResultBuilder();
    }

    public ChainAction getAction() {
        return action;
    }

    public void setAction(ChainAction action) {
        this.action = action;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public ChainContext getContext() {
        return context;
    }

    public void setContext(ChainContext context) {
        this.context = context;
    }

    public Exception getError() {
        return error;
    }

    public void setError(Exception error) {
        this.error = error;
    }

    public static final class ChainResultBuilder {
        private ChainAction action;
        private Object result;
        private ChainContext context;
        private Exception error;

        public ChainResultBuilder action(ChainAction action) {
            this.action = action;
            return this;
        }

        public ChainResultBuilder result(Object result) {
            this.result = result;
            return this;
        }

        public ChainResultBuilder context(ChainContext context) {
            this.context = context;
            return this;
        }

        public ChainResultBuilder error(Exception error) {
            this.error = error;
            return this;
        }

        public ChainResult build() {
            ChainResult chainResult = new ChainResult();
            chainResult.setAction(action);
            chainResult.setResult(result);
            chainResult.setContext(context);
            chainResult.setError(error);
            return chainResult;
        }
    }
}
