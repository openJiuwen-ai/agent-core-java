/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import java.util.List;

/**
 * Legacy package alias for {@link com.openjiuwen.dev_tools.tune.dataset.CaseLoader}.
 *
 * <p>Mirrors Python's {@code CaseLoader} in
 * {@code openjiuwen/dev_tools/tune/dataset/case_loader.py}.</p>
 */
public class CaseLoader extends com.openjiuwen.dev_tools.tune.dataset.CaseLoader {

    public CaseLoader() {
        super(List.of());
    }

    public CaseLoader(List<Case> cases) {
        super(cases);
    }
}
