package com.limelight.binding.input;

import android.util.SparseArray;

/**
 * Maps characters typed on the Android soft keyboard to the US-positional virtual key codes
 * that produce the same character on the HOST keyboard layout.
 *
 * Sunshine converts "normalized" VK codes into scancodes using a US-English positional table,
 * so sending VK_A to a host configured in AZERTY types a 'q'. This class performs the reverse
 * mapping: for a host in AZERTY, the character 'a' is sent as VK_Q (the physical position of
 * 'a' on an AZERTY keyboard).
 */
public class HostKeyboardLayout {
    public static final String LAYOUT_UNICODE = "unicode";
    public static final String LAYOUT_US = "us";
    public static final String LAYOUT_FR = "fr";

    private static final char GRAVE = (char) 0x60;

    // Windows virtual key codes (US positional)
    public static final int VK_BACK = 0x08, VK_TAB = 0x09, VK_RETURN = 0x0D, VK_ESCAPE = 0x1B, VK_SPACE = 0x20;
    public static final int VK_PRIOR = 0x21, VK_NEXT = 0x22, VK_END = 0x23, VK_HOME = 0x24;
    public static final int VK_LEFT = 0x25, VK_UP = 0x26, VK_RIGHT = 0x27, VK_DOWN = 0x28;
    public static final int VK_INSERT = 0x2D, VK_DELETE = 0x2E, VK_LWIN = 0x5B, VK_F1 = 0x70;
    public static final int VK_LSHIFT = 0xA0, VK_LCONTROL = 0xA2, VK_LMENU = 0xA4, VK_RMENU = 0xA5;
    public static final int VK_OEM_1 = 0xBA, VK_OEM_PLUS = 0xBB, VK_OEM_COMMA = 0xBC, VK_OEM_MINUS = 0xBD;
    public static final int VK_OEM_PERIOD = 0xBE, VK_OEM_2 = 0xBF, VK_OEM_3 = 0xC0, VK_OEM_4 = 0xDB;
    public static final int VK_OEM_5 = 0xDC, VK_OEM_6 = 0xDD, VK_OEM_7 = 0xDE, VK_OEM_102 = 0xE2;

    // Modifier bits used when typing strokes on the host
    public static final int MOD_CTRL = 1, MOD_ALT = 2, MOD_ALTGR = 4, MOD_WIN = 8, MOD_SHIFT = 16;

    /** One key press on the host, possibly with Shift and/or AltGr held. */
    public static final class Stroke {
        public final int vk;
        public final boolean shift;
        public final boolean altGr;
        /** True if this key is a dead key on the host: a space must follow to get the literal char. */
        public final boolean dead;

        Stroke(int vk, boolean shift, boolean altGr, boolean dead) {
            this.vk = vk;
            this.shift = shift;
            this.altGr = altGr;
            this.dead = dead;
        }
    }

    private final String id;
    private final SparseArray<Stroke> direct = new SparseArray<>();
    // Composed characters: char -> {dead key char, base char}
    private final SparseArray<char[]> composed = new SparseArray<>();

