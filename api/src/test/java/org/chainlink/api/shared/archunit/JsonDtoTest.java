package org.chainlink.api.shared.archunit;

import java.lang.reflect.Parameter;

import org.chainlink.api.shared.archunit.predicates.AnnotationPredicates;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.chainlink.api.shared.archunit.ArchConst.Pattern;
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
    void all_fields_of_dtos_must_declare_nullability_or_schema() {
        // Modern Quarkus + jspecify already infers required/optional from @NonNull / @NotNull /
        // @Nullable, so @Schema is only load-bearing for fields the generator can't infer:
        // primitives and reference fields without any nullness annotation. This rule enforces
        // that every DTO field has SOME explicit declaration, without forcing redundant @Schema
        // annotations on top of @NonNull/@Nullable. jspecify's @NonNull/@Nullable are TYPE_USE
        // annotations, so we have to inspect the field's type as well as the field itself.
        ArchRule rule = ArchRuleDefinition
            .fields().that()
            .areDeclaredInClassesThat(JSON_DTO)
            .and().areNotStatic()
            .should().beAnnotatedWith(Schema.class)
            .orShould().beAnnotatedWith(NotNull.class)
            .orShould(have(AnnotationPredicates.typeAnnotation(org.jspecify.annotations.NonNull.class)))
            .orShould(have(AnnotationPredicates.typeAnnotation(Nullable.class)))
            .because("Every DTO field must declare its wire-format optionality: "
                + "via @NonNull / @NotNull (required), @Nullable (optional), "
                + "or @Schema(required = ...) when neither nullness annotation applies (e.g. primitives).");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
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
