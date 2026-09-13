package com.linkwork.agent.sandbox.provider.k8s;

import com.linkwork.agent.sandbox.core.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class K8sVolcanoDeletionRetryTest {
    private static final String NS = "test-sandbox";
    private static final String ID = "742";
    private static final String NAME = "svc-742-0";

    @Test
    public void destroyRetriesConflictWithFreshResourceVersion() throws Exception {
        try (Fixture f = new Fixture("Pod")) {
            SandboxResult result = f.destroy();
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(2, f.deletes.size());
            assertEquals(List.of("100", "101"), f.deletes.stream()
                .map(value -> value.getPreconditions().getResourceVersion()).toList());
            assertTrue(f.deletes.stream().allMatch(value -> "uid-1".equals(value.getPreconditions().getUid())));
            assertEquals(1, f.refreshGets);
            assertTrue("destroy must verify final inventory", f.verificationLists > 0);
        }
    }

    @Test
    public void conflictThenMissingIsSuccessfulWithoutAnotherDelete() throws Exception {
        try (Fixture f = new Fixture("Pod")) {
            f.refreshed = null;
            assertTrue(f.destroy().isSuccess());
            assertEquals(1, f.deletes.size());
            assertEquals(1, f.refreshGets);
        }
    }

    @Test
    public void replacementUidFailsClosedForEveryResourceKind() throws Exception {
        for (String kind : List.of("Pod", "PodGroup", "ConfigMap", "Secret")) {
            try (Fixture f = new Fixture(kind)) {
                f.refreshed.getMetadata().setUid("replacement-uid");
                SandboxResult result = f.destroy();
                assertFalse(kind, result.isSuccess());
                assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("UID changed"));
                assertEquals(kind, 1, f.deletes.size());
                assertEquals("uid-1", f.deletes.getFirst().getPreconditions().getUid());
            }
        }
    }

    @Test
    public void lifecycleChangesFailClosedForEveryResourceKind() throws Exception {
        for (String kind : List.of("Pod", "PodGroup", "ConfigMap", "Secret")) {
            for (String key : List.of(SandboxLifecycleMetadata.GENERATION, SandboxLifecycleMetadata.FENCE_TOKEN,
                    SandboxLifecycleMetadata.SANDBOX_ID, SandboxLifecycleMetadata.SERVICE_ID, SandboxLifecycleMetadata.MANAGED)) {
                try (Fixture f = new Fixture(kind)) {
                    f.refreshed.getMetadata().getLabels().put(key, "changed");
                    SandboxResult result = f.destroy();
                    assertFalse(kind + ": " + key, result.isSuccess());
                    assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("lifecycle metadata mismatch"));
                    assertEquals(kind + ": " + key, 1, f.deletes.size());
                }
            }
        }
    }

    @Test
    public void managedDiscoveryIdentityChangesFailClosed() throws Exception {
        for (String key : List.of("sandbox-id", "user-service-id", "managed-by")) {
            try (Fixture f = new Fixture("Pod")) {
                f.refreshed.getMetadata().getLabels().put(key, "another-sandbox");
                assertFalse(key, f.destroy().isSuccess());
                assertEquals(key, 1, f.deletes.size());
            }
        }
    }

    @Test
    public void conflictRetryIsBoundedEvenWhenBackgroundCleanupLaterSucceeds() throws Exception {
        try (Fixture f = new Fixture("Pod")) {
            f.conflicts = 10;
            SandboxResult result = f.destroy();
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("after 3 DELETE attempts"));
            assertTrue(result.getErrorMessage().contains("pods/" + NAME));
            assertEquals(3, f.deletes.size());
            assertEquals(3, f.refreshGets);
            assertTrue(f.verificationLists > 0);
        }
    }

    @Test
    public void delete404RemainsIdempotent() throws Exception {
        try (Fixture f = new Fixture("Pod")) {
            f.deleteError = 404;
            assertTrue(f.destroy().isSuccess());
            assertEquals(1, f.deletes.size());
            assertEquals(0, f.refreshGets);
        }
    }

    @Test
    public void otherDeleteErrorsAreNotRetriedOrSwallowed() throws Exception {
        for (int code : List.of(403, 500)) {
            try (Fixture f = new Fixture("Pod")) {
                f.deleteError = code;
                SandboxResult result = f.destroy();
                assertFalse(result.isSuccess());
                assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("HTTP " + code));
                assertEquals(1, f.deletes.size());
                assertEquals(0, f.refreshGets);
            }
        }
    }

    @Test
    public void refreshFailuresDoNotPermitAnotherDelete() throws Exception {
        for (int code : List.of(403, 409, 500)) {
            try (Fixture f = new Fixture("Pod")) {
                f.refreshError = code;
                assertFalse(f.destroy().isSuccess());
                assertEquals(1, f.deletes.size());
                assertEquals(1, f.refreshGets);
            }
        }
    }

    @Test
    public void allKindsUseTheirExactGetterAndPreserveDeleteOptions() throws Exception {
        for (String kind : List.of("PodGroup", "ConfigMap", "Secret")) {
            try (Fixture f = new Fixture(kind)) {
                SandboxResult result = f.destroy();
                assertTrue(kind + ": " + result.getErrorMessage(), result.isSuccess());
                assertEquals(2, f.deletes.size());
                assertEquals(1, f.refreshGets);
                assertEquals("101", f.deletes.get(1).getPreconditions().getResourceVersion());
                for (DeleteOptions options : f.deletes) {
                    assertEquals("uid-1", options.getPreconditions().getUid());
                    assertEquals("PodGroup".equals(kind) ? "Foreground" : "Background", options.getPropagationPolicy());
                    assertEquals(Long.valueOf("PodGroup".equals(kind) ? 2L : 0L), options.getGracePeriodSeconds());
                }
            }
        }
    }

    @Test
    public void successfulDeleteCannotBypassFinalGenerationVerification() throws Exception {
        try (Fixture f = new Fixture("Pod")) {
            f.verificationError = 500;
            SandboxResult result = f.destroy();
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage(), result.getErrorMessage().contains("verification failed"));
            assertEquals(2, f.deletes.size());
            assertTrue(f.verificationLists > 0);
        }
    }

    @Test
    public void scaleDownRetriesAndRejectsReplacementUidAndLifecycleChanges() throws Exception {
        for (String change : List.of("none", "uid", "generation", "fence")) {
            try (Fixture f = new Fixture("Pod")) {
                f.scale = true;
                if ("uid".equals(change)) f.refreshed.getMetadata().setUid("replacement");
                if ("generation".equals(change)) f.refreshed.getMetadata().getLabels().put(SandboxLifecycleMetadata.GENERATION, "g2");
                if ("fence".equals(change)) f.refreshed.getMetadata().getLabels().put(SandboxLifecycleMetadata.FENCE_TOKEN, "8");
                SandboxScaleDownRequest request = new SandboxScaleDownRequest();
                request.setSandboxId(ID);
                request.setNamespace(NS);
                request.setPodName(NAME);
                request.setExpectedGeneration("g1");
                request.setExpectedFenceToken(7L);
                request.setExpectedPodGroupUid("uid-1");
                request.setTargetPodCount(1);
                SandboxScaleResult result = f.orchestrator.scaleDown(request);
                assertEquals(result.getErrorMessage(), "none".equals(change), result.isSuccess());
                assertEquals(change, "none".equals(change) ? 2 : 1, f.deletes.size());
                if (result.isSuccess()) {
                    assertEquals("101", f.deletes.get(1).getPreconditions().getResourceVersion());
                    assertEquals(Long.valueOf(0L), f.deletes.get(1).getGracePeriodSeconds());
                }
            }
        }
    }

    @Test
    public void legacyRetryRejectsNewLifecycleAndChangedOwnership() throws Exception {
        for (String change : List.of("none", "managed", "sandbox", "owner")) {
            try (Fixture f = new Fixture("Pod")) {
                f.original.getMetadata().getLabels().keySet().removeIf(key -> key.startsWith("platform.momo.com/"));
                f.refreshed.setMetadata(new ObjectMetaBuilder(f.original.getMetadata()).withResourceVersion("101").build());
                if ("managed".equals(change)) f.refreshed.getMetadata().getLabels().put(SandboxLifecycleMetadata.GENERATION, "g2");
                if ("sandbox".equals(change)) f.refreshed.getMetadata().getLabels().put("sandbox-id", "another");
                if ("owner".equals(change)) f.refreshed.getMetadata().setOwnerReferences(List.of(new OwnerReferenceBuilder().withUid("new-owner").build()));
                SandboxDestroyRequest request = request();
                request.setAllowLegacy(true);
                SandboxResult result = f.orchestrator.destroySandbox(request);
                assertEquals(result.getErrorMessage(), "none".equals(change), result.isSuccess());
                assertEquals("none".equals(change) ? 2 : 1, f.deletes.size());
            }
        }
    }

    @Test
    public void missingVersionAndChangedResourceAddressFailClosed() throws Exception {
        for (String change : List.of("version", "name", "namespace", "metadata", "owner")) {
            try (Fixture f = new Fixture("Pod")) {
                if ("version".equals(change)) f.refreshed.getMetadata().setResourceVersion(null);
                if ("name".equals(change)) f.refreshed.getMetadata().setName("another");
                if ("namespace".equals(change)) f.refreshed.getMetadata().setNamespace("another");
                if ("metadata".equals(change)) f.refreshed.setMetadata(null);
                if ("owner".equals(change)) f.refreshed.getMetadata().setOwnerReferences(List.of(new OwnerReferenceBuilder().withUid("new-owner").build()));
                assertFalse(change, f.destroy().isSuccess());
                assertEquals(change, 1, f.deletes.size());
            }
        }
    }

    private static ObjectMeta metadata(String name, String uid, String rv) {
        return new ObjectMetaBuilder().withName(name).withNamespace(NS).withUid(uid).withResourceVersion(rv)
            .withLabels(new HashMap<>(Map.of("sandbox-id", ID, "managed-by", "linkwork-k8s-starter",
                SandboxLifecycleMetadata.MANAGED, "true", SandboxLifecycleMetadata.SERVICE_ID, ID,
                SandboxLifecycleMetadata.SANDBOX_ID, ID, SandboxLifecycleMetadata.GENERATION, "g1",
                SandboxLifecycleMetadata.FENCE_TOKEN, "7"))).build();
    }

    private static GenericKubernetesResource resource(String kind, String name, String uid, String rv) {
        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setApiVersion("PodGroup".equals(kind) ? "scheduling.volcano.sh/v1beta1" : "v1");
        resource.setKind(kind);
        resource.setMetadata(metadata(name, uid, rv));
        if ("Pod".equals(kind)) resource.setAdditionalProperty("status", Map.of("phase", "Running"));
        return resource;
    }

    private static SandboxDestroyRequest request() {
        SandboxDestroyRequest request = new SandboxDestroyRequest();
        request.setSandboxId(ID);
        request.setNamespace(NS);
        request.setExpectedGeneration("g1");
        request.setExpectedFenceToken(7L);
        request.setExpectedPodGroupUid("uid-1");
        request.setGracePeriodSeconds(2L);
        return request;
    }

    /** Real Fabric8 typed GET/raw DELETE over HTTP; no production methods are mocked. */
    private static class Fixture implements AutoCloseable {
        final HttpServer server;
        final KubernetesClient client;
        final K8sVolcanoOrchestratorImpl orchestrator;
        final GenericKubernetesResource original;
        GenericKubernetesResource refreshed;
        final String collection;
        final String path;
        final List<DeleteOptions> deletes = new ArrayList<>();
        int conflicts = 1;
        int deleteError;
        int refreshError;
        int verificationError;
        int refreshGets;
        int verificationLists;
        boolean pendingRefresh;
        boolean scale;

        Fixture(String kind) throws IOException {
            String name = "PodGroup".equals(kind) ? SandboxNaming.podGroupName(ID) : NAME;
            collection = "PodGroup".equals(kind)
                ? "/apis/scheduling.volcano.sh/v1beta1/namespaces/" + NS + "/podgroups"
                : "/api/v1/namespaces/" + NS + "/" + kind.toLowerCase(Locale.ROOT) + "s";
            path = collection + "/" + name;
            original = resource(kind, name, "uid-1", "100");
            refreshed = resource(kind, name, "uid-1", "101");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
            client = new KubernetesClientBuilder().withConfig(new ConfigBuilder()
                .withMasterUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .withRequestRetryBackoffLimit(0).withHttp2Disable(true).build()).build();
            orchestrator = new K8sVolcanoOrchestratorImpl(client, new PodGroupSpecGenerator(),
                new PodSpecGenerator(), new K8sSandboxProperties());
        }

        SandboxResult destroy() { return orchestrator.destroySandbox(request()); }

        private void handle(HttpExchange exchange) throws IOException {
            String requested = exchange.getRequestURI().getPath();
            if (requested.equals("/apis/scheduling.volcano.sh/v1beta1")) {
                respond(exchange, 200, Map.of("apiVersion", "v1", "kind", "APIResourceList",
                    "groupVersion", "scheduling.volcano.sh/v1beta1", "resources", List.of(Map.of(
                        "name", "podgroups", "kind", "PodGroup", "namespaced", true,
                        "verbs", List.of("get", "list", "delete")))));
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                if (!path.equals(requested)) { respond(exchange, 500, status(500)); return; }
                deletes.add(Serialization.unmarshal(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), DeleteOptions.class));
                int code = deleteError != 0 ? deleteError : deletes.size() <= conflicts ? 409 : 200;
                pendingRefresh = code == 409;
                respond(exchange, code, status(code));
            } else if (path.equals(requested)) {
                if (pendingRefresh) {
                    pendingRefresh = false;
                    refreshGets++;
                    if (refreshError != 0) respond(exchange, refreshError, status(refreshError));
                    else respond(exchange, refreshed == null ? 404 : 200,
                        refreshed == null ? status(404) : refreshed);
                } else {
                    // Simulate asynchronous cleanup after DELETE; it must never mask a recorded error.
                    respond(exchange, deletes.isEmpty() ? 200 : 404,
                        deletes.isEmpty() ? original : status(404));
                }
            } else if (requested.endsWith("/" + SandboxNaming.podGroupName(ID))) {
                respond(exchange, scale ? 200 : 404, scale
                    ? resource("PodGroup", SandboxNaming.podGroupName(ID), "uid-1", "1") : status(404));
            } else {
                if (!deletes.isEmpty()) {
                    verificationLists++;
                    if (verificationError != 0) { respond(exchange, verificationError, status(verificationError)); return; }
                }
                List<GenericKubernetesResource> items = new ArrayList<>();
                if (deletes.isEmpty() && requested.equals(collection)) items.add(original);
                if (scale && requested.endsWith("/pods")) items.add(resource("Pod", "svc-742-1", "other-uid", "1"));
                String kind = requested.endsWith("/pods") ? "PodList" : requested.endsWith("/secrets") ? "SecretList" : "ConfigMapList";
                respond(exchange, 200, Map.of("apiVersion", "v1", "kind", kind, "items", items));
            }
        }

        private static Map<String, Object> status(int code) {
            return Map.of("apiVersion", "v1", "kind", "Status", "code", code,
                "status", code == 200 ? "Success" : "Failure", "message", code == 409
                    ? "The ResourceVersion in the precondition does not match the ResourceVersion in record." : "HTTP " + code);
        }

        private static void respond(HttpExchange exchange, int code, Object object) throws IOException {
            byte[] body = Serialization.asJson(object).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        }

        @Override public void close() { client.close(); server.stop(0); }
    }
}