    private HostKeyboardLayout(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isUnicodeOnly() {
        return LAYOUT_UNICODE.equals(id);
    }

    /** @return the stroke(s) needed to type c, or null if it must be sent as UTF-8 text. */
    public Stroke[] strokesFor(char c) {
        Stroke s = direct.get(c);
        if (s != null) {
            if (s.dead) {
                // Dead key followed by space produces the literal accent character
                return new Stroke[] { s, direct.get(' ') };
            }
            return new Stroke[] { s };
        }
        char[] comp = composed.get(c);
        if (comp != null) {
            Stroke d = direct.get(comp[0]);
            Stroke b = direct.get(comp[1]);
            if (d != null && b != null) {
                return new Stroke[] { d, b };
            }
        }
        return null;
    }

    private void put(char c, int vk, boolean shift, boolean altGr, boolean dead) {
        direct.put(c, new Stroke(vk, shift, altGr, dead));
    }

    private void put(char c, int vk, boolean shift) {
        put(c, vk, shift, false, false);
    }

    private void putCommon() {
        put(' ', VK_SPACE, false);
        put('\n', VK_RETURN, false);
        put('\r', VK_RETURN, false);
        put('\t', VK_TAB, false);
    }

    public static HostKeyboardLayout forId(String id) {
        if (LAYOUT_US.equals(id)) {
            return buildUs();
        }
        else if (LAYOUT_UNICODE.equals(id)) {
            HostKeyboardLayout l = new HostKeyboardLayout(LAYOUT_UNICODE);
            // Control characters are still sent as keys
            l.putCommon();
            return l;
        }
        return buildFr();
    }

    private static HostKeyboardLayout buildUs() {
        HostKeyboardLayout l = new HostKeyboardLayout(LAYOUT_US);
        l.putCommon();
        for (char c = 'a'; c <= 'z'; c++) {
            l.put(c, 'A' + (c - 'a'), false);
            l.put(Character.toUpperCase(c), 'A' + (c - 'a'), true);
        }
        String digits = "0123456789";
        String shifted = ")!@#$%^&*(";
        for (int i = 0; i < 10; i++) {
            l.put(digits.charAt(i), '0' + i, false);
            l.put(shifted.charAt(i), '0' + i, true);
        }
        l.put(GRAVE, VK_OEM_3, false); l.put('~', VK_OEM_3, true);
        l.put('-', VK_OEM_MINUS, false); l.put('_', VK_OEM_MINUS, true);
        l.put('=', VK_OEM_PLUS, false); l.put('+', VK_OEM_PLUS, true);
        l.put('[', VK_OEM_4, false); l.put('{', VK_OEM_4, true);
        l.put(']', VK_OEM_6, false); l.put('}', VK_OEM_6, true);
        l.put('\\', VK_OEM_5, false); l.put('|', VK_OEM_5, true);
        l.put(';', VK_OEM_1, false); l.put(':', VK_OEM_1, true);
        l.put('\'', VK_OEM_7, false); l.put('"', VK_OEM_7, true);
        l.put(',', VK_OEM_COMMA, false); l.put('<', VK_OEM_COMMA, true);
        l.put('.', VK_OEM_PERIOD, false); l.put('>', VK_OEM_PERIOD, true);
        l.put('/', VK_OEM_2, false); l.put('?', VK_OEM_2, true);
        return l;
    }

    /** French AZERTY (Windows "Francais" layout), expressed as US physical positions. */
    private static HostKeyboardLayout buildFr() {
        HostKeyboardLayout l = new HostKeyboardLayout(LAYOUT_FR);
        l.putCommon();

        // Letters: same position as QWERTY except a/q, z/w and m.
        for (char c = 'a'; c <= 'z'; c++) {
            int vk = 'A' + (c - 'a');
            switch (c) {
                case 'a': vk = 'Q'; break;
                case 'q': vk = 'A'; break;
                case 'z': vk = 'W'; break;
                case 'w': vk = 'Z'; break;
                case 'm': vk = VK_OEM_1; break;
            }
            l.put(c, vk, false);
            l.put(Character.toUpperCase(c), vk, true);
        }

        // Number row: unshifted symbols, shifted digits
        String unshifted = "&\u00e9\"'(-\u00e8_\u00e7\u00e0"; // keys 1..9,0
        int[] vks = { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' };
        for (int i = 0; i < 10; i++) {
            l.put(unshifted.charAt(i), vks[i], false);
            l.put((char) vks[i], vks[i], true);
        }
        l.put('\u00b2', VK_OEM_3, false);          // superscript 2
        l.put(')', VK_OEM_MINUS, false);
        l.put('\u00b0', VK_OEM_MINUS, true);       // degree
        l.put('=', VK_OEM_PLUS, false);
        l.put('+', VK_OEM_PLUS, true);

        // AltGr on the number row
        l.put('~', '2', false, true, true);        // dead
        l.put('#', '3', false, true, false);
        l.put('{', '4', false, true, false);
        l.put('[', '5', false, true, false);
        l.put('|', '6', false, true, false);
        l.put(GRAVE, '7', false, true, true);      // dead
        l.put('\\', '8', false, true, false);
        l.put('^', VK_OEM_4, false, false, true);  // dead circumflex (key right of P)
        l.put('@', '0', false, true, false);
        l.put(']', VK_OEM_MINUS, false, true, false);
        l.put('}', VK_OEM_PLUS, false, true, false);
        l.put('\u20ac', 'E', false, true, false);  // euro

        // Top row right side
        l.put('\u00a8', VK_OEM_4, true, false, true); // diaeresis, dead
        l.put('$', VK_OEM_6, false);
        l.put('\u00a3', VK_OEM_6, true);           // pound
        l.put('\u00a4', VK_OEM_6, false, true, false);

        // Home row right side
        l.put('\u00f9', VK_OEM_7, false);          // u grave
        l.put('%', VK_OEM_7, true);
        l.put('*', VK_OEM_5, false);
        l.put('\u00b5', VK_OEM_5, true);           // micro

        // Bottom row
        l.put('<', VK_OEM_102, false);
        l.put('>', VK_OEM_102, true);
        l.put(',', 'M', false);
        l.put('?', 'M', true);
        l.put(';', VK_OEM_COMMA, false);
        l.put('.', VK_OEM_COMMA, true);
        l.put(':', VK_OEM_PERIOD, false);
        l.put('/', VK_OEM_PERIOD, true);
        l.put('!', VK_OEM_2, false);
        l.put('\u00a7', VK_OEM_2, true);           // section

        // Accented characters through dead keys
        l.putComposed('^', "a\u00e2e\u00eai\u00eeo\u00f4u\u00fbA\u00c2E\u00caI\u00ceO\u00d4U\u00db");
        l.putComposed('\u00a8', "a\u00e4e\u00ebi\u00efo\u00f6u\u00fcy\u00ffA\u00c4E\u00cbI\u00cfO\u00d6U\u00dc");
        l.putComposed('~', "n\u00f1o\u00f5a\u00e3N\u00d1O\u00d5A\u00c3");
        l.putComposed(GRAVE, "a\u00e0e\u00e8i\u00eco\u00f2u\u00f9A\u00c0E\u00c8I\u00ccO\u00d2U\u00d9");
        return l;
    }

    private void putComposed(char dead, String pairs) {
        for (int i = 0; i + 1 < pairs.length(); i += 2) {
            char base = pairs.charAt(i);
            char result = pairs.charAt(i + 1);
            if (direct.get(result) == null) {
                composed.put(result, new char[] { dead, base });
            }
        }
    }
}
