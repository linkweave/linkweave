package ch.dvbern.dvbstarter.i18n.translations;

import ch.dvbern.oss.commons.i18nl10n.AppLanguage;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@RequestScoped
public class FromRequestAppLanguageProvider implements AppLanguageProvider {

    private final HttpAcceptLanguageRequestFilter filter;

    @Inject
    public FromRequestAppLanguageProvider(HttpAcceptLanguageRequestFilter filter) {
        this.filter = filter;
    }

    @Override
    @Produces
    @Default
    public AppLanguage current() {
        var result = filter.getAppLanguage()
            .orElse(AppLanguages.DEFAULT);

        return result;
    }
}
