package ch.dvbern.dvbstarter.i18n.translations;

import ch.dvbern.oss.commons.i18nl10n.AppLanguage;
import com.ibm.icu.util.ULocale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Keep in Sync with config "quarkus.locales"!!!
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum AppLanguages implements AppLanguage {
    DE(ULocale.forLanguageTag("de-CH")),
    FR(ULocale.forLanguageTag("fr-CH")),
    IT(ULocale.forLanguageTag("it-CH")),
    EN(ULocale.forLanguageTag("en-US")),
    ;

    public static final AppLanguage DEFAULT = AppLanguages.DE;

    private final ULocale locale;

}
