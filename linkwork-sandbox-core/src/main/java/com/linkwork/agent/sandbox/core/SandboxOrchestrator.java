package com.linkwork.agent.sandbox.core;

import com.linkwork.agent.sandbox.core.model.SandboxDestroyRequest;
import com.linkwork.agent.sandbox.core.model.SandboxQuery;
import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxScaleDownRequest;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.SandboxStatus;
import com.linkwork.agent.sandbox.core.model.SandboxPreview;

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

    default SandboxResult destroySandbox(SandboxDestroyRequest request) {
        if (request == null) {
            return SandboxResult.failed(null, "INVALID_DESTROY_REQUEST", "destroy request is required");
        }
        return destroySandbox(request.getSandboxId(), request.getNamespace());
    }

    SandboxStatus querySandbox(String sandboxId, String namespace);

    default SandboxStatus querySandbox(SandboxQuery query) {
        if (query == null) {
            SandboxStatus status = new SandboxStatus();
            status.setMessage("query is required");
            return status;
        }
        return querySandbox(query.getSandboxId(), query.getNamespace());
    }

    SandboxScaleResult scaleDown(String sandboxId, String podName, String namespace);

    default SandboxScaleResult scaleDown(SandboxScaleDownRequest request) {
        if (request == null) {
            return SandboxScaleResult.failed(null, "scale-down request is required");
        }
        return scaleDown(request.getSandboxId(), request.getPodName(), request.getNamespace());
    }

    SandboxScaleResult scaleUp(String sandboxId, int targetPodCount, String namespace, SandboxSpec templateSpec);

    List<String> listRunningPods(String sandboxId, String namespace);
}
