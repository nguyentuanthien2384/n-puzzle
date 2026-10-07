package com.example.npuzzleai.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Thu thập thông tin môi trường cho giao thức tái lập: hệ điều hành, CPU, RAM, JDK, tham số JVM,
 * commit Git (đọc trực tiếp thư mục .git, không gọi tiến trình ngoài) và phiên bản dependency Maven.
 */
public final class EnvironmentInfo {
    private EnvironmentInfo() {
    }

    public static Map<String, Object> collect() {
        Map<String, Object> env = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        env.put("osName", System.getProperty("os.name"));
        env.put("osVersion", System.getProperty("os.version"));
        env.put("osArch", System.getProperty("os.arch"));
        env.put("cpuModel", cpuModel());
        env.put("availableProcessors", rt.availableProcessors());
        env.put("physicalMemoryBytes", physicalMemory());
        env.put("maxHeapBytes", rt.maxMemory());
        env.put("javaVendor", System.getProperty("java.vendor"));
        env.put("javaVersion", System.getProperty("java.version"));
        env.put("javaVmName", System.getProperty("java.vm.name"));
        env.put("javaVmVersion", System.getProperty("java.vm.version"));
        env.put("jvmArgs", jvmArgs());
        Map<String, String> git = gitInfo();
        env.put("gitCommit", git.getOrDefault("commit", "unknown"));
        env.put("gitBranch", git.getOrDefault("branch", "unknown"));
        env.put("build", buildProperties());
        return env;
    }

    public static List<String> jvmArgs() {
        try {
            return ManagementFactory.getRuntimeMXBean().getInputArguments();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static long physicalMemory() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) return sun.getTotalMemorySize();
        } catch (Throwable ignored) {
            // Không phải HotSpot/OpenJDK hoặc module jdk.management không có.
        }
        return -1;
    }

    private static String cpuModel() {
        String win = System.getenv("PROCESSOR_IDENTIFIER");
        if (win != null && !win.isBlank()) return win;
        Path cpuinfo = Path.of("/proc/cpuinfo");
        if (Files.isReadable(cpuinfo)) {
            try {
                for (String line : Files.readAllLines(cpuinfo, StandardCharsets.UTF_8)) {
                    if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
                }
            } catch (IOException ignored) {
                // bỏ qua
            }
        }
        return System.getProperty("os.arch");
    }

    /** Commit/nhánh Git hiện tại, tìm thư mục .git từ thư mục làm việc trở lên. */
    public static Map<String, String> gitInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve(".git"))) dir = dir.getParent();
        if (dir == null) return info;
        Path git = dir.resolve(".git");
        try {
            String head = Files.readString(git.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            if (head.startsWith("ref:")) {
                String ref = head.substring(4).trim();
                info.put("branch", ref.replace("refs/heads/", ""));
                Path refFile = git.resolve(ref);
                if (Files.isRegularFile(refFile)) {
                    info.put("commit", Files.readString(refFile, StandardCharsets.UTF_8).trim());
                } else {
                    Path packed = git.resolve("packed-refs");
                    if (Files.isRegularFile(packed)) {
                        for (String line : Files.readAllLines(packed, StandardCharsets.UTF_8)) {
                            if (line.endsWith(" " + ref)) info.put("commit", line.substring(0, line.indexOf(' ')));
                        }
                    }
                }
            } else {
                info.put("branch", "(detached)");
                info.put("commit", head);
            }
        } catch (IOException ignored) {
            // bỏ qua
        }
        return info;
    }

    /** Phiên bản dự án và dependency, do Maven điền vào build.properties khi build (resource filtering). */
    public static Map<String, String> buildProperties() {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = EnvironmentInfo.class.getResourceAsStream("/com/example/npuzzleai/build.properties")) {
            if (in == null) return map;
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            for (String key : p.stringPropertyNames()) map.put(key, p.getProperty(key));
        } catch (IOException ignored) {
            // bỏ qua
        }
        return map;
    }
}
