package dev.mcver.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON：解析成 Map / List / String / Long|Double / Boolean / null，
 * 写出时也吃这套结构。没有第三方依赖。
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    // ================================================================ 读

    public static Object parse(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Map<String, Object> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof String ? (String) v : def;
    }

    /** 直接取一个 JSON 标量（数组元素常用）。 */
    public static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    public static int num(Map<String, Object> m, String k, int def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static long lng(Map<String, Object> m, String k, long def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (i < s.length()) {
            ws();
            String k = string();
            ws();
            i++; // ':'
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') break;
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (i < s.length()) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') break;
        }
        return l;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++; // 开引号
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(start, i);
        if (t.isEmpty()) return 0L;
        if (t.contains(".") || t.contains("e") || t.contains("E")) {
            try { return Double.parseDouble(t); } catch (NumberFormatException e) { return 0D; }
        }
        try { return Long.parseLong(t); } catch (NumberFormatException e) {
            try { return Double.parseDouble(t); } catch (NumberFormatException e2) { return 0L; }
        }
    }

    // ================================================================ 写

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb, 0);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb, int depth) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            quote((String) o, sb);
        } else if (o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Number) {
            Number n = (Number) o;
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append((long) d);
                else sb.append(d);
            } else {
                sb.append(n.longValue());
            }
        } else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int k = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                indent(sb, depth + 1);
                quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, depth + 1);
                if (++k < m.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
        } else if (o instanceof List) {
            List<?> l = (List<?>) o;
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                indent(sb, depth + 1);
                write(l.get(k), sb, depth + 1);
                if (k + 1 < l.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else {
            quote(String.valueOf(o), sb);
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int k = 0; k < depth; k++) sb.append("  ");
    }

    private static void quote(String v, StringBuilder sb) {
        sb.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
