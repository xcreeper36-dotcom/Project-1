import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** UNIX-like shell emulator for configuration management variant 23. */
public class Main {
    private static final int DEFAULT_TAIL = 10;
    private final Map<String, byte[]> files = new TreeMap<>();
    private final Set<String> dirs = new TreeSet<>();
    private String cwd = "/";
    private boolean running = true;

    public Main() { dirs.add("/"); }

    /** Parses quoted arguments and simple backslash escapes. */
    static List<String> parse(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean active = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (next == quote || next == '"' || next == '\'' || next == '\\' ||
                        (quote == 0 && Character.isWhitespace(next))) {
                    token.append(next);
                    i++;
                    active = true;
                    continue;
                }
            }
            if (quote != 0) {
                if (c == quote) quote = 0;
                else token.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                active = true;
            } else if (Character.isWhitespace(c)) {
                if (active) { result.add(token.toString()); token.setLength(0); active = false; }
            } else { token.append(c); active = true; }
        }
        if (quote != 0) throw new IllegalArgumentException("unclosed quote");
        if (active) result.add(token.toString());
        return result;
    }

    private String path(String value) {
        Deque<String> parts = new ArrayDeque<>();
        String full = value.startsWith("/") ? value : cwd + "/" + value;
        for (String part : full.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }

    private static String parent(String path) {
        int index = path.lastIndexOf('/');
        return index == 0 ? "/" : path.substring(0, index);
    }

    /** Loads ZIP entries into RAM without extracting them to disk. */
    void load(Path archive) throws IOException {
        if (!Files.isRegularFile(archive)) throw new IOException("VFS not found: " + archive);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || Arrays.asList(name.split("/")).contains("..")) {
                    throw new IOException("unsafe ZIP path: " + name);
                }
                String normalized = path("/" + name);
                if (entry.isDirectory()) addParents(normalized);
                else {
                    addParents(parent(normalized));
                    try (InputStream input = zip.getInputStream(entry)) {
                        files.put(normalized, input.readAllBytes());
                    }
                }
            }
        }
    }

    private void addParents(String dir) {
        dirs.add("/");
        if (dir.equals("/")) return;
        String current = "";
        for (String segment : dir.substring(1).split("/")) {
            current += "/" + segment;
            dirs.add(current);
        }
    }

    private void requireCount(List<String> args, int min, int max) {
        if (args.size() < min || args.size() > max) {
            throw new IllegalArgumentException("invalid argument count");
        }
    }

    private String contents(String name) {
        String target = path(name);
        if (!files.containsKey(target)) throw new IllegalArgumentException("file not found: " + name);
        return new String(files.get(target), StandardCharsets.UTF_8);
    }

    private void ls(List<String> args) {
        requireCount(args, 1, 2);
        String target = path(args.size() == 2 ? args.get(1) : ".");
        if (files.containsKey(target)) { System.out.println(target.substring(target.lastIndexOf('/') + 1)); return; }
        if (!dirs.contains(target)) throw new IllegalArgumentException("directory not found: " + target);
        Set<String> names = new TreeSet<>();
        for (String dir : dirs) if (!dir.equals(target) && parent(dir).equals(target)) {
            names.add(dir.substring(dir.lastIndexOf('/') + 1) + "/");
        }
        for (String file : files.keySet()) if (parent(file).equals(target)) {
            names.add(file.substring(file.lastIndexOf('/') + 1));
        }
        names.forEach(System.out::println);
    }

    private void cd(List<String> args) {
        requireCount(args, 1, 2);
        String target = path(args.size() == 2 ? args.get(1) : "/");
        if (!dirs.contains(target)) throw new IllegalArgumentException("directory not found: " + target);
        cwd = target;
    }

    private void rev(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("usage: rev TEXT or rev FILE");
        String value = args.size() == 2 && files.containsKey(path(args.get(1)))
                ? contents(args.get(1)) : String.join(" ", args.subList(1, args.size()));
        for (String line : value.split("\\R", -1)) {
            System.out.println(new StringBuilder(line).reverse());
        }
    }

    private void tail(List<String> args) {
        requireCount(args, 2, 4);
        int count = DEFAULT_TAIL;
        String filename;
        if (args.size() == 4 && args.get(1).equals("-n")) {
            count = Integer.parseInt(args.get(2));
            filename = args.get(3);
        } else if (args.size() == 2) filename = args.get(1);
        else throw new IllegalArgumentException("usage: tail [-n NUMBER] FILE");
        if (count < 0) throw new IllegalArgumentException("line count must be nonnegative");
        String content = contents(filename);
        String[] lines = content.split("\\R", -1);
        int length = lines.length;
        if (content.endsWith("\n") || content.endsWith("\r")) length--;
        for (int i = Math.max(0, length - count); i < length; i++) System.out.println(lines[i]);
    }

    private void mkdir(List<String> args) {
        requireCount(args, 2, 2);
        String target = path(args.get(1));
        if (dirs.contains(target) || files.containsKey(target)) {
            throw new IllegalArgumentException("already exists: " + target);
        }
        if (!dirs.contains(parent(target))) throw new IllegalArgumentException("parent directory missing");
        dirs.add(target);
    }

    private void rm(List<String> args) {
        requireCount(args, 2, 2);
        String target = path(args.get(1));
        if (files.remove(target) != null) return;
        if (!dirs.contains(target)) throw new IllegalArgumentException("not found: " + target);
        if (target.equals("/")) throw new IllegalArgumentException("cannot remove root");
        for (String dir : dirs) if (parent(dir).equals(target)) {
            throw new IllegalArgumentException("directory not empty");
        }
        for (String file : files.keySet()) if (parent(file).equals(target)) {
            throw new IllegalArgumentException("directory not empty");
        }
        dirs.remove(target);
    }

    /** Executes a command; reports errors without terminating the REPL. */
    boolean execute(String line) {
        try {
            List<String> args = parse(line);
            if (args.isEmpty()) return true;
            switch (args.get(0)) {
                case "ls" -> ls(args);
                case "cd" -> cd(args);
                case "rev" -> rev(args);
                case "tail" -> tail(args);
                case "mkdir" -> mkdir(args);
                case "rm" -> rm(args);
                case "exit" -> { requireCount(args, 1, 1); running = false; }
                default -> throw new IllegalArgumentException("unknown command: " + args.get(0));
            }
            return true;
        } catch (IllegalArgumentException ex) {
            System.err.println("error: " + ex.getMessage());
            return false;
        }
    }

    private String prompt() {
        String user = System.getProperty("user.name", "user");
        String host = System.getenv().getOrDefault("COMPUTERNAME", "localhost");
        try { host = InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { /* fallback to environment hostname */ }
        return user + "@" + host + ":" + (cwd.equals("/") ? "~" : cwd) + "$ ";
    }

    private void script(Path file) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!running) break;
            System.out.println(prompt() + line);
            execute(line);
        }
    }

    public static void main(String[] arguments) {
        try {
            String archive = null;
            String startup = null;
            for (int i = 0; i < arguments.length; i++) {
                if (i + 1 >= arguments.length) throw new IllegalArgumentException("missing option value");
                switch (arguments[i]) {
                    case "--vfs" -> archive = arguments[++i];
                    case "--script" -> startup = arguments[++i];
                    default -> throw new IllegalArgumentException("unknown option: " + arguments[i]);
                }
            }
            System.out.println("configuration: vfs=" + archive + ", script=" + startup);
            Main shell = new Main();
            if (archive != null) shell.load(Path.of(archive));
            if (startup != null) shell.script(Path.of(startup));
            Scanner input = new Scanner(System.in, StandardCharsets.UTF_8);
            while (shell.running) {
                System.out.print(shell.prompt());
                if (!input.hasNextLine()) break;
                shell.execute(input.nextLine());
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("startup error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
