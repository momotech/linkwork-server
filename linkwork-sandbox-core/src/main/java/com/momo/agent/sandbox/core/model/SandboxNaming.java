package com.momo.agent.sandbox.core.model;

import java.util.Locale;

/**
 * Shared naming strategy for sandbox-related K8s resources.
 */
public final class SandboxNaming {

    private SandboxNaming() {
    }

    public static String podGroupName(String sandboxId) {
        return "svc-" + normalizeName(sandboxId) + "-pg";
    }

    public static String podName(String sandboxId, int podIndex) {
        return "svc-" + normalizeName(sandboxId) + "-" + podIndex;
    }

    public static String normalizeName(String raw) {
        String safe = raw == null ? "unknown" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        safe = safe.replaceAll("^-+", "").replaceAll("-+$", "");
        if (safe.isBlank()) {
            safe = "unknown";
        }
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return safe;
    }
}
