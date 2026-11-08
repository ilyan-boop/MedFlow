package medflow.io;

import java.util.ArrayList;

/** Small CSV helper. Values with commas, quotes, or line breaks are wrapped in quotes. */
public final class Csv {
    private Csv() { }

    public static String escape(String value) {
        if (value == null) value = "";
        boolean needsQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') { needsQuotes = true; break; }
        }
        if (!needsQuotes) return value;
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') sb.append("\"\"");
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    public static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values[i]));
        }
        return sb.toString();
    }

    /** Splits one CSV line into fields, reading each character once. */
    public static ArrayList<String> split(String line) {
        ArrayList<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
