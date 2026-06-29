package org.linkweave.infrastructure.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import org.linkweave.api.types.emailaddress.EmailAddressConverter;
import org.linkweave.api.types.id.IDConverter;

/**
 * Registers Jackson (de)serializers for LinkWeave's custom value types
 * ({@code ID}, {@code EmailAddress}), applies common ObjectMapper settings, and
 * installs the {@link StringNormalizerModule} so all Strings are trimmed and
 * stripped of unprintable characters.
 *
 * @see <a href="https://quarkus.io/guides/rest-json#jackson">Quarkus Jackson Customization</a>
 */
@Singleton
@SuppressWarnings("unused")
public class LinkweaveJacksonCustomizer implements ObjectMapperCustomizer {
    @Override
    public void customize(ObjectMapper mapper) {
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setConstructorDetector(ConstructorDetector.USE_PROPERTIES_BASED);
        SimpleModule module = new SimpleModule("linkweave.api");
        EmailAddressConverter.registerJackson(module);
        IDConverter.registerJackson(module);
        mapper.registerModule(module);
        mapper.registerModule(new StringNormalizerModule()); // all Strings are trimmed + unprintables removed
    }
}
