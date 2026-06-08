/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.drivers;

import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedBrowserDriverTest {

    @Test
    void testStartReusesExistingEndpointWithoutSpawning() {
        class TestDriver extends ManagedBrowserDriver {
            int spawnCalls;

            TestDriver(BrowserProfile profile) {
                super(profile);
            }

            @Override
            protected boolean isEndpointReadyInternal() {
                return true;
            }

            @Override
            protected Process spawnProcess(List<String> args) {
                spawnCalls += 1;
                return new FakeProcess(true, 0);
            }
        }

        TestDriver driver = new TestDriver(profile());
        String endpoint = driver.start();
        assertEquals("http://127.0.0.1:9333", endpoint);
        assertFalse(driver.ownsProcess());
        assertEquals(0, driver.spawnCalls);
    }

    @Test
    void testStopDoesNotTerminateExternalBrowser() {
        BrowserProfile profile = profile();
        ManagedBrowserDriver driver = new ManagedBrowserDriver(profile);
        FakeProcess process = new FakeProcess(true, 0);
        setProcess(driver, process, false);

        driver.stop();

        assertFalse(process.destroyCalled);
        assertFalse(process.destroyForciblyCalled);
    }

    @Test
    void testBuildArgsContainsCdpFlags() {
        BrowserProfile profile = profile();
        ManagedBrowserDriver driver = new ManagedBrowserDriver(profile);
        List<String> args = driver.buildArgs("chrome");
        assertTrue(args.contains("--remote-debugging-address=127.0.0.1"));
        assertTrue(args.contains("--remote-debugging-port=9333"));
    }

    private static BrowserProfile profile() {
        return new BrowserProfile(
                "test-profile",
                "managed",
                "http://127.0.0.1:9333",
                "",
                ".",
                9333,
                "127.0.0.1",
                List.of()
        );
    }

    private static void setProcess(ManagedBrowserDriver driver, Process process, boolean ownsProcess) {
        try {
            java.lang.reflect.Field processField = ManagedBrowserDriver.class.getDeclaredField("process");
            processField.setAccessible(true);
            processField.set(driver, process);
            java.lang.reflect.Field ownsField = ManagedBrowserDriver.class.getDeclaredField("ownsProcess");
            ownsField.setAccessible(true);
            ownsField.setBoolean(driver, ownsProcess);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class FakeProcess extends Process {

        private boolean alive;
        private final int exitCode;
        private boolean destroyCalled;
        private boolean destroyForciblyCalled;

        private FakeProcess(boolean alive, int exitCode) {
            this.alive = alive;
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
