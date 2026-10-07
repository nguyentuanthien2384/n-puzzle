package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.heuristics.pdb.AdditivePatternDatabaseHeuristic;
import com.example.npuzzleai.search.Heuristic;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * Danh mục heuristic. Mã có thể kèm tham số:
 * {@code apdb} (phân hoạch mặc định), {@code apdb:1,2,5,6,9;3,4,7,8,11;10,12,13,14,15} (tường minh),
 * {@code pdb:1,2,3,4} (một pattern). Instance được cache theo mã để bảng PDB dùng chung.
 */
public final class HeuristicRegistry {
    private static final HeuristicRegistry DEFAULT = createDefault();
    /** Mã tương ứng H1..H9 của giao diện cũ (State.heuristic). */
    private static final String[] LEGACY_IDS = {
            null, "misplaced", "manhattan", "euclid", "rowcol", "legacy-h5", "legacy-h6",
            "walking-distance", "wd-lc", "apdb"
    };

    private final Map<String, Function<String, Heuristic>> factories = new LinkedHashMap<>();
    private final Map<String, Heuristic> instances = new LinkedHashMap<>();

    public static HeuristicRegistry defaults() {
        return DEFAULT;
    }

    private static HeuristicRegistry createDefault() {
        HeuristicRegistry r = new HeuristicRegistry();
        r.define("zero", p -> new BasicHeuristics.Zero());
        r.define("misplaced", p -> new BasicHeuristics.Misplaced());
        r.define("manhattan", p -> new BasicHeuristics.Manhattan());
        r.define("euclid", p -> new BasicHeuristics.Euclidean());
        r.define("rowcol", p -> new BasicHeuristics.RowColumn());
        r.define("linear-conflict", p -> new BasicHeuristics.LinearConflict());
        r.define("legacy-h5", p -> new LegacyHeuristics.H5());
        r.define("legacy-h6", p -> new LegacyHeuristics.H6());
        r.define("walking-distance", p -> new WalkingDistanceHeuristic());
        r.define("wd-lc", p -> new MaxHeuristic("wd-lc", "H8 max(MD+LC, Walking Distance)",
                List.of(new BasicHeuristics.LinearConflict(), new WalkingDistanceHeuristic())));
        r.define("apdb", p -> p == null
                ? AdditivePatternDatabaseHeuristic.defaultPartition()
                : explicitPartition(p));
        r.define("pdb", p -> {
            if (p == null) throw new IllegalArgumentException("pdb cần danh sách ô, ví dụ pdb:1,2,3,4");
            return explicitPartition(p);
        });
        r.loadProviders(ServiceLoader.load(Heuristic.class));
        return r;
    }

    /**
     * Partition tường minh. Kích thước bảng ghi rõ bằng tiền tố, ví dụ {@code 4x4@1,2,3,4},
     * hoặc suy từ ô lớn nhất nếu không có tiền tố.
     */
    private static Heuristic explicitPartition(String param) {
        int at = param.indexOf('@');
        if (at > 0) {
            String dim = param.substring(0, at).trim().toLowerCase();
            int x = dim.indexOf('x');
            int size = Integer.parseInt(x > 0 ? dim.substring(0, x) : dim);
            return AdditivePatternDatabaseHeuristic.explicit(param.substring(at + 1), size);
        }
        return AdditivePatternDatabaseHeuristic.explicit(param, inferSize(param));
    }

    /** Kích thước nhỏ nhất chứa được ô lớn nhất trong partition. */
    private static int inferSize(String spec) {
        int max = 0;
        for (String part : spec.split("[;,\\s]+")) {
            if (!part.isBlank()) max = Math.max(max, Integer.parseInt(part.trim()));
        }
        int size = 2;
        while (size * size - 1 < max) size++;
        return size;
    }

    private synchronized void define(String base, Function<String, Heuristic> factory) {
        factories.put(base, factory);
    }

    public synchronized void register(Heuristic heuristic) {
        factories.put(heuristic.id(), p -> heuristic);
        instances.put(heuristic.id(), heuristic);
    }

    /** Lấy heuristic theo mã (cache theo mã đầy đủ). */
    public synchronized Heuristic create(String spec) {
        String trimmed = spec.trim();
        Heuristic cached = instances.get(trimmed);
        if (cached != null) return cached;
        int colon = trimmed.indexOf(':');
        String base = colon < 0 ? trimmed : trimmed.substring(0, colon);
        String param = colon < 0 ? null : trimmed.substring(colon + 1).trim();
        Function<String, Heuristic> factory = factories.get(base);
        if (factory == null) {
            throw new IllegalArgumentException("Không có heuristic '" + base + "'. Có: " + String.join(", ", factories.keySet()));
        }
        Heuristic h;
        try {
            h = factory.apply(param == null || param.isEmpty() ? null : param);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tham số không hợp lệ cho '" + base + "': " + param);
        }
        instances.put(trimmed, h);
        return h;
    }

    /** Mã mặc định (không tham số) theo thứ tự đăng ký; bỏ "pdb" vì bắt buộc có tham số. */
    public synchronized List<String> defaultIds() {
        List<String> ids = new ArrayList<>();
        for (String id : factories.keySet()) if (!id.equals("pdb")) ids.add(id);
        return ids;
    }

    public List<Heuristic> all() {
        List<Heuristic> list = new ArrayList<>();
        for (String id : defaultIds()) list.add(create(id));
        return list;
    }

    /** Mã heuristic mới tương ứng với chỉ số H1..H9 của giao diện cũ. */
    public static String legacyId(int index) {
        if (index < 1 || index >= LEGACY_IDS.length) return "manhattan";
        return LEGACY_IDS[index];
    }

    public int loadPlugins(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        List<URL> urls = new ArrayList<>();
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path jar : jars) {
                try {
                    urls.add(jar.toUri().toURL());
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        if (urls.isEmpty()) return 0;
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), HeuristicRegistry.class.getClassLoader());
        return loadProviders(ServiceLoader.load(Heuristic.class, loader));
    }

    private int loadProviders(ServiceLoader<Heuristic> loader) {
        int count = 0;
        try {
            for (Heuristic h : loader) {
                synchronized (this) {
                    if (factories.containsKey(h.id())) continue;
                }
                register(h);
                count++;
            }
        } catch (ServiceConfigurationError e) {
            System.err.println("[HeuristicRegistry] Bỏ qua plugin lỗi: " + e.getMessage());
        }
        return count;
    }
}
