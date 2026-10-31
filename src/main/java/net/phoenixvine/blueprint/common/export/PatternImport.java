package net.phoenixvine.blueprint.common.export;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a multiblock pattern written as GregTech KubeJS or Java: the {@code .slice(...)} / {@code .aisle(...)} /
 * {@code .sliceRepeatable(...)} lines and the {@code .where(symbol, predicate)} lines, whether our own output or hand-written.
 * It does not know about blocks: each predicate is only classified (a block by id, a block by registry field, an ability, the
 * controller, air, anything), and {@link ImportResolver} turns that into blocks.
 */
public final class PatternImport {

    public enum Kind {
        CONTROLLER,
        AIR,
        ANY,
        /** A block by its id; {@code a} is the id. */
        BLOCK,
        /** A block by a registry field such as {@code GTBlocks.CASING_STEEL_SOLID}; {@code a} is the class (or null), {@code b} the field. */
        FIELD,
        /** Hatches and buses by ability; {@code a} is the first PartAbility constant. */
        ABILITY,
        /** A custom predicate method such as {@code PhoenixFissionPredicates.fissionModerators()}: {@code a} is the class (or null), {@code b} the method. */
        CALL,
        UNKNOWN
    }

    public record Pred(Kind kind, @Nullable String a, @Nullable String b) {

        static Pred of(Kind kind) {
            return new Pred(kind, null, null);
        }
    }

    /** What was read: the slices (each a list of rows), the predicate of every symbol, and what could not be understood. */
    public record Layout(List<List<String>> slices, Map<Character, Pred> symbols, List<String> problems,
                         Map<Character, String> raws) {}

    private static final Pattern CALL = Pattern.compile("\\.(sliceRepeatable|slice|aisle|where)\\s*\\(");
    private static final Pattern REPEAT_PREFIX = Pattern.compile("^\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,");

    /** At most this many slices are read after repeats are counted. */
    private static final int MAX_SLICES = 512;

    private PatternImport() {}

    public static Layout parse(String raw) {
        String text = stripComments(raw);
        List<List<String>> slices = new ArrayList<>();
        Map<Character, Pred> symbols = new LinkedHashMap<>();
        Map<Character, String> raws = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();

        Matcher m = CALL.matcher(text);
        int from = 0;
        while (m.find(from)) {
            int open = m.end() - 1;
            int close = closing(text, open);
            if (close < 0) {
                problems.add("unbalanced brackets after ." + m.group(1));
                break;
            }
            String args = text.substring(open + 1, close);
            from = close + 1;
            switch (m.group(1)) {
                case "where" -> readWhere(args, symbols, raws, problems);
                case "sliceRepeatable" -> {
                    Matcher p = REPEAT_PREFIX.matcher(args);
                    int repeats = 1;
                    String rest = args;
                    if (p.find()) {
                        repeats = Math.max(1, Integer.parseInt(p.group(1)));
                        rest = args.substring(p.end());
                    }
                    List<String> rows = strings(rest);
                    for (int i = 0; i < repeats && slices.size() < MAX_SLICES; i++) slices.add(rows);
                }
                default -> slices.add(strings(args));
            }
        }

        if (slices.isEmpty()) problems.add("no .slice(...) or .aisle(...) lines found");
        int rows = -1;
        int chars = -1;
        for (List<String> slice : slices) {
            if (rows < 0) rows = slice.size();
            if (slice.size() != rows) problems.add("slices have different numbers of rows");
            for (String row : slice) {
                if (chars < 0) chars = row.length();
                if (row.length() != chars) problems.add("rows have different lengths");
            }
        }
        return new Layout(slices, symbols, problems.stream().distinct().toList(), raws);
    }

    private static void readWhere(String args, Map<Character, Pred> symbols, Map<Character, String> raws,
                                  List<String> problems) {
        int comma = topLevelComma(args);
        if (comma < 0) {
            problems.add("a .where(...) without a predicate");
            return;
        }
        String symbolText = args.substring(0, comma).trim();
        String symbol = symbolText.startsWith("'") ? charLiteral(symbolText) : strings(symbolText).stream().findFirst()
                .orElse(null);
        if (symbol == null || symbol.length() != 1) {
            problems.add("a .where(...) with a symbol that is not one character: " + symbolText);
            return;
        }
        String predicate = args.substring(comma + 1).trim();
        symbols.put(symbol.charAt(0), classify(predicate));
        raws.put(symbol.charAt(0), predicate.replaceAll("\\s+", " "));
    }

    // ------------------------------------------------------------------ predicates

