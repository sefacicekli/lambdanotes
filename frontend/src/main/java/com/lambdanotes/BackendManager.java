package com.lambdanotes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class BackendManager {

    private Process backendProcess;

    public void startBackend() {
        try {
            String backendPath = findBackendExecutable();
            if (backendPath == null) {
                System.err.println("Backend executable not found!");
                return;
            }

            File dataDir = new File(System.getProperty("user.home"), ".lambdanotes");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            ProcessBuilder pb = new ProcessBuilder(backendPath);
            pb.directory(dataDir); // Set working directory to user's home/.lambdanotes
            pb.redirectErrorStream(true);
            
            // Hide console window on Windows
            // This is handled by the fact that we are launching from a javaw process (no console)
            // and ProcessBuilder doesn't create a console by default unless inheritIO is used.
            // However, the backend itself is a console app. To hide it completely:
            // We rely on the fact that the main app is now windowed (no console).
            
            backendProcess = pb.start();
            
            // Consume output in a separate thread to prevent blocking if buffer fills up
            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(backendProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // System.out.println("Backend: " + line); // Optional logging
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }).start();

            System.out.println("Backend started: " + backendPath);
            System.out.println("Working directory: " + dataDir.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopBackend() {
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            System.out.println("Backend stopped.");
        }
    }

    private String findBackendExecutable() {
        String appDir = System.getProperty("user.dir");
        
        // 1. Look in 'app' subdirectory (jpackage structure)
        File jpackageExe = new File(appDir, "app/backend.exe");
        if (jpackageExe.exists()) return jpackageExe.getAbsolutePath();

        // 2. Look in the same directory (Flat structure)
        File flatExe = new File(appDir, "backend.exe");
        if (flatExe.exists()) return flatExe.getAbsolutePath();

        // 3. Look in ../backend/ (Development)
        File devExe = Paths.get(appDir, "..", "backend", "main.exe").toFile(); 
        if (devExe.exists()) return devExe.getAbsolutePath();
        
        File devExe2 = Paths.get(appDir, "..", "backend", "backend.exe").toFile();
        if (devExe2.exists()) return devExe2.getAbsolutePath();

        return null;
    }
}
