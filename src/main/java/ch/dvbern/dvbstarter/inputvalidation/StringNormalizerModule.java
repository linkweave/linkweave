/*
 * Copyright (C) 2023 DV Bern AG, Switzerland
 *
 * Das vorliegende Dokument, einschliesslich aller seiner Teile, ist urheberrechtlich
 * geschuezt. Jede Verwertung ist ohne Zustimmung der DV Bern AG unzulaessig. Dies gilt
 * insbesondere für Vervielfaeltigungen, die Einspeicherung und Verarbeitung in
 * elektronischer Form. Wird das Dokument einem Kunden im Rahmen der Projektarbeit zur
 * Ansicht uebergeben, ist jede weitere Verteilung durch den Kunden an Dritte untersagt.
 */

package ch.dvbern.dvbstarter.inputvalidation;

import java.io.IOException;
import java.io.Serial;

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
 * This module trims Strings and removes unprintable characters from them.
 */
public class StringNormalizerModule extends SimpleModule {
    @Serial
    private static final long serialVersionUID = 6319383204609406456L;

    public StringNormalizerModule() {
        super();
        addDeserializer(String.class, new TrimmingStringDeserializer());
        addSerializer(String.class, new Serializer());
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
            gen.writeString(InputValidationUtil.removeUnprintables(value));
        }

    }

    public static class TrimmingStringDeserializer extends StringDeserializer {

        @Serial
        private static final long serialVersionUID = -6840658188445739027L;

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = super.deserialize(p, ctxt);
            String trimmed = value != null ? value.strip() : null;
            return InputValidationUtil.removeUnprintables(trimmed);
        }
    }

}