    private static final Pattern PART_ABILITY = Pattern.compile("PartAbility\\.(\\w+)");
    private static final Pattern STRING_ARG = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PARSE_ID = Pattern.compile("(?:fromNamespaceAndPath|withNamespaceAndPath)\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern PARSE_ONE = Pattern.compile("(?:ResourceLocation\\.parse|ResourceLocation\\.tryParse)\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern FIELD_GET = Pattern.compile("^(?:(\\w+)\\.)?(\\w+)\\.get\\(\\)");
    private static final Pattern FIELD_PLAIN = Pattern.compile("^(\\w+)\\.(\\w+)\\b");

    /** The first predicate of a symbol's expression (what follows a {@code .or(...)} is not looked at). */
    public static Pred classify(String e) {
        // Predicates.blocks(...) or, with the class statically imported, just blocks(...)
        String expr = e.trim();
        if (expr.startsWith("Predicates.")) expr = expr.substring("Predicates.".length());
        if (expr.startsWith("controller(")) return Pred.of(Kind.CONTROLLER);
        if (expr.startsWith("air()")) return Pred.of(Kind.AIR);
        if (expr.startsWith("any()")) return Pred.of(Kind.ANY);
        if (expr.startsWith("abilities(")) {
            Matcher a = PART_ABILITY.matcher(expr);
            return a.find() ? new Pred(Kind.ABILITY, a.group(1), null) : Pred.of(Kind.UNKNOWN);
        }
        if (expr.startsWith("blocks(")) {
            int open = expr.indexOf('(');
            int close = closing(expr, open);
            String inner = (close < 0 ? expr.substring(open + 1) : expr.substring(open + 1, close)).trim();
            int comma = topLevelComma(inner);
            String arg = (comma < 0 ? inner : inner.substring(0, comma)).trim();

            Matcher s = STRING_ARG.matcher(arg);
            if (s.find()) return new Pred(Kind.BLOCK, withNamespace(s.group(1)), null);
            Matcher id = PARSE_ID.matcher(arg);
            if (id.find()) return new Pred(Kind.BLOCK, id.group(1) + ":" + id.group(2), null);
            Matcher one = PARSE_ONE.matcher(arg);
            if (one.find()) return new Pred(Kind.BLOCK, withNamespace(one.group(1)), null);
            if (arg.startsWith("definition.")) return Pred.of(Kind.CONTROLLER);
            Matcher get = FIELD_GET.matcher(arg);
            if (get.find()) return new Pred(Kind.FIELD, get.group(1), get.group(2));
            Matcher plain = FIELD_PLAIN.matcher(arg);
            if (plain.find()) return new Pred(Kind.FIELD, plain.group(1), plain.group(2));
        }
        Matcher call = CUSTOM_CALL.matcher(expr);
        if (call.find() && !"Predicates".equals(call.group(1))) return new Pred(Kind.CALL, call.group(1), call.group(2));
        Matcher bare = BARE_CALL.matcher(expr);
        if (bare.find()) return new Pred(Kind.CALL, null, bare.group(1));
        return Pred.of(Kind.UNKNOWN);
    }

    private static final Pattern CUSTOM_CALL = Pattern.compile("^(\\w+)\\.(\\w+)\\(\\s*\\)");
    private static final Pattern BARE_CALL = Pattern.compile("^(\\w+)\\(\\s*\\)");

    private static String withNamespace(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    // ------------------------------------------------------------------ small text tools

    /** Removes // and /* *\/ comments, leaving strings and character literals alone. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < s.length()) out.append(s.charAt(++i));
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                if (i < s.length()) out.append('\n');
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The index of the bracket that closes the one at {@code open}, skipping strings and character literals; -1 if none. */
    static int closing(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    /** The first comma that is not inside brackets or a string; -1 if none. */
    static int topLevelComma(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    /** Every double-quoted string in the text, unescaped. */
    static List<String> strings(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '"') continue;
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length() && s.charAt(i) != '"') {
                if (s.charAt(i) == '\\' && i + 1 < s.length()) i++;
                b.append(s.charAt(i));
                i++;
            }
            out.add(b.toString());
        }
        return out;
    }

    /** The character of a literal such as {@code 'a'} or {@code '\''}; null if it is not one. */
    static @Nullable String charLiteral(String s) {
        if (s.length() < 3 || s.charAt(0) != '\'' || s.charAt(s.length() - 1) != '\'') return null;
        String inner = s.substring(1, s.length() - 1);
        if (inner.length() == 2 && inner.charAt(0) == '\\') return inner.substring(1);
        return inner.length() == 1 ? inner : null;
    }
}
