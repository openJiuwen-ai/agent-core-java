package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SslUtils 测试类
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
class SslUtilsTest {

    @Test
    void testCreateStrictSslContext() {
        // Act
        SSLContext context = SslUtils.createStrictSslContext(null);
        
        // Assert
        assertNotNull(context);
        assertEquals("TLS", context.getProtocol());
    }

    @Test
    void testGetSslConfigWithHttps() {
        // Arrange
        String verifySwitch = "TEST_SSL_VERIFY";
        String certEnv = "TEST_SSL_CERT";
        List<String> triggerValues = Arrays.asList("false", "off", "0");
        
        // Set environment variable to disable SSL verification
        System.setProperty(verifySwitch, "false");
        
        // Act
        SslUtils.SslConfig config = SslUtils.getSslConfig(verifySwitch, certEnv, triggerValues, true);
        
        // Assert
        assertNotNull(config);
        assertFalse(config.isSslVerify());
        
        // Cleanup
        System.clearProperty(verifySwitch);
    }

    @Test
    void testGetSslConfigWithHttp() {
        // Arrange
        String verifySwitch = "TEST_SSL_VERIFY";
        String certEnv = "TEST_SSL_CERT";
        List<String> triggerValues = Arrays.asList("false", "off", "0");
        
        // Act
        SslUtils.SslConfig config = SslUtils.getSslConfig(verifySwitch, certEnv, triggerValues, false);
        
        // Assert
        assertNotNull(config);
        assertFalse(config.isSslVerify());
    }

    @Test
    void testGetSslConfigRequiresCertWhenEnabled() {
        // Arrange
        String verifySwitch = "TEST_SSL_VERIFY_REQUIRED";
        String certEnv = "TEST_SSL_CERT_REQUIRED";
        List<String> triggerValues = Arrays.asList("false", "off", "0");
        
        // Enable SSL verification but don't set cert
        System.setProperty(verifySwitch, "true");
        System.clearProperty(certEnv);
        
        // Act & Assert
        assertThrows(
            JiuWenBaseException.class,
            () -> SslUtils.getSslConfig(verifySwitch, certEnv, triggerValues, true)
        );
        
        // Cleanup
        System.clearProperty(verifySwitch);
    }

    @Test
    void testSecureLoadCertValidFile() throws IOException {
        // Note: This test requires SAFE_CERT_DIR to be set
        // In production environment, this should be configured properly
        
        // Arrange
        Path tempCert = Files.createTempFile("test-cert", ".pem");
        try {
            String certContent = "-----BEGIN CERTIFICATE-----\n" +
                               "MIICertificateDataHere\n" +
                               "-----END CERTIFICATE-----\n";
            Files.writeString(tempCert, certContent);
            
            // Act & Assert - Should throw because SAFE_CERT_DIR not set
            assertThrows(
                JiuWenBaseException.class,
                () -> SslUtils.createStrictSslContext(tempCert.toString())
            );
        } finally {
            // Cleanup
            Files.deleteIfExists(tempCert);
        }
    }

    @Test
    void testSecureLoadCertInvalidSize() throws IOException {
        // Arrange - Create empty file
        Path tempCert = Files.createTempFile("test-cert-empty", ".pem");
        try {
            System.setProperty("SAFE_CERT_DIR", tempCert.getParent().toString());
            
            // Act & Assert
            assertThrows(
                JiuWenBaseException.class,
                () -> SslUtils.createStrictSslContext(tempCert.toString())
            );
        } finally {
            // Cleanup
            Files.deleteIfExists(tempCert);
            System.clearProperty("SAFE_CERT_DIR");
        }
    }

    @Test
    void testSecureLoadCertOutsideSafeDir() throws IOException {
        // Arrange
        Path tempCert = Files.createTempFile("test-cert-unsafe", ".pem");
        try {
            Files.writeString(tempCert, "test cert content");
            
            // Set SAFE_CERT_DIR to a different directory
            System.setProperty("SAFE_CERT_DIR", "/different/path");
            
            // Act & Assert
            assertThrows(
                JiuWenBaseException.class,
                () -> SslUtils.createStrictSslContext(tempCert.toString())
            );
        } finally {
            // Cleanup
            Files.deleteIfExists(tempCert);
            System.clearProperty("SAFE_CERT_DIR");
        }
    }
}

