package com.example.kaoyan.util;

import com.example.kaoyan.config.AgentDebugLogContextInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDebugLogSmokeTest {

    @Test
    void ndjsonCreatesSessionLogUnderProjectRoot() throws Exception {
        System.clearProperty("agent.debug.log");
        new AgentDebugLogContextInitializer().initialize((ConfigurableApplicationContext) null);

        String prop = System.getProperty("agent.debug.log");
        assertTrue(prop != null && !prop.isBlank(), "agent.debug.log should be set by initializer");

        AgentDebugLog.ndjson("smoke", "AgentDebugLogSmokeTest", "run", "{}");

        Path logPath = Path.of(prop);
        assertTrue(Files.exists(logPath), "Expected log file at " + logPath.toAbsolutePath());
    }
}
