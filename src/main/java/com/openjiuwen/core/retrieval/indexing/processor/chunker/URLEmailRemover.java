/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/** Removes URLs and email addresses from text. */
public class URLEmailRemover implements TextPreprocessor {
  private final boolean isRemoveUrls;
  private final boolean isRemoveEmails;
  private final String replacement;

  /** Auto-generated for codecheck compliance. */
  public URLEmailRemover() {
    this(true, true, "");
  }

  /** Auto-generated for codecheck compliance. */
  public URLEmailRemover(boolean isRemoveUrls, boolean isRemoveEmails, String replacement) {
    this.isRemoveUrls = isRemoveUrls;
    this.isRemoveEmails = isRemoveEmails;
    this.replacement = replacement != null ? replacement : "";
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public String process(String text) {
    if (text == null) {
      return null;
    }
    String result = text;
    if (isRemoveUrls) {
      result =
          result
              .replaceAll("https?://\\S+|www\\.\\S+", replacement)
              .replaceAll(
                  "(?<![\\w.%+-]@)(?:https?://|www\\.)?\\b\\S+?\\.(?:com|net|org|cn)(?:[/?#]\\S*)?\\b",
                  replacement);
    }
    if (isRemoveEmails) {
      result = result.replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", replacement);
    }
    return result;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isRemoveUrls() {
    return isRemoveUrls;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isRemoveEmails() {
    return isRemoveEmails;
  }

  /** Auto-generated for codecheck compliance. */
  public String getReplacement() {
    return replacement;
  }
}
