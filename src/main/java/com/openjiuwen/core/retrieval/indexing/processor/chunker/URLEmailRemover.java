/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.regex.Pattern;

/**
 * Removes URLs and email addresses from text.
 */
public class URLEmailRemover implements TextPreprocessor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern COM_PATTERN =
            Pattern.compile("(?:https?://|www\\.)?\\S+?\\.(?:com|net|org|cn)(?:[/?#]\\S*)?\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.-]+@[\\w.-]+\\.\\w+\\b");

    private final boolean removeUrls;
    private final boolean removeEmails;
    private final String replacement;

    public URLEmailRemover() {
        this(true, true, "");
    }

    public URLEmailRemover(boolean removeUrls, boolean removeEmails, String replacement) {
        this.removeUrls = removeUrls;
        this.removeEmails = removeEmails;
        this.replacement = replacement == null ? "" : replacement;
    }

    public boolean isRemoveUrls() {
        return removeUrls;
    }

    public boolean isRemoveEmails() {
        return removeEmails;
    }

    public String getReplacement() {
        return replacement;
    }

    @Override
    public String process(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String processed = text;
        if (removeUrls) {
            processed = URL_PATTERN.matcher(processed).replaceAll(replacement);
            processed = COM_PATTERN.matcher(processed).replaceAll(replacement);
        }
        if (removeEmails) {
            processed = EMAIL_PATTERN.matcher(processed).replaceAll(replacement);
        }
        return processed;
    }
}
