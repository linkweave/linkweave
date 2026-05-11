package org.chainlink.api.shared.archunit;

import org.chainlink.infrastructure.errorhandling.AppException;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExceptionsTest {

    @Test
    void only_specific_exceptions() {
        var rule = noClasses()
            .that()
            .areNotAssignableTo(AppException.class)
            .should()
            .beAssignableTo(Throwable.class)
            .because("it prevents growing too many different one");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void extends_runtime_exception() {
        var rule = classes()
            .that(are(equivalentTo(AppException.class)))
            .should()
            .beAssignableTo(RuntimeException.class)
            .because("in streams these are propagated");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void extends_base_exception() {
        var rule = classes()
            .that()
            .areAssignableTo(Exception.class)
            .should()
            .beAssignableTo(AppException.class)
            .because("prevents misuse outside of the base class");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void final_class() {
        var rule = classes()
            .that()
            .areAssignableTo(Exception.class)
            .and()
            .doNotHaveModifier(JavaModifier.ABSTRACT)
            .should()
            .haveModifier(JavaModifier.FINAL)
            .because("this prevents misuse");

        rule.check(ArchConst.APP_CLASSES);
    }
}
