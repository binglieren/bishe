package com.example.kaoyan.config;

import com.example.kaoyan.KaoyanApplication;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

/**
 * Runs before the context starts so {@code agent.debug.log} points at the Maven project root (pom.xml parent).
 */
public class AgentDebugLogContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String existing = System.getProperty("agent.debug.log");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String fromEnv = System.getenv("AGENT_DEBUG_LOG");
        if (fromEnv != null && !fromEnv.isBlank()) {
            System.setProperty("agent.debug.log", new File(fromEnv).getAbsolutePath());
            return;
        }
        for (String envKey : new String[]{"WORKSPACE_FOLDER", "CURSOR_WORKSPACE", "VSCODE_WORKSPACE_FOLDER"}) {
            String root = System.getenv(envKey);
            if (root == null || root.isBlank()) {
                continue;
            }
            File rootDir = new File(root);
            if (new File(rootDir, "pom.xml").isFile()) {
                System.setProperty("agent.debug.log", new File(rootDir, "debug-a01679.log").getAbsolutePath());
                return;
            }
        }
        File cwd = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        if (new File(cwd, "pom.xml").isFile()) {
            System.setProperty("agent.debug.log", new File(cwd, "debug-a01679.log").getAbsolutePath());
            return;
        }
        try {
            ApplicationHome home = new ApplicationHome(KaoyanApplication.class);
            File dir = home.getDir();
            if (dir == null) {
                return;
            }
            File walk = dir.isFile() ? dir.getParentFile() : dir;
            for (int i = 0; i < 14 && walk != null; i++) {
                if (new File(walk, "pom.xml").isFile()) {
                    File logFile = new File(walk, "debug-a01679.log");
                    System.setProperty("agent.debug.log", logFile.getAbsolutePath());
                    return;
                }
                walk = walk.getParentFile();
            }
        } catch (Throwable ignored) {
            // keep default resolution in AgentDebugLog
        }
    }
}
