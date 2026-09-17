package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.v2.verification.executor.DockerVerificationExecutor;
import com.company.forgeops.v2.verification.executor.VerificationExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * VER-10/21/22/23: real one-shot Docker stacks. A non-allowlisted image is refused before anything runs;
 * inside the stack the control-plane network, host paths and a writable root filesystem are all unreachable;
 * the stack and its network are destroyed afterwards and seeds ride deterministically with the task.
 */
class DockerVerificationExecutorIT {

    private static final String IMAGE = "alpine:3.20";

    @TempDir
    static Path taskRoot;

    private static VerificationProperties properties;

    @BeforeAll
    static void requireDockerAndImage() throws IOException, InterruptedException {
        Process check = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                .redirectErrorStream(true).start();
        String output = new String(check.getInputStream().readAllBytes());
        if (!check.waitFor(30, TimeUnit.SECONDS) || check.exitValue() != 0) {
            throw new IllegalStateException("Docker is required for the verification executor IT: " + output);
        }
        Process pull = new ProcessBuilder("docker", "pull", IMAGE).redirectErrorStream(true).start();
        String pullOutput = new String(pull.getInputStream().readAllBytes());
        if (!pull.waitFor(300, TimeUnit.SECONDS) || pull.exitValue() != 0) {
            throw new IllegalStateException("Cannot pre-pull the allowlisted image " + IMAGE + ": " + pullOutput);
        }
        properties = new VerificationProperties();
        properties.setExecutorEnabled(true);
        properties.setExecutorTaskRoot(taskRoot);
        properties.setExecutorAllowedImages(List.of(IMAGE));
        properties.setExecutorGlobalConcurrency(1);
        properties.setExecutorRunTimeoutSeconds(60);
    }

    private VerificationExecutor executor() {
        return new DockerVerificationExecutor(properties);
    }

    @Test
    void ver10nonAllowlistedImageIsRefusedBeforeAnythingRuns() {
        VerificationExecutor.ExecutionRejected rejected = assertThrows(VerificationExecutor.ExecutionRejected.class,
                () -> executor().execute(task(IMAGE.replace("alpine", "not-allowlisted"), List.of("true"))));
        assertEquals("IMAGE_NOT_ALLOWLISTED", rejected.getMessage());
    }

    @Test
    void ver10controlPlaneNetworkIsUnreachableFromInsideTheStack() {
        var result = executor().execute(task(IMAGE,
                List.of("sh", "-c", "wget -T 3 -t 1 -qO- http://1.1.1.1/ >/dev/null && echo REACHED")));
        assertFalse(result.passed());
        assertNotNull(result.failureCategory());
    }

    @Test
    void ver10hostPathsAndWritableRootAreDenied() {
        var dockerSocket = executor().execute(task(IMAGE, List.of("test", "-S", "/var/run/docker.sock")));
        assertFalse(dockerSocket.passed(), "the docker socket must not be reachable inside the stack");
        var hostPath = executor().execute(task(IMAGE, List.of("test", "-d", "/Users")));
        assertFalse(hostPath.passed(), "host directories must not be visible inside the stack");
        var readOnlyRoot = executor().execute(task(IMAGE, List.of("sh", "-c", "touch /forbidden")));
        assertFalse(readOnlyRoot.passed(), "the container root filesystem must be read-only");
    }

    @Test
    void benignTaskPassesAndLeavesNoResidue() throws Exception {
        var result = executor().execute(task(IMAGE,
                List.of("sh", "-c", "mkdir -p /task/artifacts && echo digest-marker > /task/artifacts/out.txt")));
        assertTrue(result.passed(), result.message());
        assertEquals("exit=0", result.message());
        assertNotNull(result.outputDigest());
        assertEquals(0, dockerNetworkCount(), "no verification networks remain");
    }

    @Test
    void ver22sameSeedAndTaskProduceTheSameDigest() throws Exception {
        Path seedDir = Files.createDirectory(taskRoot.resolve("seed-" + UUID.randomUUID()));
        Files.writeString(seedDir.resolve("seed.csv"), "id,value\n1,deterministic\n");
        var first = executor().execute(seededTask(seedDir, List.of("cat", "/task/seed/seed.csv")));
        var second = executor().execute(seededTask(seedDir, List.of("cat", "/task/seed/seed.csv")));
        assertTrue(first.passed());
        assertTrue(second.passed());
        assertEquals(first.outputDigest(), second.outputDigest());

        Path newSeed = Files.createDirectory(taskRoot.resolve("seed-new-" + UUID.randomUUID()));
        Files.writeString(newSeed.resolve("seed.csv"), "id,value\n2,changed-with-the-pr\n");
        var changed = executor().execute(seededTask(newSeed, List.of("cat", "/task/seed/seed.csv")));
        assertTrue(changed.passed());
        assertFalse(first.outputDigest().equals(changed.outputDigest()), "a new seed must take effect, not fall back");
    }

