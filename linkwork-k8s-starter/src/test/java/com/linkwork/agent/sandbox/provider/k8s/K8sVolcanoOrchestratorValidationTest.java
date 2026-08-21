package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class K8sVolcanoOrchestratorValidationTest {

    @Test
    public void createRejectsMissingLifecycleIdentityBeforeCallingKubernetes() {
        K8sVolcanoOrchestratorImpl orchestrator = new K8sVolcanoOrchestratorImpl(
            null,
            new PodGroupSpecGenerator(),
            new PodSpecGenerator(),
            new K8sSandboxProperties()
        );
        SandboxSpec spec = new SandboxSpec();
        spec.setSandboxId("svc-1");
        spec.setPodCount(1);

        SandboxResult result = orchestrator.createSandbox(spec);

        assertEquals("LIFECYCLE_PRECONDITION_REQUIRED", result.getErrorCode());
    }
}
