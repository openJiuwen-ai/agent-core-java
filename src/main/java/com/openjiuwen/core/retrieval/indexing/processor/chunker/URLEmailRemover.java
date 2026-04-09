  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Removes URLs and email addresses from text.
 */
public class URLEmailRemover implements TextPreprocessor {

    @Override
    public String process(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "")
                .trim();
    }
}
