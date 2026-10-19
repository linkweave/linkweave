package org.linkweave.api.shared.archunit;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.linkweave.infrastructure.stereotypes.Port;
import org.linkweave.api.shared.archunit.predicates.ClassPredicates;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.linkweave.api.shared.archunit.ArchConst.APP_CLASSES;

/**
 * Ports &amp; adapters rules.
 *
 * <p>A {@link Port} is an interface the application defines for something it needs
 * from outside; the adapter that implements it is the only code allowed to know
 * which technology answers. These rules keep that seam from quietly closing again:
 * {@code ScreenshotFetcherService} used to handle {@code jakarta.ws.rs.core.Response}
 * itself, and nothing in the build noticed, because the layering rules classify by
 * name suffix and it was called {@code ...Service}.
 *
 * <p>Only outbound (driven) ports are covered. Inbound boundaries are the public
 * API of a service class and have no interface to annotate.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PortsAndAdaptersTest {

    /** Packages a port may never name: naming one puts the outside world in its signature. */
    private static final String[] OUTSIDE_PACKAGES = {
        "jakarta.ws.rs..",
        "jakarta.persistence..",
        "java.nio.file..",
        "org.eclipse.microprofile.rest.client..",
    };

    private static final DescribedPredicate<JavaClass> PORT =
        DescribedPredicate.describe("ports", clazz -> clazz.isInterface() && clazz.isAnnotatedWith(Port.class));

    /** An adapter is whatever implements a port — it needs no annotation of its own. */
    private static final DescribedPredicate<JavaClass> ADAPTER =
        DescribedPredicate.describe("adapters", clazz -> !clazz.isInterface() && implementsAPort(clazz));

    private static final DescribedPredicate<JavaClass> SERVICE = ClassPredicates.service();

    private static final DescribedPredicate<JavaClass> REST_CLIENT =
        annotatedWith(RegisterRestClient.class).forSubtype();

    private static boolean implementsAPort(JavaClass clazz) {
        return clazz.getAllRawInterfaces().stream().anyMatch(i -> i.isAnnotatedWith(Port.class));
    }

    @Test
    void ports_are_interfaces() {
        ArchRule rule = classes().that()
            .areAnnotatedWith(Port.class)
            .should()
            .beInterfaces()
            .because("a port is a contract the application owns, not an implementation");

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void ports_do_not_name_the_outside_world() {
        ArchRule rule = noClasses().that(are(PORT))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(OUTSIDE_PACKAGES)
            .because("a port names a purposeful conversation, not the technology that answers it");

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void ports_have_an_implementation() {
        List<String> orphans = APP_CLASSES.stream()
            .filter(PORT)
            .filter(port -> APP_CLASSES.stream()
                .filter(ADAPTER)
                .noneMatch(adapter -> adapter.getAllRawInterfaces().stream()
                    .anyMatch(i -> i.getName().equals(port.getName()))))
            .map(JavaClass::getName)
            .toList();

        assertThat(orphans)
            .as("@Port interfaces with no implementation — an unimplemented port is an extra file")
            .isEmpty();
    }

    /**
     * The rule with no substitute. Injecting the adapter instead of the port silently
     * undoes the seam, and no naming or packaging convention detects it.
     */
    @Test
    void nothing_depends_on_a_concrete_adapter() {
        ArchRule rule = noClasses().that(are(not(ADAPTER)))
            .should()
            .dependOnClassesThat(are(ADAPTER))
            .because("callers depend on the port; only the container knows which adapter answers");

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    /**
     * {@code @RegisterRestClient} rather than the {@code jakarta.ws.rs} import,
     * because a REST client can return plain records — {@code OllamaClient.chat}
     * does — and then nothing in the caller names a transport type at all.
     */
    @Test
    void only_adapters_talk_to_rest_clients() {
        ArchRule rule = noClasses().that(are(not(ADAPTER)))
            .should()
            .dependOnClassesThat(are(REST_CLIENT))
            .because("reaching another system's protocol is the adapter's job");

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void services_do_not_speak_http() {
        ArchRule rule = noClasses().that(are(SERVICE))
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.ws.rs..")
            .because("this is exactly what ScreenshotFetcherService used to do");

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }
}
