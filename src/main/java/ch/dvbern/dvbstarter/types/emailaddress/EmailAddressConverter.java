package ch.dvbern.dvbstarter.types.emailaddress;

import ch.dvbern.dvbstarter.shared.types.GenericStringConverter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public class EmailAddressConverter extends GenericStringConverter<EmailAddress> {

    public EmailAddressConverter() {
        super(EmailAddressConverter::toExternalForm, EmailAddressConverter::fromString);
    }

    public static void registerJackson(
        SimpleModule module
    ) {
        GenericStringConverter.registerJackson(
            module,
            EmailAddress.class,
            EmailAddressConverter::toExternalForm,
            EmailAddressConverter::fromString
        );
    }

    private static @Nullable String toExternalForm(@Nullable EmailAddress email) {
        return email == null
            ? null
            : email.getAddress();
    }

    private static @Nullable EmailAddress fromString(@Nullable String string) {
        return string == null
            ? null
            : EmailAddress.fromString(string);
    }
}
