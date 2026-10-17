package org.linkweave.api.shared.archunit;

import java.util.Collection;
import java.util.OptionalInt;

import org.linkweave.api.sentry.SentryTunnelResource;
import org.linkweave.infrastructure.db.NotRetryableOnSqliteBusy;
import org.linkweave.infrastructure.db.RetryOnSqliteBusy;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.linkweave.api.shared.archunit.ArchConst.Pattern.STARTS_WITH_LOWERCASE_FOLLOWED_BY_ALPHANUMERIC;
import static org.linkweave.api.shared.archunit.ArchUtil.parameterAnnotations;
import static org.linkweave.api.shared.archunit.LayeringTest.JSON_DTO;
import static org.linkweave.api.shared.archunit.predicates.AnnotationPredicates.annotationThatHas;
import static org.linkweave.api.shared.archunit.predicates.PathAnnotation.pathWithOptionalKebabCaseSegments;
import static org.linkweave.api.shared.archunit.predicates.PathAnnotation.pathWithSafeChars;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.conditions.ArchConditions.haveRawReturnType;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JaxResourceTest {

    private static DescribedPredicate<? super JavaAnnotation<?>> validPathValue() {
        return annotationThatHas(
            Path.class,
            DescribedPredicate.and(
                pathWithOptionalKebabCaseSegments(),
                pathWithSafeChars()
            )
        );
    }

    private static DescribedPredicate<JavaMethod> haveAParamWithAnnotation(Class<?> annotationClass) {
        return DescribedPredicate.describe(
            "have a query-param parameter",
            javaMethod -> parameterAnnotations(javaMethod).stream()
                .anyMatch(annotation -> annotation.getRawType().isAssignableTo(annotationClass))
        );
    }

    private static DescribedPredicate<JavaMethod> validQueryParamNaming() {
        return DescribedPredicate.describe(
            "query-param has valid naming",
            method -> parameterAnnotations(method).stream()
                .filter(annotation -> annotation.getRawType().isAssignableTo(QueryParam.class))
                .map(annotation -> annotation.as(QueryParam.class).value())
                .allMatch(queryParam -> STARTS_WITH_LOWERCASE_FOLLOWED_BY_ALPHANUMERIC.matcher(queryParam).matches())
        );
    }

    @Test
    void dtos_returned_by_resource_methods_must_be_in_json_DTOs() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(JaxResource.class)
            .and()
            .arePublic()
            .and()
            .doNotHaveRawReturnType(Void.TYPE)
            .should(haveRawReturnType(JSON_DTO))
            .orShould(haveRawReturnType(Response.class))
            .orShould(haveRawReturnType(Multi.class))
            .andShould().notHaveRawReturnType(Collection.class)
            .because("all return values of resource methods should be JSON DTOs (@JaxDTO) or Response. "
                + "Do not use Collections: you must always return a JSON object (and not e.g. an array) "
                + "as a top level data structure to support future extensibility ");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void params_taken_by_resource_methods_must_be_json_DTOs_or_builtin_types() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(JaxResource.class)
            .and()
            .areNotDeclaredIn(SentryTunnelResource.class)
            .and()
            .arePublic()
            .should(have(paramsThatAreJsonDTOsOrJaxRSParams()))
            .because("all params of resource methods should be JSON DTOs (@JaxDTO)");

        rule.check(ArchConst.APP_CLASSES);
    }

    private DescribedPredicate<? super JavaMethod> paramsThatAreJsonDTOsOrJaxRSParams() {
        return new DescribedPredicate<>("json DTOs or JaxRS specific parameters") {
            @Override
            public boolean test(JavaMethod javaMethod) {
                return javaMethod.getParameters().stream()
                    .allMatch(parameter ->
                        parameter.isMetaAnnotatedWith(QueryParam.class)
                            || parameter.isMetaAnnotatedWith(PathParam.class)
                            || parameter.isMetaAnnotatedWith(Context.class)
                            || parameter.isMetaAnnotatedWith(FormParam.class)
                            || parameter.isMetaAnnotatedWith(RestForm.class)
                            || JSON_DTO.test(parameter.getRawType()));
            }
        };
    }

    @Test
    void paths_must_be_defined_on_resources() {
        var rule = classes()
            .that()
            .areAnnotatedWith(JaxResource.class)
            .should()
            .beAnnotatedWith(Path.class);

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void paths_must_be_valid_on_classes() {
        var rule = classes()
            .that()
            .areAnnotatedWith(Path.class)
            .should()
            .beAnnotatedWith(validPathValue());

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void paths_must_be_valid_on_resource_methods() {
        var rule = methods()
            .that()
            .areAnnotatedWith(Path.class)
            .should()
            .beAnnotatedWith(validPathValue());

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void query_params_must_be_properly_defined() {
        var rule = methods()
            .that(haveAParamWithAnnotation(QueryParam.class))
            .should(have(validQueryParamNaming()));

        rule.allowEmptyShould(true)
            .check(ArchConst.APP_CLASSES);
    }

    @Test
    void enforce_rate_limit_on_all_methods() {

        var rule = classes()
            .that()
            .areAnnotatedWith(JaxResource.class)
            .should()
            .beAnnotatedWith(RateLimit.class)
            .because("we need to protect ourself from possible attacks");

        rule.check(ArchConst.APP_CLASSES);
    }

    /**
     * BR-109-2: contention coverage is enforced, not remembered.
     *
     * <p>Every resource must take a position on {@code SQLITE_BUSY}, because the only way to know
     * whether re-executing an endpoint is safe is to look at what it does besides write to the
     * database. Inference was tried and rejected: being
     * {@code @Transactional(TxType.NOT_SUPPORTED)} looks like a reason to exempt a resource, but
     * {@code AuthResource} is exactly that and still needs the boundary — the services it calls
     * each open their own transaction, so a retry re-runs them on a fresh WAL snapshot. A rule
     * that guessed would have exempted the one path whose failure takes down every request
     * (BR-109-3).</p>
     *
     * <p>So the rule is deliberately blunt, like {@link #enforce_rate_limit_on_all_methods()}:
     * declare {@code @RetryOnSqliteBusy}, or declare {@code @NotRetryableOnSqliteBusy} and say
     * why. A new resource cannot be added without that decision being made.</p>
     */
    @Test
    void enforce_sqlite_busy_retry_boundary_on_all_resources() {

        var rule = classes()
            .that()
            .areAnnotatedWith(JaxResource.class)
            .should()
            .beAnnotatedWith(RetryOnSqliteBusy.class)
            .orShould()
            .beAnnotatedWith(NotRetryableOnSqliteBusy.class)
            .because("lock contention on the single SQLite file (C-003) is a normal operating "
                + "state, so every resource must either retry on it or document why retrying "
                + "would be incorrect — annotate with @RetryOnSqliteBusy, or with "
                + "@NotRetryableOnSqliteBusy(because = \"...\") when re-execution would repeat a "
                + "side effect a rollback cannot undo");

        rule.check(ArchConst.APP_CLASSES);
    }

    /**
     * BR-109-4: every retry attempt spends a {@code @RateLimit} permit, because
     * {@code @RetryOnSqliteBusy} is ordered outside SmallRye Fault Tolerance. The two numbers are
     * declared in different places — often the bucket on a method and the retry on the class — so
     * nothing makes their product visible. {@code AuthResource#register} is how that goes wrong: a
     * 5-permit bucket under the class default of 5 attempts.
     */
    @Test
    void retry_attempts_must_not_be_able_to_drain_a_rate_limit_bucket() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(JaxResource.class)
            .and()
            .arePublic()
            .should(leaveHalfTheRateLimitBucketForOtherCallers())
            .because("a method whose retry attempts exceed half its rate-limit bucket turns lock "
                + "contention into a lockout for everyone — lower attempts, or raise the bucket");

        rule.check(ArchConst.APP_CLASSES);
    }

    private static ArchCondition<JavaMethod> leaveHalfTheRateLimitBucketForOtherCallers() {
        return new ArchCondition<>("leave at least half of its rate-limit bucket for other callers") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                int attempts = effectiveRetryAttempts(method);
                OptionalInt bucket = effectiveRateLimit(method);
                if (attempts <= 1 || bucket.isEmpty()) {
                    return;
                }
                if (attempts * 2 > bucket.getAsInt()) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                        "%s may spend %d of its %d rate-limit permits on one contended call",
                        method.getFullName(), attempts, bucket.getAsInt())));
                }
            }
        };
    }

    /** Method wins, then class — as {@code SqliteBusyRetryInterceptor#resolveAttempts} resolves it. */
    private static int effectiveRetryAttempts(JavaMethod method) {
        return method.tryGetAnnotationOfType(RetryOnSqliteBusy.class)
            .or(() -> method.getOwner().tryGetAnnotationOfType(RetryOnSqliteBusy.class))
            .map(retry -> Math.max(1, retry.attempts()))
            .orElse(1);
    }

    private static OptionalInt effectiveRateLimit(JavaMethod method) {
        return method.tryGetAnnotationOfType(RateLimit.class)
            .or(() -> method.getOwner().tryGetAnnotationOfType(RateLimit.class))
            .map(rateLimit -> OptionalInt.of(rateLimit.value()))
            .orElseGet(OptionalInt::empty);
    }

    @Test
    void ensure_permission_are_defined_on_endpoints() {
        var rule = classes()
            .that()
            .areAnnotatedWith(JaxResource.class)
            .should()
            .beMetaAnnotatedWith(RolesAllowed.class)
            .orShould()
            .beMetaAnnotatedWith(PermitAll.class)
            .orShould()
            .beMetaAnnotatedWith(Authenticated.class)
            .because("every resource should declare an explicit access decision");

        rule.check(ArchConst.APP_CLASSES);
    }
    @Test
    void ensure_permission_are_defined_on_endpoint_methods() {
        var rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(JaxResource.class)
            .and()
            .arePublic()
            .should()
            .beMetaAnnotatedWith(RolesAllowed.class)
            .orShould()
            .beMetaAnnotatedWith(PermitAll.class)
            .orShould()
            .beMetaAnnotatedWith(Authenticated.class)
            .because("every method of a resource should declare an explicit access decision "
                + "(class-level coverage doesn't count — too easy to forget when adding methods)");

        rule.check(ArchConst.APP_CLASSES);
    }
}
