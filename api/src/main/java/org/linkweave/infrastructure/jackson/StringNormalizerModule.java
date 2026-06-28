package org.linkweave.infrastructure.jackson;

import java.io.IOException;
import java.io.Serial;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Jackson module that normalizes every {@link String} field crossing the JSON
 * boundary: inbound values are trimmed and stripped of unprintable control
 * characters; outbound values are stripped of unprintable control characters.
 * <p>
 * Registering this globally (see {@link LinkweaveJacksonCustomizer}) means callers
 * cannot smuggle leading/trailing whitespace or control characters into persisted
 * free-text fields (bookmark titles, tag/folder names, search queries, ...).
 */
public class StringNormalizerModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 6319383204609406456L;

    /**
     * All ASCII control characters (0x00-0x1F and 0x7F) <em>except</em> the printable
     * whitespace ones we want to keep: Tab (0x09), LF (0x0A) and CR (0x0D).
     */
    private static final Pattern UNPRINTABLE_CONTROL_CHARACTERS = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    public StringNormalizerModule() {
        super("linkweave.string-normalizer");
        addDeserializer(String.class, new TrimmingStringDeserializer());
        addSerializer(String.class, new Serializer());
    }

    /**
     * Remove all control characters except the printable ones (Tab, LF, CR).
     * Returns {@code null} for {@code null} input.
     */
    private static @Nullable String removeUnprintables(@Nullable String input) {
        if (input == null) {
            return null;
        }
        return UNPRINTABLE_CONTROL_CHARACTERS.matcher(input).replaceAll("");
    }

    public static class Serializer extends StdSerializer<String> {

        @Serial
        private static final long serialVersionUID = 3922865608437835627L;

        protected Serializer() {
            super(String.class);
        }

        @Override
        public void serialize(
            @Nullable String value, @NonNull JsonGenerator gen, SerializerProvider serializers
        ) throws IOException {
            gen.writeString(removeUnprintables(value));
        }
    }

    public static class TrimmingStringDeserializer extends StringDeserializer {

        @Serial
        private static final long serialVersionUID = -6840658188445739027L;

        @Override
        public @Nullable String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = super.deserialize(p, ctxt);
            if (value == null) {
                return null;
            }
            return removeUnprintables(value.strip());
        }
    }
}
