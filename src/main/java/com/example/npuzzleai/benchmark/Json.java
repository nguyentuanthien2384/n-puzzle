package com.example.npuzzleai.benchmark;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Bộ ghi JSON tối giản (không thêm dependency): Map, Collection, mảng, chuỗi, số, boolean, null. */
public final class Json {
    private Json() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Double d) {
            sb.append(Double.isFinite(d) ? String.format(Locale.ROOT, "%.6g", d) : "null");
        } else if (v instanceof Float f) {
            write(sb, (double) f, indent);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                pad(sb, indent + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                if (++i < map.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            writeArray(sb, c.toArray(), indent);
        } else if (v instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(a[i]);
            }
            sb.append(']');
        } else if (v instanceof double[] a) {
            Object[] boxed = new Object[a.length];
            for (int i = 0; i < a.length; i++) boxed[i] = a[i];
            writeArray(sb, boxed, indent);
        } else if (v instanceof Object[] a) {
            writeArray(sb, a, indent);
        } else {
            quote(sb, v.toString());
        }
    }

    private static void writeArray(StringBuilder sb, Object[] items, int indent) {
        if (items.length == 0) {
            sb.append("[]");
            return;
        }
        boolean simple = true;
        for (Object o : items) if (o instanceof Map || o instanceof Collection || o instanceof Object[]) simple = false;
        if (simple) {
            sb.append('[');
            for (int i = 0; i < items.length; i++) {
                if (i > 0) sb.append(", ");
                write(sb, items[i], indent);
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < items.length; i++) {
            pad(sb, indent + 1);
            write(sb, items[i], indent + 1);
            if (i < items.length - 1) sb.append(',');
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append(']');
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }

    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
