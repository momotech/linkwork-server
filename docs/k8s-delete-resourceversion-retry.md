# Sandbox deletion after resourceVersion conflicts

## Race and safety boundary

Controllers can update a Pod between inventory GET and DELETE, invalidating the
resourceVersion precondition without any concurrent build. The current helper
records 409 as a permanent cleanup error even if a reconciler later removes it.

Keep UID and resourceVersion preconditions on every DELETE. Pin the original UID,
name and namespace. Each call supplies its exact typed getter and lifecycle
validator. Managed resources must still match managed/service/sandbox labels,
generation and fence token. PodGroup deletion retains expectedPodGroupUid.
Legacy deletion must reject newly managed resources and changes to its original
ownership labels/references. Scale-down retains all existing admission checks.

## State machine

At most three DELETE attempts, without sleeps. Success or DELETE 404 completes
the request. On 409, GET exactly the same resource: absence/404 completes;
changed UID, address or lifecycle identity fails closed before another DELETE.
Only validated metadata supplies the next resourceVersion. If the resource still
exists after the third conflict, fail with resource path and attempt count.
Non-404/409 DELETE errors and non-404 GET errors propagate immediately.
Missing UID/resourceVersion fails closed. Final destroy generation inventory and
scale-down Gone checks remain unchanged; a prior delete error remains a failure.

## Regression plan

Use JUnit with a local HTTP fake API server and real Fabric8 client; invoke public orchestrator
destroySandbox and scaleDown, not a disconnected retry utility. Assert raw DELETE
options, exact call counts and typed refresh GETs. Cover refreshed RV success,
missing resource, replacement UID, changed generation/fence/service/sandbox,
retry exhaustion, 404, 403/500, all four resource kinds, legacy ownership changes,
and final generation verification including verification failure.

## Release

Publish the reactor as 1.0.3-SNAPSHOT after JDK 21 focused and full tests. Upgrade
the General Agent consumer in a separate master-based worktree and cherry-pick
only the intended consumer change to dev. Deploy only dev and verify the consumed
artifact and sandbox cleanup behavior. Production rollout is outside this change.

## Validation (2026-09-14)

JDK 21.0.1 and Maven 3.8.8. The first real HTTP regression failed on the
master implementation with the exact DELETE 409 precondition message. With the
fix, the focused reactor passed 31 tests and `mvn clean test` passed 40 tests
(0 failures/errors/skips). The 13 deletion regression methods exercise multiple
resource/identity variants. `git diff --check` passed.

Commands (JAVA_HOME points to jdk-21.jdk/Contents/Home):

```sh
mvn -pl linkwork-k8s-starter -am test
mvn clean test
```
