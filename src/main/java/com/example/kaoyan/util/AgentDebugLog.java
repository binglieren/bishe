package com.example.kaoyan.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Debug session NDJSON append (session a01679).
 */
public final class AgentDebugLog {

    private static final Logger SLF4J = LoggerFactory.getLogger("com.example.kaoyan.agent.a01679");

    private static final String FILE = "debug-a01679.log";

    /** Cursor debug ingest: writes NDJSON to workspace log when session server is running. */
    private static final String INGEST_URI =
            "http://127.0.0.1:7794/ingest/5c4737e2-1536-438f-a0c1-17dba0a2fdfa";

    private static volatile Path resolvedLogPath;

    private static volatile boolean loggedPath;

    /**
     * Prefer {@code -Dagent.debug.log=G:/bishe/debug-a01679.log}, else walk up from classpath root to directory containing pom.xml, else user.dir.
     */
    public static Path logFilePath() {
        if (resolvedLogPath != null) {
            return resolvedLogPath;
        }
        synchronized (AgentDebugLog.class) {
            if (resolvedLogPath != null) {
                return resolvedLogPath;
            }
            String override = System.getProperty("agent.debug.log");
            if (override != null && !override.isBlank()) {
                resolvedLogPath = Paths.get(override).toAbsolutePath().normalize();
                return resolvedLogPath;
            }
            try {
                URL loc = AgentDebugLog.class.getProtectionDomain().getCodeSource().getLocation();
                if (loc != null && "file".equals(loc.getProtocol())) {
                    Path start = Paths.get(loc.toURI()).toAbsolutePath().normalize();
                    Path p = Files.isRegularFile(start) ? start.getParent() : start;
                    for (int i = 0; i < 10 && p != null; i++) {
                        if (Files.isRegularFile(p.resolve("pom.xml"))) {
                            resolvedLogPath = p.resolve(FILE);
                            return resolvedLogPath;
                        }
                        p = p.getParent();
                    }
                }
            } catch (Throwable ignored) {
                // fall through
            }
            resolvedLogPath = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(FILE);
            return resolvedLogPath;
        }
    }

    public static void ndjson(String hypothesisId, String location, String message, String jsonData) {
        Path p = logFilePath();
        String data = (jsonData == null || jsonData.isBlank()) ? "{}" : jsonData;
        String line = "{\"sessionId\":\"a01679\",\"hypothesisId\":\"" + esc(hypothesisId) + "\",\"location\":\""
                + esc(location) + "\",\"message\":\"" + esc(message) + "\",\"data\":" + data
                + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
        String oneLine = line.trim();
        SLF4J.warn("NDJSON {}", oneLine);
        // #region agent log ingest mirror
        mirrorToIngest(oneLine);
        // #endregion
        try {
            Files.writeString(p, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Path cursorDir = p.getParent().resolve(".cursor");
            Files.createDirectories(cursorDir);
            Path cursorLog = cursorDir.resolve(FILE);
            Files.writeString(cursorLog, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ex) {
            if (!loggedPath) {
                loggedPath = true;
                SLF4J.warn("AgentDebugLog file write failed path={} err={}", p, ex.toString());
            }
        }
    }

    private static void mirrorToIngest(String jsonBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(INGEST_URI))
                    .timeout(Duration.ofMillis(1500))
                    .header("Content-Type", "application/json")
                    .header("X-Debug-Session-Id", "a01679")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(600))
                    .build()
                    .sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
            // ingest server may be offline outside Cursor debug sessions
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
