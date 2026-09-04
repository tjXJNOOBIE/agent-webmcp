package org.tavall.agentwebmcp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceIdSyntaxTest {
    @Test
    void acceptsSystemdEscapedUnitNamesWithoutAllowingShellSyntax() {
        String escaped = "systemd-fsck@dev-disk-by\\x2dlabel-UEFI.service";

        assertEquals(escaped, ServiceIdSyntax.require(escaped));
        assertTrue(ServiceIdSyntax.isValid(escaped));
        assertThrows(IllegalArgumentException.class, () -> ServiceIdSyntax.require("demo.service;rm"));
        assertThrows(IllegalArgumentException.class, () -> ServiceIdSyntax.require("demo\\qservice"));
    }
}
