package ch.dvbern.dvbstarter.types.semver;

import ch.dvbern.dvbstarter.types.GenericStringConverter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vdurmont.semver4j.Semver;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SemverConverter {

    public static void registerJackson(
        SimpleModule module
    ) {
        GenericStringConverter.registerJackson(
            module,
            Semver.class,
            Semver::toString,
            Semver::new
        );
    }
}
