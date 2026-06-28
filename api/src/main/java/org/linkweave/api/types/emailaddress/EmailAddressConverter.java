package org.linkweave.api.types.emailaddress;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * JPA attribute converter (auto-applied) and Jackson (de)serializer registration
 * for {@link EmailAddress}, which is stored/transmitted as its string form.
 */
@Converter(autoApply = true)
public class EmailAddressConverter implements AttributeConverter<EmailAddress, String> {

    public static void registerJackson(SimpleModule module) {
        module.addSerializer(EmailAddress.class, new JsonSerializer<>() {
            @Override
            public void serialize(EmailAddress value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
                gen.writeString(value == null ? null : value.getAddress());
            }
        });
        module.addDeserializer(EmailAddress.class, new JsonDeserializer<>() {
            @Override
            public @Nullable EmailAddress deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String value = StringUtils.trimToNull(p.getValueAsString());
                return value == null ? null : EmailAddress.fromString(value);
            }
        });
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable EmailAddress attribute) {
        return attribute == null ? null : attribute.getAddress();
    }

    @Override
    public @Nullable EmailAddress convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : EmailAddress.fromString(dbData);
    }
}
