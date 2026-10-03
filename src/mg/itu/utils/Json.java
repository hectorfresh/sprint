package mg.itu.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

public class Json {

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        write(sb, obj);
        return sb.toString();
    }

private static void write(StringBuilder sb, Object obj) {
    if (obj == null) { sb.append("null"); return; }

    if (obj instanceof String)  { writeString(sb, (String) obj); return; }
    if (obj instanceof Number || obj instanceof Boolean) {
        sb.append(obj.toString());
        return;
    }
    if (obj instanceof Map)         { writeMap(sb, (Map<?, ?>) obj); return; }
    if (obj instanceof Collection)  { writeCollection(sb, (Collection<?>) obj); return; }
    if (obj.getClass().isArray())   { writeArray(sb, obj); return; }

    // Court-circuit pour les types JDK (java.*, javax.*, jdk.*, sun.*)
    // et les enums → toString() au lieu de récursion infinie
    Class<?> clazz = obj.getClass();
    if (clazz.isEnum()) {
        writeString(sb, obj.toString());
        return;
    }
    Package pkg = clazz.getPackage();
    String pkgName = (pkg == null) ? "" : pkg.getName();
    if (pkgName.startsWith("java.")
     || pkgName.startsWith("javax.")
     || pkgName.startsWith("jdk.")
     || pkgName.startsWith("sun.")) {
        writeString(sb, obj.toString());
        return;
    }

    writePojo(sb, obj);
}
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeCollection(StringBuilder sb, Collection<?> col) {
        sb.append('[');
        boolean first = true;
        for (Object o : col) {
            if (!first) sb.append(',');
            first = false;
            write(sb, o);
        }
        sb.append(']');
    }

    private static void writeArray(StringBuilder sb, Object arr) {
        sb.append('[');
        int len = Array.getLength(arr);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            write(sb, Array.get(arr, i));
        }
        sb.append(']');
    }

    private static void writePojo(StringBuilder sb, Object obj) {
        sb.append('{');
        boolean first = true;
        for (Method m : obj.getClass().getMethods()) {
            String name = m.getName();
            if (!(name.startsWith("get") || name.startsWith("is"))) continue;
            if (name.equals("getClass")) continue;
            if (m.getParameterCount() != 0) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (!Modifier.isPublic(m.getModifiers())) continue;

            String key;
            if (name.startsWith("get")) {
                key = name.substring(3);
            } else {
                key = name.substring(2);
            }
            if (key.isEmpty()) continue;
            key = Character.toLowerCase(key.charAt(0)) + key.substring(1);

            try {
                Object value = m.invoke(obj);
                if (!first) sb.append(',');
                first = false;
                writeString(sb, key);
                sb.append(':');
                write(sb, value);
            } catch (Exception ignored) { }
        }
        sb.append('}');
    }
}