package ch.dvbern.dvbstarter.types;

import java.io.IOException;
import java.util.function.Function;

import ch.dvbern.dvbstarter.inputvalidation.InputValidationUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.persistence.AttributeConverter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class GenericStringConverter<Value> implements AttributeConverter<Value, String> {

    private final Function<@Nullable Value, @Nullable String> toDBValue;
    private final Function<@Nullable String, @Nullable Value> fromDBValue;

    public static <Value> void registerJackson(
        SimpleModule module,
        Class<Value> clazz,
        Function<Value, String> toJSONString,
        Function<String, Value> fromJSONString
    ) {
        module.addSerializer(new GenericSerializer<>(clazz, toJSONString));
        module.addDeserializer(clazz, new GenericDeserializer<>(clazz, fromJSONString));
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Value attribute) {
        var result = toDBValue.apply(attribute);

        return result;
    }

    @Override
    public @Nullable Value convertToEntityAttribute(@Nullable String textValue) {
        try {
            var result = fromDBValue.apply(textValue);

            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException(textValue, e);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    public static class GenericSerializer<Value> extends JsonSerializer<Value> {
        private final Class<Value> clazz;
        private final Function<Value, String> toJSONString;

        @Override
        public Class<Value> handledType() {
            return clazz;
        }

        @Override
        public void serialize(Value value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
            if (value != null) {
                var textValue = toJSONString.apply(value);
                gen.writeString(textValue);
            }
        }
    }

    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    public static class GenericDeserializer<Value> extends JsonDeserializer<Value> {
        private final Class<Value> clazz;
        private final Function<String, Value> fromJSONString;

        @Override
        public Class<?> handledType() {
            return clazz;
        }

        @Nullable
        @Override
        public Value deserialize(
            JsonParser p,
            DeserializationContext ctxt
        ) throws IOException {
            String valueAsString = p.getValueAsString();
            if (valueAsString == null) {
                return null;
            }
            String textValue = trimToNull(InputValidationUtil.removeUnprintables(valueAsString));
            if (textValue == null) {
                return null;
            }
            var result = fromJSONString.apply(textValue);

            return result;
        }
    }

}
