package com.linkwork.agent.sandbox.core;

import com.linkwork.agent.sandbox.core.model.SandboxDestroyRequest;
import com.linkwork.agent.sandbox.core.model.SandboxPreview;
import com.linkwork.agent.sandbox.core.model.SandboxQuery;
import com.linkwork.agent.sandbox.core.model.SandboxResult;
import com.linkwork.agent.sandbox.core.model.SandboxScaleDownRequest;
import com.linkwork.agent.sandbox.core.model.SandboxScaleResult;
import com.linkwork.agent.sandbox.core.model.SandboxSpec;
import com.linkwork.agent.sandbox.core.model.SandboxStatus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SandboxOrchestratorCompatibilityTest {

    @Test
    public void generationAwareDefaultsDelegateToLegacyImplementation() {
        LegacyOrchestrator orchestrator = new LegacyOrchestrator();

        SandboxDestroyRequest destroy = new SandboxDestroyRequest();
        destroy.setSandboxId("svc-1");
        destroy.setNamespace("robot");
        assertTrue(orchestrator.destroySandbox(destroy).isSuccess());
        assertEquals("svc-1@robot", orchestrator.lastCall);

        SandboxQuery query = SandboxQuery.of("svc-2", "robot");
        assertEquals("svc-2", orchestrator.querySandbox(query).getSandboxId());

        SandboxScaleDownRequest scaleDown = new SandboxScaleDownRequest();
        scaleDown.setSandboxId("svc-3");
        scaleDown.setPodName("svc-3-0");
        scaleDown.setNamespace("robot");
        assertTrue(orchestrator.scaleDown(scaleDown).isSuccess());
    }

    private static final class LegacyOrchestrator implements SandboxOrchestrator {

        private String lastCall;

        @Override
        public SandboxResult createSandbox(SandboxSpec spec) {
            return SandboxResult.success(spec.getSandboxId(), null, List.of(), null);
        }

        @Override
        public SandboxPreview previewSandbox(SandboxSpec spec) {
            return new SandboxPreview();
        }

        @Override
        public SandboxResult stopSandbox(String sandboxId, String namespace, boolean graceful) {
            return destroySandbox(sandboxId, namespace);
        }

        @Override
        public SandboxResult destroySandbox(String sandboxId, String namespace) {
            lastCall = sandboxId + "@" + namespace;
            return SandboxResult.success(sandboxId, null, List.of(), null);
        }

        @Override
        public SandboxStatus querySandbox(String sandboxId, String namespace) {
            SandboxStatus status = new SandboxStatus();
            status.setSandboxId(sandboxId);
            status.setNamespace(namespace);
            return status;
        }

        @Override
        public SandboxScaleResult scaleDown(String sandboxId, String podName, String namespace) {
            return SandboxScaleResult.success(
                sandboxId, "SCALE_DOWN", 1, 0, 0, List.of(), List.of(), List.of(podName)
            );
        }

        @Override
        public SandboxScaleResult scaleUp(String sandboxId,
                                          int targetPodCount,
                                          String namespace,
                                          SandboxSpec templateSpec) {
            return SandboxScaleResult.success(
                sandboxId, "SCALE_UP", 0, targetPodCount, targetPodCount, List.of(), List.of(), List.of()
            );
        }

        @Override
        public List<String> listRunningPods(String sandboxId, String namespace) {
            return List.of();
        }
    }
}
