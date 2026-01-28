package ch.dvbern.dvbstarter.types.cleanfilename;

import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;

import ch.dvbern.dvbstarter.inputvalidation.InputValidationUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

@Getter
@EqualsAndHashCode
public class CleanFileName implements Serializable {
    @Serial
    private static final long serialVersionUID = -4274317691559857419L;

    @NotEmpty
    private final String fileName;

    public CleanFileName(String fileName) {
        this.fileName = clean(fileName);
    }

    public static CleanFileName parse(String filePath) {
        return new CleanFileName(filePath);
    }

    public static CleanFileName parse(Path path) {
        return new CleanFileName(path.getFileName().toString());
    }

    private static String clean(String filePath) {
        // INFO "/" & "\" in Filename is interpreted as path separator
        String filename = FilenameUtils.getName(filePath);
        String noControls = InputValidationUtil.removeAllControlCharacters(filename);
        String windowsFileSystemSafe = InputValidationUtil.removeAllCharactersIncomatibleWithWinwdowsFileSystemNames(noControls);

        return StringUtils.trimToEmpty(windowsFileSystemSafe);
    }

    @Override
    public String toString() {
        return fileName;
    }

}
