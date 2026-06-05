import java.io.*;
import java.util.*;

public class BencodeParser {

    private final byte[] data;
    private int position;
    private int infoStart = -1;
    private int infoEnd = -1;

    public BencodeParser(byte[] data) {
        this.data = data;
        this.position = 0;
    }

    public int getInfoStart() {
        return infoStart;
    }

    public int getInfoEnd() {
        return infoEnd;
    }

    // main entry point — parse whatever is at current position
    public Object parse() throws Exception {
        char current = (char) data[position];

        if (current == 'i')
            return parseInteger();
        if (current == 'l')
            return parseList();
        if (current == 'd')
            return parseDictionary();
        if (Character.isDigit(current))
            return parseString();

        throw new Exception("Unknown bencode type at position "
                + position + ": " + current);
    }

    // parse integer — i42e → 42
    private long parseInteger() throws Exception {
        position++; // skip 'i'
        StringBuilder sb = new StringBuilder();
        while ((char) data[position] != 'e') {
            sb.append((char) data[position]);
            position++;
        }
        position++; // skip 'e'
        return Long.parseLong(sb.toString());
    }

    // parse string — 4:spam → "spam"
    private byte[] parseString() throws Exception {
        // read length digits before colon
        StringBuilder lengthStr = new StringBuilder();
        while ((char) data[position] != ':') {
            lengthStr.append((char) data[position]);
            position++;
        }
        position++; // skip ':'

        int length = Integer.parseInt(lengthStr.toString());
        byte[] bytes = new byte[length];
        System.arraycopy(data, position, bytes, 0, length);
        position += length;
        return bytes;
    }

    // parse list — l4:spam4:eggse → ["spam", "eggs"]
    private List<Object> parseList() throws Exception {
        position++; // skip 'l'
        List<Object> list = new ArrayList<>();
        while ((char) data[position] != 'e') {
            list.add(parse()); // recursive
        }
        position++; // skip 'e'
        return list;
    }

    // parse dictionary — d3:key5:valuee → {key: value}
    private Map<String, Object> parseDictionary() throws Exception {
        position++; // skip 'd'
        Map<String, Object> dict = new LinkedHashMap<>();
        while ((char) data[position] != 'e') {
            byte[] keyBytes = parseString(); // keys are always strings
            String key = new String(keyBytes, "UTF-8");
            if (key.equals("info")) {
                infoStart = position;
                Object value = parse();
                infoEnd = position;
                dict.put(key, value);
            } else {
                Object value = parse(); // recursive
                dict.put(key, value);
            }
        }
        position++; // skip 'e'
        return dict;
    }

    // convenience method — parse a string value as UTF-8 text
    public static String getString(Map<String, Object> dict,
            String key) throws Exception {
        byte[] bytes = (byte[]) dict.get(key);
        if (bytes == null)
            return null;
        return new String(bytes, "UTF-8");
    }

    // convenience method — get integer value
    public static long getLong(Map<String, Object> dict,
            String key) {
        Object val = dict.get(key);
        if (val == null)
            return 0;
        return (long) val;
    }

    // get current position — used for info hash extraction
    public int getPosition() {
        return position;
    }
}