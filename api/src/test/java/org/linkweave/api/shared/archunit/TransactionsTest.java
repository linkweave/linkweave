package org.linkweave.api.shared.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import jakarta.enterprise.inject.Stereotype;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.linkweave.api.shared.archunit.ArchConst.APP_CLASSES;
import static org.linkweave.api.shared.archunit.LayeringTest.NO_TRANSACTION_SERVICE;
import static org.linkweave.api.shared.archunit.LayeringTest.REPOSITORY;
import static org.linkweave.api.shared.archunit.LayeringTest.SERVICE;
import static org.linkweave.api.shared.archunit.predicates.AnnotationPredicates.annotationThatHas;
import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.conditions.ArchConditions.fullyQualifiedName;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.ProxyRules.no_classes_should_directly_call_other_methods_declared_in_the_same_class_that;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TransactionsTest {

    // NOTE: this does currently no cover cases when e.g. interface methods are annotated

    private static final DescribedPredicate<CanBeAnnotated> TRANSACTIONAL = metaAnnotatedWith(Transactional.class);

    /**
     * Only flag {@code @Transactional} usages that actually create or require a transaction
     * boundary ({@code REQUIRED}, {@code REQUIRES_NEW}, {@code MANDATORY}). The opt-out
     * variants ({@code NOT_SUPPORTED}, {@code NEVER}, {@code SUPPORTS}) are explicitly
     * declaring "no transaction here" and are useful as documentation at any layer.
     */
    private static final DescribedPredicate<? super JavaAnnotation<?>> BOUNDARY_TRANSACTIONAL = annotationThatHas(
        Transactional.class,
        describe(
            "creates or requires a transaction boundary",
            tx -> tx.value() == TxType.REQUIRED
                || tx.value() == TxType.REQUIRES_NEW
                || tx.value() == TxType.MANDATORY
        )
    );

    @Test
    void transactional_boundary_on_classes() {
        var rule = classes()
            .that(are(not(SERVICE.or(REPOSITORY))))
            .and()
            .areNotAnnotatedWith(Stereotype.class)
            .should()
            .notBeAnnotatedWith(BOUNDARY_TRANSACTIONAL);

        rule.check(APP_CLASSES);
    }

    @Test
    void transactional_boundary_on_methods() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat(are(
                and(
                    not(SERVICE.or(REPOSITORY).or(NO_TRANSACTION_SERVICE)),
                    not(annotatedWith(Stereotype.class))
                )
            ))
            .should()
            .notBeAnnotatedWith(BOUNDARY_TRANSACTIONAL);

        rule.check(APP_CLASSES);
    }

    @Test
    void transactional_boundary_on_methods_in_no_transaction_service() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat(
                are(NO_TRANSACTION_SERVICE)
                    .and(not(annotatedWith(Stereotype.class))))
            .should()
            .notBeAnnotatedWith(Transactional.class);

        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void transactional_only_public() {
        var rule = methods()
            .that()
            .areNotPublic()
            .should(not(be(TRANSACTIONAL)));

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void transactional_never_called_inside_self() {
        var rule = no_classes_should_directly_call_other_methods_declared_in_the_same_class_that(are(TRANSACTIONAL));

        rule.check(ArchConst.APP_CLASSES);
    }
}
