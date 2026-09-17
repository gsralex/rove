package com.gsralex.rove.core.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BashTool implements Tool {

    private static final long DEFAULT_TIMEOUT_MS = 120_000L;
    private static final long MAX_TIMEOUT_MS = 600_000L;
    private static final int MAX_OUTPUT_CHARS = 30_000;
    private static final int MAX_OUTPUT_BYTES = MAX_OUTPUT_CHARS * 4;
    private static final long DRAIN_GRACE_MS = 1_500L;
    private static final String META_ENV = "ROVE_BASH_META_FILE";

    private static final Boolean BASH_OK = probeBash();

    private final Path initialCwd;
    private Path cwd;

    public BashTool() {
        this(Path.of("").toAbsolutePath().normalize());
    }

    public BashTool(Path workingDirectory) {
        this.initialCwd = workingDirectory.toAbsolutePath().normalize();
        this.cwd = this.initialCwd;
    }

    public static boolean available() {
        return BASH_OK;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Run a local bash command (macOS/Linux, or Windows with bash on PATH e.g. Git Bash/WSL). "
                + "Session keeps working directory across calls. "
                + "Args: command (required), optional timeout ms (default 120000, max 600000), "
                + "optional description, optional stdin (text fed to the command's standard input), "
                + "optional restart=true to reset cwd. "
                + "Background processes must redirect their output (e.g. 'nohup cmd >log 2>&1 &').";
    }

    @Override
    public String inputSchema() {

        return """
                {"type":"object","properties":{
                "command":{"type":"string","description":"bash command to run"},
                "timeout":{"type":"integer","description":"timeout in milliseconds (default 120000, max 600000)"},
                "description":{"type":"string","description":"short 5-10 word summary of what this command does"},
                "stdin":{"type":"string","description":"optional text written to the command's standard input, then EOF"},
                "restart":{"type":"boolean","description":"if true, reset working directory to the session start cwd before running"}
                },"required":["command"]}""";
    }

    @Override
    public String call(Map<String, Object> args) {
        if (!available()) {
            return "Error: bash not available on this system (needs macOS/Linux bash, or Windows Git Bash/WSL on PATH)";
        }
        Map<String, Object> a = args == null ? Map.of() : args;
        synchronized (this) {
            return execute(a);
        }
    }

    private String execute(Map<String, Object> a) {
        if (truthy(a.get("restart"))) {
            cwd = initialCwd;
            Object restartCmd = a.get("command");
            if (restartCmd == null || String.valueOf(restartCmd).isBlank()) {
                return "bash session restarted; cwd=" + cwd;
            }
        }
        Object raw = a.get("command");
        String command = raw == null ? "" : String.valueOf(raw);
        if (command.isBlank()) {
            return "Error: empty command";
        }
        long timeoutMs = timeoutOf(a.get("timeout"));
        String stdin = a.get("stdin") == null ? null : String.valueOf(a.get("stdin"));
        Path work = cwd;

        Path metaFile;
        try {
            metaFile = Files.createTempFile("rove-bash-", ".meta");
        } catch (IOException e) {
            return "Error: cannot create temp file for bash metadata: " + e.getMessage();
        }

        Process p = null;
        CappedOutput captured = new CappedOutput(MAX_OUTPUT_BYTES);
        try {
            String quotedCwd = shellSingleQuote(work.toString());
            String script = "cd " + quotedCwd
                    + " || { printf 'Error: cannot cd to %s\\n' " + quotedCwd + " >&2; exit 1; }\n"
                    + command
                    + "\n__rove_ec=$?\n"
                    + "printf '%s\\n%s\\n' \"$__rove_ec\" \"$(pwd -P)\" > \"$" + META_ENV + "\" 2>/dev/null\n"
                    + "exit \"$__rove_ec\"\n";

            ProcessBuilder pb = new ProcessBuilder("bash", "-lc", script)
                    .redirectErrorStream(true)
                    .directory(work.toFile());
            pb.environment().put(META_ENV, metaFile.toString());

            p = pb.start();
            Process proc = p;
            feedStdin(proc, stdin);

            Thread reader =
                    Thread.ofVirtual().name("rove-bash-reader").unstarted(() -> drain(proc.getInputStream(), captured));
            reader.start();

            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                killTree(proc);
                if (!joinReader(reader, DRAIN_GRACE_MS)) {
                    closeQuietly(proc.getInputStream());
                    joinReader(reader, 500);
                }
                return "Error: command timed out after " + timeoutMs + "ms; process tree killed. cwd=" + cwd + "\n"
                        + render(captured);
            }
            if (!joinReader(reader, DRAIN_GRACE_MS)) {
                closeQuietly(proc.getInputStream());
                joinReader(reader, 500);
            }

            int exitCode = proc.exitValue();
            String nextCwd = null;
            String meta = readQuietly(metaFile);
            if (meta != null && !meta.isBlank()) {
                String[] lines = meta.split("\n");
                try {
                    exitCode = Integer.parseInt(lines[0].trim());
                } catch (NumberFormatException ignored) {

                }
                if (lines.length > 1 && !lines[1].isBlank()) {
                    nextCwd = lines[1].trim();
                }
            }
            if (nextCwd != null) {
                try {
                    cwd = Path.of(nextCwd).toAbsolutePath().normalize();
                } catch (Exception ignored) {
                }
            }
            return "exit=" + exitCode + " cwd=" + cwd + "\n" + render(captured);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(p);
            return "Error: interrupted while running command. cwd=" + cwd;
        } catch (Exception e) {
            killTree(p);
            return "Error: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            deleteQuietly(metaFile);
        }
    }

    private static void feedStdin(Process p, String stdin) throws IOException {
        if (stdin == null) {
            p.getOutputStream().close();
            return;
        }
        try (OutputStream os = p.getOutputStream()) {
            os.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void killTree(Process p) {
        if (p == null) {
            return;
        }
        try {
            List<ProcessHandle> descendants = p.descendants().toList();
            for (ProcessHandle child : descendants) {
                child.destroyForcibly();
            }
            p.destroyForcibly();
            for (ProcessHandle child : p.descendants().toList()) {
                child.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean joinReader(Thread reader, long millis) {
        try {
            reader.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !reader.isAlive();
    }

    private static void drain(InputStream in, CappedOutput out) {
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.append(buf, n);
            }
        } catch (Exception ignored) {
        }
    }

    private static Boolean probeBash() {
        try {
            Process p = new ProcessBuilder("bash", "-lc", "true")
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) {
                killTree(p);
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static long timeoutOf(Object raw) {
        if (!(raw instanceof Number n)) {
            return DEFAULT_TIMEOUT_MS;
        }
        long v = n.longValue();
        if (v <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(v, MAX_TIMEOUT_MS);
    }

    private static boolean truthy(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return false;
        }
        String s = String.valueOf(raw).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static String shellSingleQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }

    private static String render(CappedOutput captured) {
        byte[] kept = captured.kept();
        String text = new String(kept, StandardCharsets.UTF_8);
        boolean truncated = captured.total() > kept.length;
        if (text.length() > MAX_OUTPUT_CHARS) {
            text = text.substring(0, MAX_OUTPUT_CHARS);
            truncated = true;
        }
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (truncated) {
            text = text + "\n…(output truncated: " + captured.total() + " bytes total, limit " + MAX_OUTPUT_CHARS
                    + " chars)";
        }
        return text;
    }

    private static final class CappedOutput {
        private final ByteArrayOutputStream kept;
        private final int limit;
        private long total;

        CappedOutput(int limit) {
            this.limit = limit;
            this.kept = new ByteArrayOutputStream(Math.min(limit, 8192));
        }

        void append(byte[] buf, int len) {
            total += len;
            int room = limit - kept.size();
            if (room > 0) {
                kept.write(buf, 0, Math.min(room, len));
            }
        }

        byte[] kept() {
            return kept.toByteArray();
        }

        long total() {
            return total;
        }
    }
}
