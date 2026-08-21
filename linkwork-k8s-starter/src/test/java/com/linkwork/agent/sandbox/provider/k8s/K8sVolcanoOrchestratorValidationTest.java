package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxLifecycleMetadata;
import com.linkwork.agent.sandbox.core.model.SandboxScaleDownRequest;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void legacyDestroyOnlyAcceptsResourcesWithoutLifecycleMetadata() {
        assertFalse(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(
                    "app", "linkwork-sandbox",
                    "sandbox-id", "svc-1",
                    "service-id", "svc-1"
                ))
                .build()
        ));

        assertTrue(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(SandboxLifecycleMetadata.GENERATION, "generation-1"))
                .build()
        ));
        assertTrue(K8sVolcanoOrchestratorImpl.isLifecycleManagedResource(
            new ObjectMetaBuilder()
                .withLabels(Map.of(SandboxLifecycleMetadata.MANAGED, "true"))
                .build()
        ));
    }

    @Test
    public void managedScaleOperationsRequireThePodGroupUid() {
        K8sVolcanoOrchestratorImpl orchestrator = new K8sVolcanoOrchestratorImpl(
            null,
            new PodGroupSpecGenerator(),
            new PodSpecGenerator(),
            new K8sSandboxProperties()
        );
        SandboxScaleDownRequest down = new SandboxScaleDownRequest();
        down.setSandboxId("svc-1");
        down.setPodName("svc-svc-1-0");
        down.setExpectedGeneration("generation-1");
        down.setExpectedFenceToken(1L);
        down.setTargetPodCount(0);

        SandboxScaleResult downResult = orchestrator.scaleDown(down);

        SandboxSpec up = new SandboxSpec();
        up.setSandboxId("svc-1");
        up.setLifecycleGeneration("generation-1");
        up.setFenceToken(1L);
        SandboxScaleResult upResult = orchestrator.scaleUp("svc-1", 2, "ai-worker", up);

        assertTrue(downResult.getErrorMessage().contains("expectedPodGroupUid"));
        assertTrue(upResult.getErrorMessage().contains("expectedPodGroupUid"));
    }

    @Test
    public void lifecycleIdentityRequiresMatchingServiceAndSandboxValues() {
        var metadata = new ObjectMetaBuilder()
            .withLabels(Map.of(
                SandboxLifecycleMetadata.MANAGED, "true",
                SandboxLifecycleMetadata.SERVICE_ID, "svc-1",
                SandboxLifecycleMetadata.SANDBOX_ID, "svc-1",
                SandboxLifecycleMetadata.GENERATION, "generation-1",
                SandboxLifecycleMetadata.FENCE_TOKEN, "1"
            ))
            .build();

        assertTrue(K8sVolcanoOrchestratorImpl.matchesLifecycleIdentity(
            metadata, "svc-1", "generation-1", 1L
        ));
        assertFalse(K8sVolcanoOrchestratorImpl.matchesLifecycleIdentity(
            metadata, "svc-2", "generation-1", 1L
        ));
    }

    @Test
    public void managedInventoryRejectsAnySameSandboxResourceOutsideTheExpectedGeneration() {
        Pod expected = new PodBuilder().withNewMetadata().withName("svc-1-0").withUid("uid-1")
            .endMetadata().build();
        Pod legacyOrDifferentGeneration = new PodBuilder().withNewMetadata()
            .withName("svc-1-1").withUid("uid-2")
            .endMetadata().build();

        assertFalse(K8sVolcanoOrchestratorImpl.hasConflictingManagedResources(
            List.of(expected), List.of(expected)
        ));
        assertTrue(K8sVolcanoOrchestratorImpl.hasConflictingManagedResources(
            List.of(expected, legacyOrDifferentGeneration), List.of(expected)
        ));
    }
}
