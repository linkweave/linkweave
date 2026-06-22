package org.linkweave.api.shared.archunit;

import org.linkweave.infrastructure.db.BaseRepo;
import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.i18n.datetime.DateUtil;
import org.linkweave.api.shared.abstractentity.AbstractEntityListener;
import org.linkweave.api.shared.archunit.predicates.MethodPredicates;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.inject.Inject;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.ext.Provider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION;

@DisplayNameGeneration(ReplaceUnderscores.class)
class GeneralTest {

    @Test
    void no_standard_streams() {
        var rule = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_generic_exceptions() {
        var rule = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_field_injection() {
        var rule = noFields()
            .that()
            .areDeclaredInClassesThat(
                are(not(or(
                    annotatedWith(Provider.class),
                    assignableTo(AbstractEntityListener.class),
                    assignableTo(BaseRepo.class)
                ))))
            .should(BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION)
            .as("no classes should use field injection")
            .because("field injection is considered harmful; "
                + "use constructor injection or setter injection instead; "
                + "see https://stackoverflow.com/q/39890849 for detailed explanations");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void ensure_quarkus_doesnt_need_reflection() {
        var rule = fields()
            .that()
            .areAnnotatedWith(Inject.class)
            // .or()
            // .areAnnotatedWith(RestClient.class)
            .should()
            .notBePrivate()
            .because(
                "otherwise quarkus must use reflection. "
                    + "see: https://quarkus.io/guides/cdi-reference#native-executables-and-private-members"
            );

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_usage_of_old_date_api() {
        var rule = noClasses()
            .that()
            .doNotImplement(TypedQuery.class)
            .should()
            .dependOnClassesThat(
                resideInAPackage("org.joda.time")
                    .or(assignableTo(Calendar.class))
                    .or(assignableTo(Date.class))
            )
            .as("Date and Joda-Time shouldn't be used")
            .because("modern projects use java.time api, "
                + "for conversion utility methods exist in " + DateUtil.class);

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_usage_file_api() {
        var rule = noClasses()
            .should()
            .callMethodWhere(MethodPredicates.methodOfFileExceptDeleteOnExit())
            .because("we should use java.nio.Path instead, which offers "
                + "better performance, error handling, scalability and symlink handling.");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_usage_of_now_api_to_enable_time_travel() {
        var rule = noClasses()
            .that()
            // some exceptions are allowed
            .areNotAssignableTo(AppClock.class)
            .and()
            .areNotAssignableTo(AppClock.LocalDateTimeProvider.class)
            .and()
            .areNotAssignableTo(AppClock.LocalTimeProvider.class)
            .and()
            .areNotAssignableTo(AppClock.OffsetDateTimeProvider.class)
            .and()
            .areNotAssignableTo(AppClock.ZonedDateTimeProvider.class)
            // all other classes should not use java.time.X.now()
            .should()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(LocalTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .as("java.time.X.now() shouldn't be used")
            .because("we use ch.dvbern.dvbstarter.clock.AppClock instead to enable testing with 'time travel'");

        rule.check(ArchConst.APP_CLASSES);
    }

}
