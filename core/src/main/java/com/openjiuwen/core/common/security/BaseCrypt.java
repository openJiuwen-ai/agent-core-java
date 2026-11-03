/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

/**
 * Mirrors Python's {@code BaseCrypt} in
 * {@code openjiuwen/core/common/security/crypt_utils.py}.
 */
public abstract class BaseCrypt {

    public abstract String encrypt(byte[] key, String origin);

    public abstract String decrypt(byte[] key, String encryptStr);
}
