package br.com.simplelauncher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String text;
    private int index;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.index != parser.text.length()) {
            throw new IllegalArgumentException("JSON contains extra data.");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return (List<Object>) value;
    }

    static String string(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value == null ? "" : value.toString();
    }

    private Object value() {
        skipWhitespace();
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (text.startsWith("true", index)) { index += 4; return Boolean.TRUE; }
        if (text.startsWith("false", index)) { index += 5; return Boolean.FALSE; }
        if (text.startsWith("null", index)) { index += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') { index++; return result; }
        while (true) {
            String key = string();
            expect(':');
            result.put(key, value());
            skipWhitespace();
            char c = peek();
            if (c == '}') { index++; return result; }
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> result = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') { index++; return result; }
        while (true) {
            result.add(value());
            skipWhitespace();
            char c = peek();
            if (c == ']') { index++; return result; }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') return result.toString();
            if (c == '\\') {
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        String hex = text.substring(index, index + 4);
                        result.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                        break;
                    default: throw new IllegalArgumentException("Invalid JSON escape: " + escaped);
                }
            } else {
                result.append(c);
            }
        }
        throw new IllegalArgumentException("Unclosed JSON string.");
    }

    private Number number() {
        int start = index;
        while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) index++;
        String raw = text.substring(start, index);
        if (raw.contains(".") || raw.contains("e") || raw.contains("E")) return Double.valueOf(raw);
        return Long.valueOf(raw);
    }

    private void skipWhitespace() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
    }

    private char peek() {
        if (index >= text.length()) throw new IllegalArgumentException("JSON ended earlier than expected.");
        return text.charAt(index);
    }

    private void expect(char expected) {
        skipWhitespace();
        char actual = peek();
        if (actual != expected) throw new IllegalArgumentException("Expected '" + expected + "', found '" + actual + "'.");
        index++;
    }
}
