/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;

/**
 * AES-256-GCM encryption/decryption utilities for memory content.
 * 
 * @since 0.1.7
 */
public final class MemoryCrypto {
    /**
     * NONCE_LENGTH.
     * 
     * @since 0.1.7
     */
    public static final int NONCE_LENGTH = 12;

    /**
     * TAG_LENGTH.
     * 
     * @since 0.1.7
     */
    public static final int TAG_LENGTH = 16;

    /**
     * AES_KEY_LENGTH.
     * 
     * @since 0.1.7
     */
    public static final int AES_KEY_LENGTH = 32;
    private static final int BIT_LENGTH = 128; // Tag length in bits

    /**
     * MemoryCrypto.
     * 
     * @since 0.1.7
     */
    private MemoryCrypto() {
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     * 
     * @param key AES key, must be 32 bytes
     * @param plaintext text to encrypt
     * @return String array: [ciphertextHex, nonceHex, tagHex]
     * @since 0.1.7
     */
    public static String[] encrypt(byte[] key, String plaintext) {
        if (key.length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException("Wrong key length: " + key.length + ", expected " + AES_KEY_LENGTH);
        }

        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);

        try {
            byte[] plaintextBytes = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            AEADParameters params = new AEADParameters(new KeyParameter(key), BIT_LENGTH, nonce);
            GCMBlockCipher memCipher = new GCMBlockCipher(new AESEngine());
            memCipher.init(true, params);

            byte[] memOutput = new byte[memCipher.getOutputSize(plaintextBytes.length)];
            int resLen = memCipher.processBytes(plaintextBytes, 0, plaintextBytes.length, memOutput, 0);
            memCipher.doFinal(memOutput, resLen);

            // output contains ciphertext + tag
            int ciphertextLen = memOutput.length - TAG_LENGTH;
            byte[] ciphertext = new byte[ciphertextLen];
            byte[] tag = new byte[TAG_LENGTH];
            System.arraycopy(memOutput, 0, ciphertext, 0, ciphertextLen);
            System.arraycopy(memOutput, ciphertextLen, tag, 0, TAG_LENGTH);

            return new String[]{bytesToHex(ciphertext), bytesToHex(nonce), bytesToHex(tag)};
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     * 
     * @param key AES key, must be 32 bytes
     * @param ciphertext hex-encoded ciphertext
     * @param nonce hex-encoded nonce
     * @param tag hex-encoded authentication tag
     * @return decrypted plaintext
     * @since 0.1.7
     */
    public static String decrypt(byte[] key, String ciphertext, String nonce, String tag) {
        byte[] ciphertextBytes = hexToBytes(ciphertext);
        byte[] nonceBytes = hexToBytes(nonce);
        byte[] tagBytes = hexToBytes(tag);

        if (key.length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException("Wrong key length: " + key.length + ", expected " + AES_KEY_LENGTH);
        }
        if (nonceBytes.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("Wrong nonce length: " + nonceBytes.length);
        }
        if (tagBytes.length != TAG_LENGTH) {
            throw new IllegalArgumentException("Wrong tag length: " + tagBytes.length + ", expected " + TAG_LENGTH);
        }

        try {
            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            AEADParameters params = new AEADParameters(new KeyParameter(key), BIT_LENGTH, nonceBytes);
            cipher.init(false, params);

            // input = ciphertext + tag
            byte[] input = new byte[ciphertextBytes.length + tagBytes.length];
            System.arraycopy(ciphertextBytes, 0, input, 0, ciphertextBytes.length);
            System.arraycopy(tagBytes, 0, input, ciphertextBytes.length, tagBytes.length);

            byte[] output = new byte[cipher.getOutputSize(input.length)];
            int len = cipher.processBytes(input, 0, input.length, output, 0);
            cipher.doFinal(output, len);

            return new String(output, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * bytesToHex.
     * 
     * @param bytes bytes
     * @return the result
     * @since 0.1.7
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * hexToBytes.
     * 
     * @param hex hex
     * @return the result
     * @since 0.1.7
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
