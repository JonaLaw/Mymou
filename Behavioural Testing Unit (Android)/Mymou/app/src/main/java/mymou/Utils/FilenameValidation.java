package mymou.Utils;
import java.util.Arrays;

public class FilenameValidation {
    // Taken and simplified from https://www.baeldung.com/java-validate-filename

    public static final Character[] INVALID_CHARS = {'"', '*', ':', '<', '>', '?', '\\', '|', 0x7F};

    public static boolean validateStringFilenameUsingContains(String filename) {
        if (filename == null || filename.isEmpty() || filename.length() > 255) {
            return false;
        }
        return Arrays.stream(INVALID_CHARS).noneMatch(ch -> filename.contains(ch.toString()));
    }
}
