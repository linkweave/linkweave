package ch.dvbern.dvbstarter;

import ch.dvbern.dvbstarter.inputvalidation.StringNormalizerModule;
import ch.dvbern.dvbstarter.types.cleanfilename.CleanFileNameConverter;
import ch.dvbern.dvbstarter.types.emailaddress.EmailAddressConverter;
import ch.dvbern.dvbstarter.types.id.IDConverter;
import ch.dvbern.dvbstarter.types.semver.SemverConverter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * See <a href="https://quarkus.io/guides/rest-json#jackson">Quarkus Jackson Customization</a>
 */
@Singleton
@SuppressWarnings("unused")
public class JacksonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {

        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setConstructorDetector(ConstructorDetector.USE_PROPERTIES_BASED);

        SimpleModule module = new SimpleModule("linkweave.api");

        EmailAddressConverter.registerJackson(module);
        CleanFileNameConverter.registerJackson(module);
        IDConverter.registerJackson(module);
        SemverConverter.registerJackson(module);

        mapper.registerModule(module);
        mapper.registerModule(new StringNormalizerModule()); // all Strings will be trimmed
    }

}
