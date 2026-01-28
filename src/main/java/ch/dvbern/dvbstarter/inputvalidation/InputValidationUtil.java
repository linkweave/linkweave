package ch.dvbern.dvbstarter.inputvalidation;

import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@UtilityClass
public final class InputValidationUtil {
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}*");
    // all Control-Characters except: Tab, LF, CR, see e.g.: http://www.asciitable.com/
    private static final Pattern UNPRINTABLE_CONTROL_CHARACTERS = Pattern.compile(
        "[\\x00\\x01\\x02\\x03\\x04\\x05\\x06\\x07\\x08" /* tab: 0x09 */
            + /* LF: 0x0A*/ "\\x0B\\x0C" +/* CR: 0x0D*/ "\\x0E\\x0F"
            + "\\x10\\x11\\x12\\x13\\x14\\x15\\x16\\x17\\x18\\x19"
            + "\\x1A\\x1B\\x1C\\x1D\\x1E\\x1F"
            + "\\x7F]*");

    private static final Pattern INVALID_WINDOWS_FILENAME_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|]");

    /**
     * Remove all Control-Characters (0x00-0X1F, 0x7F)
     * <p>
     * See e.g.: <a href="http://www.asciitable.com/">ASCII-Table</a>.
     * </p>
     */
    public static String removeAllControlCharacters(CharSequence input) {
        return CONTROL_CHARACTERS.matcher(input)
            .replaceAll("");
    }

    /**
     * Remove all Control-Characters except the printable ones: Tab, LF, CR.
     * <p>
     * See e.g.: <a href="http://www.asciitable.com/">ASCII-Table</a>.
     * </p>
     */
    @NonNull
    public static String removeUnprintables(@Nullable CharSequence input) {
        if (input == null) {
            return "";
        }
        return UNPRINTABLE_CONTROL_CHARACTERS.matcher(input)
            .replaceAll("");
    }

    /**
     * Remove all characters that are incompatible with the Windows file system.
     * <p>
     * See e.g.:
     * <a href="https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file#naming-conventions">
     * Windows Naming Conventions
     * </a>
     * </p>
     */
    public static String removeAllCharactersIncomatibleWithWinwdowsFileSystemNames(CharSequence input) {
        return INVALID_WINDOWS_FILENAME_CHARACTERS.matcher(input).replaceAll("");
    }

}
