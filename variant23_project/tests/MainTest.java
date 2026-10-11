import java.util.List;

/** Dependency-free parser regression tests. */
public class MainTest {
    public static void main(String[] args) {
        assert Main.parse("rev \"hello world\"").equals(List.of("rev", "hello world"));
        assert Main.parse("ls 'my folder'").equals(List.of("ls", "my folder"));
        assert Main.parse("rev \"\"").equals(List.of("rev", ""));
        try {
            Main.parse("rev \"broken");
            throw new AssertionError("Expected parser error");
        } catch (IllegalArgumentException expected) {
            System.out.println("All parser tests passed");
        }
    }
}
