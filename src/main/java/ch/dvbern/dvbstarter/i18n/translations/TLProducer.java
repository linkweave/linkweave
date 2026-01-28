package ch.dvbern.dvbstarter.i18n.translations;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.concurrent.ConcurrentHashMap;

import ch.dvbern.oss.commons.i18nl10n.AppLanguage;
import ch.dvbern.oss.commons.i18nl10n.DefaultTranslatorStrategy;
import ch.dvbern.oss.commons.i18nl10n.Translator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

@ApplicationScoped
public class TLProducer {

    private final ConcurrentHashMap<AppLanguage, ResourceBundle> bundleMap = new ConcurrentHashMap<>();
    private final AppLanguageProvider appLanguageProvider;
    private final String baseName;

    @Inject
    public TLProducer(
        AppLanguageProvider appLanguageProvider
    ) {
        this(
            requireNonNull(appLanguageProvider),
            "translations"
        );
    }

    TLProducer(
        AppLanguageProvider appLanguageProvider,
        String baseName
    ) {
        this.appLanguageProvider = requireNonNull(appLanguageProvider);
        this.baseName = baseName;
    }

    public static TLProducer forTesting(
        AppLanguageProvider provider,
        String baseName
    ) {
        return new TLProducer(provider, baseName);
    }

    // should not need the @RequestScoped annotation!
    // but since the FromRequestAppLanguageProvider is @RequestScoped
    @Produces
    @RequestScoped
    public TL produceTL() {
        var appLanguage = appLanguageProvider.current();

        return forAppLanguage(appLanguage);
    }

    public TL forAppLanguage(AppLanguage appLanguage) {
        Translator translator = DefaultTranslatorStrategy.createDefault(
            appLanguage,
            this::loadBundle
        );

        return translator::translate;
    }

    ResourceBundle loadBundle(AppLanguage appLanguage) {
        return bundleMap.computeIfAbsent(appLanguage, this::initBundle);
    }

    ResourceBundle initBundle(AppLanguage appLanguage) {
        ResourceBundle bundle = ResourceBundle.getBundle(
            baseName,
            appLanguage.javaLocale(),
            requireNonNull(currentThread().getContextClassLoader()),
            new FallbackToBaseBundleControl()
        );

        return bundle;
    }

    private static class FallbackToBaseBundleControl extends Control {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            if (locale.equals(Locale.ROOT)) {
                throw new IllegalStateException(
                    ("No fallback resource bundle found for baseName: %s. The default bundle-file must be named: "
                        + "%s.properties without any locale suffix")
                        .formatted(baseName, baseName)
                );
            }

            return Locale.ROOT;
        }
    }
}
