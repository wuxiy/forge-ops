package com.company.forgeops.v2.verification.executor;

import com.company.forgeops.v2.verification.VerificationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Docker one-shot stack: internal network (no egress), read-only root filesystem, the task directory as the only
 * host mount, dropped capabilities, resource caps and a hard timeout. The stack is always destroyed afterwards;
 * per-project verification is serial and global concurrency is capped (VER-21/23).
 */
@Component
public class DockerVerificationExecutor implements VerificationExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerVerificationExecutor.class);
    private static final Duration NETWORK_CREATE_TIMEOUT = Duration.ofSeconds(15);

    private final VerificationProperties properties;
    private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();
    private final Semaphore globalSlots;

    public DockerVerificationExecutor(VerificationProperties properties) {
        this.properties = properties;
        this.globalSlots = new Semaphore(properties.getExecutorGlobalConcurrency());
    }

    @Override
    public ExecutorResult execute(ExecutorTask task) {
        if (!properties.isExecutorEnabled()) {
            throw new ExecutionRejected("EXECUTOR_DISABLED");
        }
        if (task.image() == null || !properties.getExecutorAllowedImages().contains(task.image())) {
            throw new ExecutionRejected("IMAGE_NOT_ALLOWLISTED");
        }
        try {
            return runLocked(task);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExecutionRejected("EXECUTOR_INTERRUPTED");
        } catch (IOException | IllegalStateException failure) {
            return new ExecutorResult(false, null, "EXECUTOR_ERROR", safeMessage(failure));
        }
    }

    private ExecutorResult runLocked(ExecutorTask task) throws IOException, InterruptedException {
        Object lock = projectLocks.computeIfAbsent(task.planId().toString(), key -> new Object());
        synchronized (lock) {
            if (!globalSlots.tryAcquire(properties.getExecutorRunTimeoutSeconds() + 30, TimeUnit.SECONDS)) {
                return new ExecutorResult(false, null, "GLOBAL_QUEUE_TIMEOUT", "global verification concurrency is full");
            }
            try {
                return runStack(task);
            } finally {
                globalSlots.release();
            }
        }
    }

    private ExecutorResult runStack(ExecutorTask task) throws IOException, InterruptedException {
        Path taskDir = properties.getExecutorTaskRoot().resolve(task.planId() + "-" + task.category() + "-"
                + task.attempt());
        Files.createDirectories(taskDir);
        if (task.seedDir() != null && Files.isDirectory(task.seedDir())) {
            copySeed(task.seedDir(), taskDir.resolve("seed"));
        }
        String network = "forgeops-verify-" + UUID.randomUUID().toString().substring(0, 12);
        CommandResult networkCreated = docker(Duration.ofSeconds(15), "network", "create", "--internal", network);
        if (networkCreated.exitCode() != 0) {
            return new ExecutorResult(false, null, "STACK_CREATE_FAILED", safeMessage(networkCreated));
        }
        try {
            List<String> command = new ArrayList<>(List.of("run", "--rm", "--network", network, "--read-only",
                    "--volume", taskDir + ":/task", "--workdir", "/task", "--tmpfs", "/tmp:rw,size=64m",
                    "--memory", "512m", "--memory-swap", "512m", "--pids-limit", "128", "--cap-drop", "ALL",
                    "--security-opt", "no-new-privileges", "--user", "65534:65534", task.image()));
            command.addAll(task.command());
            CommandResult result = docker(Duration.ofSeconds(properties.getExecutorRunTimeoutSeconds()),
                    command.toArray(String[]::new));
            String digest = sha256(result.output());
            scanArtifacts(taskDir);
            boolean passed = result.exitCode() == 0;
            return new ExecutorResult(passed, digest, passed ? null : "TASK_FAILED",
                    "exit=" + result.exitCode());
        } finally {
            CommandResult cleanup = docker(NETWORK_CREATE_TIMEOUT, "network", "rm", network);
            if (cleanup.exitCode() != 0) {
                log.warn("verification network cleanup failed for {}: {}", network, safeMessage(cleanup));
            }
        }
    }

    private static void copySeed(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
    }

    /** VER-22: seeds ride with the PR; VER-17: artifact content is scanned before anything is stored. */
    private static void scanArtifacts(Path taskDir) throws IOException {
        Path artifacts = taskDir.resolve("artifacts");
        if (!Files.isDirectory(artifacts)) {
            return;
        }
        try (var files = Files.list(artifacts)) {
            for (Path artifact : files.toList()) {
                String content = Files.readString(artifact, StandardCharsets.UTF_8);
                if (content.matches("(?s).*(?i)(api[_-]?key|password|secret|token)\\s*[:=].*")) {
                    throw new IllegalStateException("artifact contains secret-like content: " + artifact.getFileName());
                }
            }
        }
    }

    private CommandResult docker(Duration timeout, String... command) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>(List.of("docker"));
        full.addAll(List.of(command));
        Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new CommandResult(-1, "timeout after " + timeout.toSeconds() + "s: " + output);
        }
        return new CommandResult(process.exitValue(), output);
    }

    private static String safeMessage(Object failure) {
        String message = failure == null ? "unknown" : failure.toString();
        return message.substring(0, Math.min(300, message.length()));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
