/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.stream.Collectors;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * Runs an existing JUnit test class from a bridge test and asserts it passes.
 */
public final class JUnitBridgeAssertions {

    private JUnitBridgeAssertions() {
    }

    public static void assertDelegatedClassPasses(Class<?> testClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        var summary = listener.getSummary();
        assertTrue(
                summary.getTestsFoundCount() > 0,
                "Delegated test class did not expose runnable tests: " + testClass.getName());
        assertEquals(
                0,
                summary.getFailures().size(),
                summary.getFailures().stream()
                        .map(failure -> failure.getTestIdentifier().getDisplayName() + ": "
                                + failure.getException().getMessage())
                        .collect(Collectors.joining(System.lineSeparator())));
    }
}
