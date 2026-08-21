package com.linkwork.agent.sandbox.core.model;

/**
 * Canonical metadata keys shared by the platform and sandbox providers.
 */
public final class SandboxLifecycleMetadata {

    public static final String MANAGED = "platform.momo.com/managed";
    public static final String SERVICE_ID = "platform.momo.com/service-id";
    public static final String SANDBOX_ID = "platform.momo.com/sandbox-id";
    public static final String GENERATION = "platform.momo.com/lifecycle-generation";
    public static final String FENCE_TOKEN = "platform.momo.com/fence-token";
    public static final String CREATED_AT = "platform.momo.com/created-at";

    private SandboxLifecycleMetadata() {
    }
}
