package com.linkwork.agent.sandbox.provider.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class K8sVolcanoScaleDownPolicyTest {

    @Test
    public void terminalMemberCanBePrunedWhenAnotherActiveMemberExists() {
        assertFalse(K8sVolcanoOrchestratorImpl.requiresFullDestroy(1, false, 1));
        assertFalse(K8sVolcanoOrchestratorImpl.requiresRepairBeforeScaleDown(1, false));
    }

    @Test
    public void terminalOnlySandboxRequiresFullDestroy() {
        assertTrue(K8sVolcanoOrchestratorImpl.requiresFullDestroy(0, false, 0));
    }

    @Test
    public void lastActiveMemberRequiresDestroyOrRepair() {
        assertTrue(K8sVolcanoOrchestratorImpl.requiresFullDestroy(1, true, 0));
        assertTrue(K8sVolcanoOrchestratorImpl.requiresRepairBeforeScaleDown(1, true));
    }

    @Test
    public void scaleVerificationKeepsTerminalMembersOutsideTheActiveSet() {
        Pod running = pod("svc-1-0", "Running");
        Pod terminal = pod("svc-1-1", "Failed");

        List<Pod> compared = K8sVolcanoOrchestratorImpl.activePodsForScaleVerification(
            List.of(running, terminal)
        );

        assertEquals(List.of(running), compared);
        assertTrue(K8sVolcanoOrchestratorImpl.isScaleUpPodGroupPhaseHealthy("Running"));
        assertFalse(K8sVolcanoOrchestratorImpl.isScaleUpPodGroupPhaseHealthy(null));
        assertFalse(K8sVolcanoOrchestratorImpl.isScaleUpPodGroupPhaseHealthy("Completed"));
    }

    @Test
    public void managedScaleDownTargetMustMatchTheObservedActiveInventory() {
        assertTrue(K8sVolcanoOrchestratorImpl.isScaleDownTargetConsistent(3, true, 2));
        assertTrue(K8sVolcanoOrchestratorImpl.isScaleDownTargetConsistent(3, false, 3));
        assertFalse(K8sVolcanoOrchestratorImpl.isScaleDownTargetConsistent(3, true, 3));
        assertFalse(K8sVolcanoOrchestratorImpl.isScaleDownTargetConsistent(3, false, 2));
    }

    private Pod pod(String name, String phase) {
        return new PodBuilder()
            .withNewMetadata()
                .withName(name)
            .endMetadata()
            .withNewStatus()
                .withPhase(phase)
            .endStatus()
            .build();
    }
}
