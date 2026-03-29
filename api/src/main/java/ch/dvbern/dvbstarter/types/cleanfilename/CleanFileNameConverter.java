package ch.dvbern.dvbstarter.types.cleanfilename;


import ch.dvbern.dvbstarter.types.GenericStringConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public class CleanFileNameConverter extends GenericStringConverter<CleanFileName> {

    public CleanFileNameConverter() {
        super(CleanFileNameConverter::toExternalForm, CleanFileNameConverter::fromString);
    }

    private static @Nullable String toExternalForm(@Nullable CleanFileName fileName) {
        return fileName == null
            ? null
            : fileName.getFileName();
    }

    private static @Nullable CleanFileName fromString(@Nullable String s) {
        return s == null
            ? null
            : CleanFileName.parse(s);
    }


}
