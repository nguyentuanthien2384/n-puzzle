package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.search.ConfigurableAlgorithm;
import com.example.npuzzleai.search.SearchAlgorithm;

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
 * Danh mục thuật toán. Mã có dạng {@code ten} hoặc {@code ten:tham_so}, ví dụ
 * {@code wastar:2.0} (trọng số), {@code sma:50000} (số node tối đa), {@code ida-tt:64} (MB bảng chuyển vị).
 * Mã đầy đủ được ghi vào kết quả thí nghiệm nên cấu hình luôn tái tạo được.
 *
 * <p>Plugin: đặt JAR vào một thư mục và gọi {@link #loadPlugins(Path)}; JAR cần khai báo
 * {@code META-INF/services/com.example.npuzzleai.search.SearchAlgorithm}.</p>
 */
public final class AlgorithmRegistry {
    private static final AlgorithmRegistry DEFAULT = createDefault();

    private final Map<String, Function<String, SearchAlgorithm>> factories = new LinkedHashMap<>();
    private final Map<String, String> defaultSpecs = new LinkedHashMap<>();

    public static AlgorithmRegistry defaults() {
        return DEFAULT;
    }

    private static AlgorithmRegistry createDefault() {
        AlgorithmRegistry r = new AlgorithmRegistry();
        r.define("bfs", "bfs", p -> new BreadthFirstSearch());
        r.define("astar", "astar", p -> AStarSearch.standard());
        r.define("astar-noreopen", "astar-noreopen", p -> AStarSearch.withoutReopening());
        r.define("wastar", "wastar:1.5", p -> AStarSearch.weighted(p == null ? 1.5 : Double.parseDouble(p)));
        r.define("greedy", "greedy", p -> AStarSearch.greedy());
        r.define("ida", "ida", p -> new IDAStarSearch(0));
        r.define("ida-tt", "ida-tt:64", p -> new IDAStarSearch(p == null ? 64 : Integer.parseInt(p)));
        r.define("rbfs", "rbfs", p -> new RecursiveBestFirstSearch());
        r.define("sma", "sma:" + SMAStarSearch.DEFAULT_MAX_NODES,
                p -> new SMAStarSearch(p == null ? SMAStarSearch.DEFAULT_MAX_NODES : Integer.parseInt(p)));
        r.define("hda", "hda:" + HashDistributedAStar.defaultWorkers(),
                p -> new HashDistributedAStar(p == null ? HashDistributedAStar.defaultWorkers() : Integer.parseInt(p)));
        r.define("mcts", "mcts:" + MonteCarloTreeSearch.DEFAULT_SIMULATIONS,
                p -> new MonteCarloTreeSearch(p == null ? MonteCarloTreeSearch.DEFAULT_SIMULATIONS : Integer.parseInt(p)));
        r.loadProviders(ServiceLoader.load(SearchAlgorithm.class));
        return r;
    }

    private synchronized void define(String base, String defaultSpec, Function<String, SearchAlgorithm> factory) {
        factories.put(base, factory);
        defaultSpecs.put(base, defaultSpec);
    }

    /**
     * Đăng ký một instance (plugin). Plugin thường dùng mã {@code algorithm.id()} cố định; plugin cài đặt
     * {@link ConfigurableAlgorithm} được đăng ký theo mã gốc (trước dấu ':') và nhận tham số.
     */
    public synchronized void register(SearchAlgorithm algorithm) {
        String id = algorithm.id();
        if (algorithm instanceof ConfigurableAlgorithm configurable) {
            String base = baseOf(id);
            factories.put(base, p -> p == null ? algorithm : configurable.configure(p));
            defaultSpecs.put(base, id);
        } else {
            factories.put(id, p -> algorithm);
            defaultSpecs.put(id, id);
        }
    }

    private static String baseOf(String spec) {
        int colon = spec.indexOf(':');
        return colon < 0 ? spec : spec.substring(0, colon);
    }

    /** Tạo thuật toán theo mã, ví dụ "ida", "wastar:2.5". */
    public synchronized SearchAlgorithm create(String spec) {
        String trimmed = spec.trim();
        int colon = trimmed.indexOf(':');
        String base = colon < 0 ? trimmed : trimmed.substring(0, colon);
        String param = colon < 0 ? null : trimmed.substring(colon + 1).trim();
        Function<String, SearchAlgorithm> factory = factories.get(base);
        if (factory == null) {
            throw new IllegalArgumentException("Không có thuật toán '" + base + "'. Có: " + String.join(", ", factories.keySet()));
        }
        try {
            return factory.apply(param == null || param.isEmpty() ? null : param);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tham số không hợp lệ cho '" + base + "': " + param);
        }
    }

    /** Mã mặc định của mọi thuật toán, theo thứ tự đăng ký. */
    public synchronized List<String> defaultSpecs() {
        return new ArrayList<>(defaultSpecs.values());
    }

    public synchronized List<String> baseIds() {
        return new ArrayList<>(factories.keySet());
    }

    /** Instance mặc định của mọi thuật toán. */
    public List<SearchAlgorithm> all() {
        List<SearchAlgorithm> list = new ArrayList<>();
        for (String spec : defaultSpecs()) list.add(create(spec));
        return list;
    }

    /** Nạp plugin từ mọi file .jar trong thư mục; trả về số thuật toán mới. */
    public int loadPlugins(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        List<URL> urls = new ArrayList<>();
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path jar : jars) urls.add(toUrl(jar));
        }
        if (urls.isEmpty()) return 0;
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), AlgorithmRegistry.class.getClassLoader());
        return loadProviders(ServiceLoader.load(SearchAlgorithm.class, loader));
    }

    private int loadProviders(ServiceLoader<SearchAlgorithm> loader) {
        int count = 0;
        try {
            for (SearchAlgorithm algorithm : loader) {
                synchronized (this) {
                    String key = algorithm instanceof ConfigurableAlgorithm ? baseOf(algorithm.id()) : algorithm.id();
                    if (factories.containsKey(key)) continue;
                }
                register(algorithm);
                count++;
            }
        } catch (ServiceConfigurationError e) {
            System.err.println("[AlgorithmRegistry] Bỏ qua plugin lỗi: " + e.getMessage());
        }
        return count;
    }

    private static URL toUrl(Path jar) {
        try {
            return jar.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
