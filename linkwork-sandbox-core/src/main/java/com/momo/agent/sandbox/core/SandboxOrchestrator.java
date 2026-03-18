package com.momo.agent.sandbox.core;

import com.momo.agent.sandbox.core.model.SandboxResult;
import com.momo.agent.sandbox.core.model.SandboxScaleResult;
import com.momo.agent.sandbox.core.model.SandboxSpec;
import com.momo.agent.sandbox.core.model.SandboxStatus;
import com.momo.agent.sandbox.core.model.SandboxPreview;

import java.util.List;

/**
 * Sandbox orchestration SPI.
 * Implementations should support create, destroy and query semantics.
 */
public interface SandboxOrchestrator {

    SandboxResult createSandbox(SandboxSpec spec);

    SandboxPreview previewSandbox(SandboxSpec spec);

    SandboxResult stopSandbox(String sandboxId, String namespace, boolean graceful);

    SandboxResult destroySandbox(String sandboxId, String namespace);

    SandboxStatus querySandbox(String sandboxId, String namespace);

    SandboxScaleResult scaleDown(String sandboxId, String podName, String namespace);

    SandboxScaleResult scaleUp(String sandboxId, int targetPodCount, String namespace, SandboxSpec templateSpec);

    List<String> listRunningPods(String sandboxId, String namespace);
}
