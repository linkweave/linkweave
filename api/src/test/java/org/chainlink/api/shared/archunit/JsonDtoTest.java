package org.chainlink.api.shared.archunit;

import java.lang.reflect.Parameter;

import org.chainlink.api.shared.archunit.predicates.AnnotationPredicates;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.chainlink.api.shared.archunit.ArchConst.Pattern;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.chainlink.api.shared.archunit.LayeringTest.JSON_DTO;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JsonDtoTest {

    @Test
    void fields_of_dtos_should_comply_naming_convention() {
        var rule = fields()
            .that()
            .areDeclaredInClassesThat(JSON_DTO)
            .and().areNotStatic()
            .should()
            .haveNameMatching(Pattern.STARTS_WITH_LOWERCASE_FOLLOWED_BY_ALPHANUMERIC.pattern());

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void boolean_fields_of_dtos_should_never_be_null() {
        var rule = fields()
            .that()
            .areDeclaredInClassesThat(JSON_DTO)
            .should()
            .notHaveRawType(Boolean.class)
            .because("you should use the primitive instead which makes it never null");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void dto_relations_should_have_valid_annotation() {
        ArchRule rule = ArchRuleDefinition
            .fields().that()
            .areDeclaredInClassesThat(JSON_DTO)
            .and().haveRawType(JSON_DTO)
            .should().beAnnotatedWith(Valid.class)
            .because("All relations to json-dto-classes must be annoated with '@Valid'");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void dtos_used_as_resource_parameters_should_have_valid_annotation() {
        var rule = methods().that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(JaxResource.class)
            .and()
            .arePublic()
            .should(haveOnlyJaxParametersAnnotatedWithValid())
            .because("all params of resource methods should be JSON DTOs (@JaxDTO)");
        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void reference_fields_of_dtos_must_declare_nullability_or_schema() {
        // Modern Quarkus + jspecify already infers required/optional from @NonNull / @NotNull /
        // @Nullable, so @Schema is only load-bearing for fields the generator can't infer.
        // For reference-typed fields this rule accepts any of the four annotations -- whichever
        // is most natural for the field. jspecify's @NonNull/@Nullable are TYPE_USE
        // annotations, so we inspect the type as well as the field itself.
        ArchRule rule = ArchRuleDefinition
            .fields().that()
            .areDeclaredInClassesThat(JSON_DTO)
            .and().areNotStatic()
            .and().doNotHaveRawType(primitiveType())
            .should().beAnnotatedWith(Schema.class)
            .orShould().beAnnotatedWith(NotNull.class)
            .orShould(have(AnnotationPredicates.typeAnnotation(org.jspecify.annotations.NonNull.class)))
            .orShould(have(AnnotationPredicates.typeAnnotation(Nullable.class)))
            .because("Every DTO reference field must declare its wire-format optionality: "
                + "via @NonNull / @NotNull (required), @Nullable (optional), "
                + "or @Schema(required = ...).");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void primitive_fields_of_dtos_must_have_schema_annotation() {
        // Primitives can't be null in Java, but the OpenAPI generator defaults them to
        // optional in the wire schema unless told otherwise. @NotNull/@NonNull aren't
        // generator-meaningful on primitives -- only @Schema(required = ...) is.
        ArchRule rule = ArchRuleDefinition
            .fields().that()
            .areDeclaredInClassesThat(JSON_DTO)
            .and().areNotStatic()
            .and().haveRawType(primitiveType())
            .should().beAnnotatedWith(Schema.class)
            .because("Primitive DTO fields must use @Schema(required = ...) -- "
                + "the OpenAPI generator otherwise defaults them to optional in the wire schema.");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    private static DescribedPredicate<JavaClass> primitiveType() {
        return DescribedPredicate.describe("primitive", JavaClass::isPrimitive);
    }

    @NonNull
    private ArchCondition<JavaMethod> haveOnlyJaxParametersAnnotatedWithValid() {
        return new ArchCondition<JavaMethod>("have @Valid parameters") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                final Parameter[] parameters = method.reflect().getParameters();
                for (Parameter parameter : parameters) {
                    if (parameter.getType().isAnnotationPresent(JaxDTO.class)) {
                        if (!parameter.isAnnotationPresent(Valid.class)) {
                            var message = "Jax-Parameter %s of method %s does not have a @Valid annotation."
                                .formatted(parameter.getName(), method.getFullName());
                            events.add(SimpleConditionEvent.violated(method, message));
                        }
                    }
                }
            }
        };
    }
}
