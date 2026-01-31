package ch.dvbern.dvbstarter.types.emailaddress;

import java.io.Serializable;

import org.chainlink.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.openapi.OpenApiConst.Format;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.jspecify.annotations.Nullable;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import static org.chainlink.infrastructure.errorhandling.AppValidationMessage.invalidEmail;
import static java.util.Objects.requireNonNull;

@Getter
@Schema(type = SchemaType.STRING, format = Format.EMAIL)
@Slf4j
public class EmailAddress implements Serializable {

    private static final long serialVersionUID = -2872872456003785391L;

    private final String address;

    protected EmailAddress(String address) {
        this.address = address;
    }

    public static EmailAddress fromString(String s) {
        requireNonNull(s);

        return parse(s);
    }

    static EmailAddress parse(String input) {
        String normalized = StringUtils.normalizeSpace(input);
        if (!EmailValidator.getInstance(true).isValid(normalized)) {
            LOG.warn("Invalid email address: {}", input);
            throw new AppValidationException(invalidEmail(input));
        }

        return new EmailAddress(normalized);
    }

    @Override
    public String toString() {
        return address;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmailAddress that)) {
            return false;
        }

        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}
