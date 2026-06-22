package org.linkweave.api.shared.archunit;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import ch.dvbern.dvbstarter.types.id.ID;
import com.querydsl.core.types.Path;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@Slf4j
@DisplayNameGeneration(ReplaceUnderscores.class)
class IdClassTest {

    @Test
    void use_only_ID_class_as_id_parameter() {
        ArchRule rule = ArchRuleDefinition.methods()
            .should(haveOnlyIdParamsWithTypeID())
            .because("All Id-Parameters of Services must be of Type ID");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void use_only_ID_class_as_id_parameter_in_collections() {
        ArchRule rule = ArchRuleDefinition.methods()
            .should(haveOnlyIdCollectionParamsWithTypeID())
            .because("All Collections of Id-Parameters of Services must be of Type ID");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void use_only_ID_class_as_id_fields() {
        var rule = ArchRuleDefinition.fields()
            .that()
            .haveName("id")
            .or()
            .haveNameEndingWith("Id")
            .and().areNotAnnotatedWith(IgnoreForIdClassTest.class)
            .and().doNotHaveRawType(Predicates.assignableTo(Path.class))
            .should()
            .haveRawType(ID.class.getName())
            .because("All Id-Fields must be of Type ID to prevent mixing up IDs of different entities. "
                + "If this is a false positive, annotate the field with @IgnoreForIdClassTest.");
        rule.check(ArchConst.APP_CLASSES);
    }

    @NonNull
    private ArchCondition<JavaMethod> haveOnlyIdParamsWithTypeID() {
        return new ArchCondition<JavaMethod>("have only id params with type ch.dvbern.dvbstarter.types.id.ID") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                final Parameter[] parameters = method.reflect().getParameters();
                for (Parameter parameter : parameters) {
                    boolean isIdParam = parameter.getName().equals("id") || parameter.getName().endsWith("Id");

                    // compare by name due to some weird ClassLoader problem on Quarkus continuous testing
                    // caused by the `method.reflect()` above.
                    // Unfortunately this reflect() is needed sind ArchUnit JavaParameter
                    // does not provide the Parameter-Name yet :( (2024-11-04)
                    boolean isIdClass = Objects.equals(parameter.getType().getName(), ID.class.getName());

                    if (isIdParam && !isIdClass) {
                        if (parameter.isAnnotationPresent(IgnoreForIdClassTest.class)) {
                            continue;
                        }
                        if (method.isAnnotatedWith(Generated.class)) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(
                            method,
                            "Id-Parameter " + (parameter.getName()) + " of method " +
                                method.getFullName() + " is not of type ch.dvbern.dvbstarter.types.id.ID. "
                                + "If this is a false positive, annotate the parameter with @IgnoreForIdClassTest."));
                    }
                }
            }
        };
    }

    @NonNull
    private ArchCondition<JavaMethod> haveOnlyIdCollectionParamsWithTypeID() {
        return new ArchCondition<JavaMethod>("have only id params with type ch.dvbern.dvbstarter.types.id.ID") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                final Parameter[] parameters = method.reflect().getParameters();
                for (Parameter parameter : parameters) {
                    boolean isIdParam = parameter.getName().equals("ids") || parameter.getName().endsWith("Ids");
                    boolean isCollection = Collection.class.isAssignableFrom(parameter.getType());
                    boolean isIdCollection = false;

                    if(isCollection){
                        Type[] typeArgs = {};
                        if (parameter.getParameterizedType() instanceof ParameterizedType) {
                            typeArgs = ((ParameterizedType)  parameter.getParameterizedType()).getActualTypeArguments();
                        }

                        if (typeArgs.length > 0) {
                            isIdCollection = typeArgs[0].getTypeName().contains( ID.class.getName());
                        }
                    }

                    if (isIdParam && isCollection && !isIdCollection) {
                        if (parameter.isAnnotationPresent(IgnoreForIdClassTest.class)) {
                            continue;
                        }
                        if (method.isAnnotatedWith(Generated.class)) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(
                            method,
                            "Id-Collection-Parameter " + (parameter.getName()) + " of method " +
                                method.getFullName() + " is not of type ch.dvbern.dvbstarter.types.id.ID. "
                                + "If this is a false positive, annotate the parameter with @IgnoreForIdClassTest."));
                    }
                }
            }
        };
    }
}
