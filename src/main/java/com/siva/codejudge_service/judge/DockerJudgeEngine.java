package com.siva.codejudge_service.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.siva.codejudge_service.entity.TestCase;
import com.siva.codejudge_service.enums.Language;
import com.siva.codejudge_service.enums.Verdict;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Component
public class DockerJudgeEngine {

    @Value("${judge.docker.image.java:openjdk:17-slim}")
    private String javaImage;

    @Value("${judge.docker.image.python:python:3.11-slim}")
    private String pythonImage;

    @Value("${judge.docker.image.cpp:gcc:12-slim}")
    private String cppImage;

    @Value("${judge.docker.image.javascript:node:18-slim}")
    private String jsImage;

    private DockerClient docker;

    private static final int MAX_CONCURRENT_JUDGEMENTS = 10;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(MAX_CONCURRENT_JUDGEMENTS);

    @PostConstruct
    public void init() {
        try {
            var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            var http = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(15))
                    .responseTimeout(Duration.ofSeconds(60))
                    .build();
            docker = DockerClientImpl.getInstance(config, http);
            docker.pingCmd().exec();
            log.info("DockerJudgeEngine: Docker connected successfully");
        } catch (Exception e) {
            log.error("DockerJudgeEngine: Failed to connect to Docker — judge will be unavailable: {}", e.getMessage());
            docker = null;
        }
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
        if (docker != null) {
            try { docker.close(); } catch (Exception ignored) {}
        }
    }

    public JudgeResult judge(String code, Language language,
                             List<TestCase> testCases,
                             int timeLimitMs, int memoryLimitMb) {

        if (docker == null) {
            return JudgeResult.builder()
                    .verdict(Verdict.RE)
                    .errorMessage("Judge engine unavailable: Docker is not connected.")
                    .build();
        }

        if (testCases == null || testCases.isEmpty()) {
            return JudgeResult.builder()
                    .verdict(Verdict.RE)
                    .errorMessage("No test cases found for this problem.")
                    .build();
        }

        int maxElapsedMs = 0;

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            JudgeResult r = runSingle(code, language, tc.getInput(),
                    tc.getExpectedOutput(), timeLimitMs, memoryLimitMb);

            if (r.getExecutionTimeMs() != null) {
                maxElapsedMs = Math.max(maxElapsedMs, r.getExecutionTimeMs());
            }

            if (r.getVerdict() != Verdict.AC) {
                r.setFailedTestCase(i + 1);
                r.setExecutionTimeMs(Math.max(maxElapsedMs, r.getExecutionTimeMs() != null ? r.getExecutionTimeMs() : 0));
                return r;
            }
        }

        return JudgeResult.builder()
                .verdict(Verdict.AC)
                .executionTimeMs(maxElapsedMs)
                .memoryUsedMb(0)
                .build();
    }

    private JudgeResult runSingle(String code, Language language,
                                  String input, String expectedOutput,
                                  int timeLimitMs, int memoryLimitMb) {
        Path workDir     = null;
        String containerId = null;

        try {
            // 1. Workspace
            workDir = Files.createTempDirectory("cj_" + UUID.randomUUID());
            writeSourceFile(workDir, code, language);
            Files.writeString(workDir.resolve("input.txt"), input != null ? input : "");

            // 2. Container config
            String image    = getImage(language);
            String runCmd   = buildRunCommand(language);
            long   memBytes = (long) memoryLimitMb * 1024 * 1024;

            CreateContainerResponse container = docker.createContainerCmd(image)
                    .withCmd("sh", "-c", runCmd)
                    .withWorkingDir("/sandbox")
                    .withNetworkDisabled(true)
                    .withHostConfig(HostConfig.newHostConfig()
                            .withBinds(new Bind(
                                    workDir.toAbsolutePath().toString(),
                                    new Volume("/sandbox")))
                            .withMemory(memBytes)
                            .withMemorySwap(memBytes)
                            .withCpuPeriod(100_000L)
                            .withCpuQuota(50_000L)
                            .withPidsLimit(64L)
                            .withReadonlyRootfs(false))
                    .exec();

            containerId = container.getId();

            // 3. Run + time
            docker.startContainerCmd(containerId).exec();
            long start = System.currentTimeMillis();

            boolean finished = waitForContainer(containerId, timeLimitMs + 2000L);
            long    elapsed  = System.currentTimeMillis() - start;

            if (!finished || elapsed > timeLimitMs) {
                try { docker.killContainerCmd(containerId).exec(); } catch (Exception ignored) {}
                return JudgeResult.builder()
                        .verdict(Verdict.TLE)
                        .executionTimeMs((int) elapsed)
                        .memoryUsedMb(memoryLimitMb)
                        .build();
            }

            // 4. Read output files
            Path outFile = workDir.resolve("output.txt");
            Path errFile = workDir.resolve("error.txt");

            String actualOutput = Files.exists(outFile) ? Files.readString(outFile).strip() : "";
            String errorOutput  = Files.exists(errFile) ? Files.readString(errFile).strip()  : "";

            // 5. Compile/runtime error detection
            if (actualOutput.isBlank() && !errorOutput.isBlank()) {
                boolean isCompileError = errorOutput.contains("error:")
                        || errorOutput.contains("SyntaxError")
                        || errorOutput.contains("cannot find symbol")
                        || errorOutput.contains("compilation failed")
                        || errorOutput.contains("g++:")
                        || errorOutput.contains("javac");

                Verdict v = isCompileError ? Verdict.CE : Verdict.RE;
                return JudgeResult.builder()
                        .verdict(v)
                        .executionTimeMs((int) elapsed)
                        .memoryUsedMb(0)
                        .errorMessage(errorOutput.length() > 500
                                ? errorOutput.substring(0, 500) + "…"
                                : errorOutput)
                        .build();
            }

            // 6. Compare
            String expected = (expectedOutput != null ? expectedOutput : "").strip();
            Verdict verdict = actualOutput.equals(expected) ? Verdict.AC : Verdict.WA;

            return JudgeResult.builder()
                    .verdict(verdict)
                    .executionTimeMs((int) elapsed)
                    .memoryUsedMb(0)
                    .actualOutput(verdict == Verdict.WA ? truncate(actualOutput, 300) : null)
                    .build();

        } catch (Exception e) {
            log.error("Judge engine error for {} submission: {}", language, e.getMessage(), e);
            return JudgeResult.builder()
                    .verdict(Verdict.RE)
                    .errorMessage("Internal judge error: " + e.getMessage())
                    .build();
        } finally {
            if (containerId != null) {
                try { docker.removeContainerCmd(containerId).withForce(true).exec(); }
                catch (Exception ignored) {}
            }
            if (workDir != null) {
                try { deleteDirectory(workDir); } catch (Exception ignored) {}
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeSourceFile(Path dir, String code, Language lang) throws IOException {
        switch (lang) {
            case JAVA       -> Files.writeString(dir.resolve("Solution.java"), code);
            case PYTHON     -> Files.writeString(dir.resolve("solution.py"),   code);
            case CPP        -> Files.writeString(dir.resolve("solution.cpp"),  code);
            case JAVASCRIPT -> Files.writeString(dir.resolve("solution.js"),   code);
        }
    }

    private String buildRunCommand(Language lang) {
        return switch (lang) {
            case JAVA ->
                    "javac Solution.java 2>error.txt && " +
                            "java -Xmx200m -cp . Solution < input.txt > output.txt 2>>error.txt";
            case PYTHON ->
                    "python3 solution.py < input.txt > output.txt 2>error.txt";
            case CPP ->
                    "g++ -O2 -std=c++17 -o solution solution.cpp 2>error.txt && " +
                            "./solution < input.txt > output.txt 2>>error.txt";
            case JAVASCRIPT ->
                    "node --max-old-space-size=200 solution.js < input.txt > output.txt 2>error.txt";
        };
    }

    private String getImage(Language lang) {
        return switch (lang) {
            case JAVA       -> javaImage;
            case PYTHON     -> pythonImage;
            case CPP        -> cppImage;
            case JAVASCRIPT -> jsImage;
        };
    }

    private boolean waitForContainer(String id, long timeoutMs) {
        Future<Boolean> future = executor.submit(() -> {
            try {
                docker.waitContainerCmd(id)
                        .exec(new com.github.dockerjava.core.command.WaitContainerResultCallback())
                        .awaitCompletion();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            future.cancel(true);
            return false;
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}