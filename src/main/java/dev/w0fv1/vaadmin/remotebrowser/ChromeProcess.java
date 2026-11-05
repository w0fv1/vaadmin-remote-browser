package dev.w0fv1.vaadmin.remotebrowser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ChromeProcess implements AutoCloseable {
    private static final String PORT_FILE = "DevToolsActivePort";
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(15);
    private static final long POLL_MILLIS = 50;

    private final Process process;
    private final URI browserEndpoint;
    private final BrowserOutputCollector output;

    private ChromeProcess(Process process, URI browserEndpoint, BrowserOutputCollector output) {
        this.process = process;
        this.browserEndpoint = browserEndpoint;
        this.output = output;
    }

    static ChromeProcess start(
            Path executable,
            List<String> arguments,
            Path profileDirectory,
            Duration timeout) {
        Path portFile = profileDirectory.resolve(PORT_FILE);
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException failure) {
            throw new IllegalStateException("无法清理浏览器调试端口文件: " + portFile, failure);
        }
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable.toString());
        command.addAll(arguments);
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            Path libraries = executable.getParent().resolve("lib");
            if (Files.isDirectory(libraries)) {
                builder.environment().put("LD_LIBRARY_PATH", libraries.toString());
                builder.environment().put("FONTCONFIG_FILE", executable.getParent().resolve("fonts.conf").toString());
            }
            process = builder.start();
        } catch (IOException failure) {
            throw new IllegalStateException("无法启动浏览器: " + executable, failure);
        }
        BrowserOutputCollector output = new BrowserOutputCollector(process.getInputStream());
        try {
            List<String> lines = awaitPort(process, portFile, timeout, output);
            int port = Integer.parseInt(lines.get(0).trim());
            return new ChromeProcess(process, URI.create("ws://127.0.0.1:" + port + lines.get(1).trim()), output);
        } catch (RuntimeException failure) {
            ProcessHandle handle = process.toHandle();
            terminate(handle, handle.descendants().toList());
            output.close();
            throw failure;
        }
    }

    URI browserEndpoint() {
        return browserEndpoint;
    }

    boolean isAlive() {
        return process.isAlive();
    }

    void shutdown(Duration grace) {
        ProcessHandle handle = process.toHandle();
        List<ProcessHandle> descendants = handle.descendants().toList();
        awaitExit(handle, grace);
        terminate(handle, descendants);
        output.close();
    }

    @Override
    public void close() {
        shutdown(Duration.ZERO);
    }

    private static List<String> awaitPort(
            Process process,
            Path portFile,
            Duration timeout,
            BrowserOutputCollector output) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "浏览器进程提前退出 (" + process.exitValue() + "): " + output.tail());
            }
            try {
                List<String> lines = Files.readAllLines(portFile);
                if (lines.size() >= 2 && !lines.get(1).isBlank()) return lines;
            } catch (IOException ignored) {
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("浏览器调试端口未就绪: " + output.tail());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待浏览器调试端口时被中断", interrupted);
            }
        }
    }

    private static void terminate(ProcessHandle handle, List<ProcessHandle> descendants) {
        stop(handle);
        descendants.forEach(ChromeProcess::stop);
        descendants.forEach(descendant -> awaitExit(descendant, STOP_TIMEOUT));
    }

    private static void stop(ProcessHandle handle) {
        if (!handle.isAlive()) return;
        handle.destroy();
        if (awaitExit(handle, STOP_TIMEOUT)) return;
        handle.destroyForcibly();
        awaitExit(handle, STOP_TIMEOUT);
    }

    private static boolean awaitExit(ProcessHandle handle, Duration timeout) {
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return !handle.isAlive();
        } catch (ExecutionException | TimeoutException failure) {
            return !handle.isAlive();
        }
    }
}
