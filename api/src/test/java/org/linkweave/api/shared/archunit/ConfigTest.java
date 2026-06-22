package org.linkweave.api.shared.archunit;

import java.util.Optional;

import org.linkweave.api.shared.config.ConfigService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMember.Predicates;
import io.smallrye.config.ConfigMapping;
import org.apache.commons.lang3.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.linkweave.api.shared.archunit.predicates.AnnotationPredicates.annotationThatHas;
import static org.linkweave.api.shared.archunit.predicates.RequireSiblingMethod.requireSiblingMethod;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongTo;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ConfigTest {

    public static final DescribedPredicate<JavaClass> CONFIG_INTERFACE = metaAnnotatedWith(ConfigMapping.class)
        .as("Config")
        .forSubtype();
    private static final DescribedPredicate<ConfigProperty> ONLY_QUARKUS_PROPERTY_VALUES = DescribedPredicate.describe(
        "only quarkus property values",
        configProperty -> Strings.CI.startsWith(configProperty.name(), "quarkus.")
    );

    @Test
    @Disabled("This test is disabled because this is project specific")
    void only_interfaces_allowed() {
        var rule = classes()
            .that()
            .areMetaAnnotatedWith(ConfigMapping.class)
            .should()
            .beInterfaces();

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void follow_naming_convention() {
        var rule = classes()
            .that(belongTo(CONFIG_INTERFACE))
            .should()
            .haveSimpleNameEndingWith("Config")
            .because("its easier to recognize");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void optional_has_require_method() {
        var rule = methods()
            .that(are(Predicates.declaredIn(belongTo(CONFIG_INTERFACE))))
            .and()
            .haveRawReturnType(Optional.class)
            .should(have(requireSiblingMethod()));

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    @Disabled("This test is disabled because this is project specific")
    void inject_configs_with_interfaces() {
        var rule = fields()
            .that()
            .areAnnotatedWith(ConfigProperty.class)
            .should()
            .beAnnotatedWith(annotationThatHas(ConfigProperty.class, ONLY_QUARKUS_PROPERTY_VALUES))
            .because("injection with @ConfigMapping with interfaces should be used");

        rule.check(ArchConst.APP_CLASSES);
    }
    @Test
    void inject_configs_should_be_tyhrough_configservice() {
        var rule = fields()
            .that()
            .areAnnotatedWith(ConfigProperty.class)
            // allow "quarkus..." properties
            .and().areNotAnnotatedWith(annotationThatHas(ConfigProperty.class, ONLY_QUARKUS_PROPERTY_VALUES))
            .should()
            .beDeclaredInClassesThat()
            .haveFullyQualifiedName(ConfigService.class.getName())
            .because("@ConfigProperty should be read through ConfigService for better encapsulation");

        rule.check(ArchConst.APP_CLASSES);
    }
}
