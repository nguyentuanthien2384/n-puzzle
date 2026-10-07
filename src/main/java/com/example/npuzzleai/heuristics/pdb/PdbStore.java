package com.example.npuzzleai.heuristics.pdb;

import com.example.npuzzleai.core.Goal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ nhớ đệm PDB hai tầng: RAM (theo đích + pattern) và thư mục đĩa chứa file {@code .pdb}.
 *
 * <p>Định dạng file: magic "NPDB", phiên bản, metadata đầy đủ, CRC32 và bảng. Khi nạp, metadata
 * phải khớp đích/pattern và checksum phải đúng, nếu không file bị bỏ qua và dựng lại.</p>
 *
 * <p>Thư mục mặc định là {@code pdb-cache} (đổi bằng system property {@code npuzzle.pdb.dir};
 * giá trị {@code none} tắt cache đĩa).</p>
 */
public final class PdbStore {
    private static final int MAGIC = 0x4E504442; // "NPDB"
    private static final int FORMAT_VERSION = 1;
    private static final Map<String, PatternDatabase> MEMORY = new ConcurrentHashMap<>();
    private static volatile Path directory = initialDirectory();

    private PdbStore() {
    }

    private static Path initialDirectory() {
        String prop = System.getProperty("npuzzle.pdb.dir", "pdb-cache");
        return prop.equalsIgnoreCase("none") || prop.isBlank() ? null : Path.of(prop);
    }

    /** Đặt thư mục cache đĩa; null để tắt. */
    public static void setDirectory(Path dir) {
        directory = dir;
    }

    public static Path directory() {
        return directory;
    }

    public static void clearMemory() {
        MEMORY.clear();
    }

    public static PatternDatabase get(PatternDefinition pattern, Goal goal) {
        String key = goal.size() + "|" + goal.board() + "|" + pattern.key();
        return MEMORY.computeIfAbsent(key, k -> loadOrBuild(pattern, goal));
    }

    private static PatternDatabase loadOrBuild(PatternDefinition pattern, Goal goal) {
        Path dir = directory;
        Path file = dir == null ? null : dir.resolve(fileName(pattern, goal));
        if (file != null && Files.isRegularFile(file)) {
            try {
                PatternDatabase db = read(file);
                if (db.metadata().matches(pattern, goal)) return db;
            } catch (IOException e) {
                System.err.println("[PdbStore] Bỏ qua file PDB hỏng " + file + ": " + e.getMessage());
            }
        }
        PatternDatabase db = PatternDatabaseBuilder.build(pattern, goal);
        if (file != null) {
            try {
                write(db, file);
            } catch (IOException e) {
                System.err.println("[PdbStore] Không ghi được cache " + file + ": " + e.getMessage());
            }
        }
        return db;
    }

    public static String fileName(PatternDefinition pattern, Goal goal) {
        int[] tiles = goal.board().toArray();
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        for (int t : tiles) crc.update(t);
        return "pdb-" + goal.size() + "x" + goal.size() + "-g" + Long.toHexString(crc.getValue())
                + "-t" + pattern.key().replace(',', '_') + ".pdb";
    }

    public static void write(PatternDatabase db, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        PdbMetadata m = db.metadata();
        try (OutputStream os = Files.newOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(os))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(m.size());
            writeInts(out, m.goalTiles());
            writeInts(out, m.patternTiles());
            out.writeUTF(m.costModel());
            out.writeUTF(m.builderVersion());
            out.writeLong(m.buildMillis());
            out.writeInt(m.entries());
            out.writeLong(m.checksum());
            out.write(db.table());
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static PatternDatabase read(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(is))) {
            if (in.readInt() != MAGIC) throw new IOException("Không phải file PDB");
            int version = in.readInt();
            if (version != FORMAT_VERSION) throw new IOException("Phiên bản định dạng không hỗ trợ: " + version);
            int size = in.readInt();
            int[] goal = readInts(in);
            int[] pattern = readInts(in);
            String costModel = in.readUTF();
            String builder = in.readUTF();
            long buildMillis = in.readLong();
            int entries = in.readInt();
            long checksum = in.readLong();
            if (entries <= 0 || entries > PatternDatabaseBuilder.MAX_STATES) throw new IOException("Số entry không hợp lệ");
            byte[] table = new byte[entries];
            in.readFully(table);
            if (PatternDatabase.checksum(table) != checksum) {
                throw new IOException("Checksum không khớp - file bị hỏng hoặc bị sửa");
            }
            PdbMetadata meta = new PdbMetadata(size, goal, pattern, costModel, builder, entries, checksum, buildMillis);
            return new PatternDatabase(meta, table);
        }
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values.length);
        for (int v : values) out.writeInt(v);
    }

    private static int[] readInts(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 1024) throw new IOException("Độ dài mảng không hợp lệ");
        int[] values = new int[len];
        for (int i = 0; i < len; i++) values[i] = in.readInt();
        return values;
    }
}
