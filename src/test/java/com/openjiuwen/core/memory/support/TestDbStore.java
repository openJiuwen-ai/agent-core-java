/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.support;

import com.openjiuwen.spi.store.BaseDbStore;

import javax.sql.DataSource;

public class TestDbStore extends BaseDbStore<DataSource> {

    private final DataSource dataSource;

    public TestDbStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DataSource getEngine() {
        return dataSource;
    }
}