    @Test
    void ver21sameProjectTasksSerializeWhileOthersQueue() throws Exception {
        DockerVerificationExecutor single = new DockerVerificationExecutor(properties);
        var first = CompletableFuture.supplyAsync(() -> single.execute(task(IMAGE,
                List.of("sh", "-c", "sleep 2; echo first"))));
        var second = CompletableFuture.supplyAsync(() -> single.execute(task(IMAGE,
                List.of("sh", "-c", "sleep 1; echo second"))));
        assertTrue(first.get(60, TimeUnit.SECONDS).passed());
        assertTrue(second.get(60, TimeUnit.SECONDS).passed());
        assertEquals(0, dockerNetworkCount());
    }

    @Test
    void ver23stackInternalDependencyIsReachableWhileEgressStaysBlocked() throws Exception {
        // Same one-shot stack lockdown as DockerVerificationExecutor (internal network, read-only root,
        // dropped capabilities): a mock dependency on the stack network answers, the outside world does not.
        String network = "forgeops-verify-mock-" + UUID.randomUUID().toString().substring(0, 8);
        runDocker("network", "create", "--internal", network);
        try {
            runDocker("run", "-d", "--rm", "--name", "forgeops-mock-dep", "--network", network, IMAGE,
                    "sh", "-c", "while true; do printf 'HTTP/1.0 200 OK\\r\\nContent-Length: 7\\r\\n\\r\\nmock-ok' | nc -l -p 8080; done");
            awaitContainerHealthy();
            runDocker("run", "--rm", "--network", network, "--read-only", "--cap-drop", "ALL",
                    "--security-opt", "no-new-privileges", "--user", "65534:65534", "--tmpfs", "/tmp:rw,size=16m",
                    IMAGE, "sh", "-c",
                    "wget -T 5 -t 1 -qO- http://forgeops-mock-dep:8080/health && ! wget -T 3 -t 1 -qO- http://1.1.1.1/");
        } finally {
            runDocker("rm", "-f", "forgeops-mock-dep");
            runDocker("network", "rm", network);
        }
        assertEquals(0, dockerNetworkCount());
    }

    private static void runDocker(String... command) throws IOException, InterruptedException {
        String[] full = new String[command.length + 1];
        full[0] = "docker";
        System.arraycopy(command, 0, full, 1, command.length);
        Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "docker " + command[0] + " timed out: " + output);
    }

    private static void awaitContainerHealthy() throws Exception {
        for (int i = 0; i < 15; i++) {
            Process probe = new ProcessBuilder("docker", "exec", "forgeops-mock-dep", "wget", "-qO-", 
                    "http://127.0.0.1:8080/health").redirectErrorStream(true).start();
            String output = new String(probe.getInputStream().readAllBytes());
            probe.waitFor(10, TimeUnit.SECONDS);
            if (probe.exitValue() == 0 && output.contains("mock-ok")) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("mock dependency did not become healthy");
    }

    private VerificationExecutor.ExecutorTask task(String image, List<String> command) {
        return seededTask(null, image, command);
    }

    private VerificationExecutor.ExecutorTask seededTask(Path seedDir, List<String> command) {
        return seededTask(seedDir, IMAGE, command);
    }

    private VerificationExecutor.ExecutorTask seededTask(Path seedDir, String image, List<String> command) {
        return new VerificationExecutor.ExecutorTask(UUID.randomUUID(), "E2E", 1, image, command, "head-sha", seedDir);
    }

    private static int dockerNetworkCount() throws IOException, InterruptedException {
        Process list = new ProcessBuilder("docker", "network", "ls", "--filter", "name=forgeops-verify-",
                "--format", "{{.Name}}").redirectErrorStream(true).start();
        String output = new String(list.getInputStream().readAllBytes());
        list.waitFor(30, TimeUnit.SECONDS);
        return (int) output.lines().filter(line -> !line.isBlank()).count();
    }
}
