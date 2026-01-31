package org.chainlink.infrastructure.errorhandling;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import ch.dvbern.dvbstarter.openapi.OpenApiConst;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Value
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Schema(type = SchemaType.STRING, format = OpenApiConst.Format.EXCEPTION_ID)
public class ExceptionId implements Serializable {

    @Serial
    private static final long serialVersionUID = -3399892679761234356L;

    @NonNull
    @NotEmpty
    @NotNull
    @IgnoreForIdClassTest
    private final String id;

    public static ExceptionId random() {
        var id = shortUUIDString(UUID.randomUUID());

        return new ExceptionId(id);
    }

    @NonNull
    @JsonCreator
    public static ExceptionId fromString(@NonNull @IgnoreForIdClassTest String id) {
        Objects.requireNonNull(StringUtils.trimToNull(id), "id must not be null or empty");

        return new ExceptionId(id);
    }

    @NonNull
    public static ExceptionId fromUUID(@NonNull @IgnoreForIdClassTest UUID id) {
        return new ExceptionId(shortUUIDString(id));
    }

    @JsonValue
    @Override
    @NonNull
    public String toString() {
        return id;
    }

    /**
     * Shorten the string representation of a UUID.
     */
    @NonNull
    private static String shortUUIDString(@NonNull @IgnoreForIdClassTest UUID id) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(id.getMostSignificantBits());
        bb.putLong(id.getLeastSignificantBits());
        return Base64.getMimeEncoder()
            .withoutPadding()
            .encodeToString(bb.array());
    }
}
