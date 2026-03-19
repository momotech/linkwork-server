package com.linkwork.agent.sandbox.core;

import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxStatus;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.SandboxPreview;

import java.time.Instant;
import java.util.List;

/**
 * Fallback orchestrator when no sandbox provider is enabled.
 */
public class NoopSandboxOrchestrator implements SandboxOrchestrator {

    @Override
    public SandboxResult createSandbox(SandboxSpec spec) {
        String sandboxId = spec == null ? null : spec.getSandboxId();
        return SandboxResult.failed(
            sandboxId,
            "SANDBOX_PROVIDER_DISABLED",
            "No sandbox provider is enabled. Set linkwork.agent.sandbox.provider."
        );
    }

    @Override
    public SandboxResult destroySandbox(String sandboxId, String namespace) {
        return SandboxResult.failed(
            sandboxId,
            "SANDBOX_PROVIDER_DISABLED",
            "No sandbox provider is enabled. Nothing was destroyed."
        );
    }

    @Override
    public SandboxPreview previewSandbox(SandboxSpec spec) {
        SandboxPreview preview = new SandboxPreview();
        preview.setSandboxId(spec == null ? null : spec.getSandboxId());
        return preview;
    }

    @Override
    public SandboxResult stopSandbox(String sandboxId, String namespace, boolean graceful) {
        return SandboxResult.failed(
            sandboxId,
            "SANDBOX_PROVIDER_DISABLED",
            "No sandbox provider is enabled. Nothing was stopped."
        );
    }

    @Override
    public SandboxStatus querySandbox(String sandboxId, String namespace) {
        SandboxStatus status = new SandboxStatus();
        status.setSandboxId(sandboxId);
        status.setNamespace(namespace);
        status.setObservedAt(Instant.now());
        status.setMessage("No sandbox provider is enabled.");
        return status;
    }

    @Override
    public SandboxScaleResult scaleDown(String sandboxId, String podName, String namespace) {
        return SandboxScaleResult.failed(sandboxId, "No sandbox provider is enabled.");
    }

    @Override
    public SandboxScaleResult scaleUp(String sandboxId, int targetPodCount, String namespace, SandboxSpec templateSpec) {
        return SandboxScaleResult.failed(sandboxId, "No sandbox provider is enabled.");
    }

    @Override
    public List<String> listRunningPods(String sandboxId, String namespace) {
        return List.of();
    }
}
