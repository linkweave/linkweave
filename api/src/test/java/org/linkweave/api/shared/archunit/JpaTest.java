package org.linkweave.api.shared.archunit;

import org.linkweave.api.shared.archunit.predicates.AnnotationPredicates;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember.Predicates;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.linkweave.api.shared.archunit.predicates.AnnotationPredicates.annotationThatHas;
import static org.linkweave.api.shared.archunit.predicates.AnyOf.anyOf;
import static org.linkweave.api.shared.archunit.predicates.ForeignKeyNaming.foreignKeyNaming;
import static org.linkweave.api.shared.archunit.predicates.HaveForeignKeyDefined.foreignKeyIndexDefined;
import static org.linkweave.api.shared.archunit.predicates.TableAnnotationNaming.tableAnnotationNaming;
import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaModifier.STATIC;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.modifier;
import static com.tngtech.archunit.core.domain.properties.HasType.Predicates.rawType;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JpaTest {

    private static final DescribedPredicate<JavaClass> PERSISTABLE_CLASSES = annotatedWith(Embeddable.class)
        .or(annotatedWith(Entity.class))
        .forSubtype();

    private static final DescribedPredicate<JavaClass> ENTITY_CLASSES = annotatedWith(Entity.class)
        .and(annotatedWith(Table.class))
        .forSubtype();

    private static final DescribedPredicate<JavaClass> MAPPED_SUPERCLASSES = annotatedWith(MappedSuperclass.class)
        .forSubtype();

    private static DescribedPredicate<JavaField> tableEntityFields() {
        return describe(
            "entity fields",
            field -> not(modifier(STATIC)).test(field)
                && declaredIn(ENTITY_CLASSES).test(field)
        );
    }

    private static DescribedPredicate<JavaField> abstractEntityFields() {
        return describe(
            "abstract entity fields",
            field -> not(modifier(STATIC)).test(field)
                && declaredIn(MAPPED_SUPERCLASSES).test(field)
        );
    }

    private static DescribedPredicate<JavaField> persistedFields() {
        return describe(
            "persisted fields",
            field -> and(
                not(modifier(STATIC)),
                Predicates.declaredIn(PERSISTABLE_CLASSES)
            ).test(field)
        );
    }

    public static final DescribedPredicate<Table> NAME_NOT_SET = describe(
        "name not set",
        input -> input.name().isEmpty()
    );
    private static final DescribedPredicate<JavaClass> NAMING_CONVENTION_FOR_IX = tableAnnotationNaming(
        "the naming convention for IDX",
        Table::indexes,
        Index::name,
        simpleClassName -> Pattern.compile("ix_" + simpleClassName + "(_.*)?")
    );
    private static final DescribedPredicate<JavaClass> NAMING_CONVENTION_FOR_UC = tableAnnotationNaming(
        "the naming convention for UC",
        Table::uniqueConstraints,
        UniqueConstraint::name,
        simpleClassName -> Pattern.compile("uc_" + simpleClassName + "(_.*)?")
    );

    // NOTES:
    // Currently missing
    // - handling of embeddables with foreignKey
    // - AssociationOverride handling
    // The reference component accepts either the referenced table name (e.g.
    // fk_collection_user) or any descriptive relationship name (e.g. fk_collection_owner,
    // fk_folder_parent) — both are idiomatic, and pinning to the table name forces
    // less-readable choices when an entity has multiple FKs to the same target.
    private static final DescribedPredicate<JavaField> NAMING_CONVENTION_FOR_FK = foreignKeyNaming(
        "the naming convention for FK",
        (owner, reference) -> Pattern.compile("fk_" + owner + "_(" + reference + "|\\w+)(_.*)?")
    );

    private static final DescribedPredicate<Enumerated> VALUE_STRING = describe(
        "value STRING",
        input -> input.value() == EnumType.STRING
    );
    public static final DescribedPredicate<OneToOne> NO_MAPPED_BY_VALUE = describe(
        "no mappedBy value",
        input -> StringUtils.isEmpty(input.mappedBy())
    );
    private static final DescribedPredicate<CanBeAnnotated> FOREIGNKEY_FIELDS = annotatedWith(ManyToOne.class)
        .or(annotatedWith(annotationThatHas(OneToOne.class, NO_MAPPED_BY_VALUE)));

    @Test
    void entities_must_be_a_table_or_query() {
        var rule = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .beAnnotatedWith(rawType(anyOf(
                Table.class,
                NamedNativeQueries.class,
                NamedNativeQuery.class,
                SqlResultSetMapping.class,
                NamedQueries.class,
                NamedQuery.class
            )))
            .because("it's clear whats the purpose and allows e.g. index/constraint definition");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_foreign_keys_must_follow_naming_convention() {
        var rule = fields()
            .that(are(tableEntityFields()).or(are(abstractEntityFields())))
            .and(are(FOREIGNKEY_FIELDS))
            .should(have(NAMING_CONVENTION_FOR_FK));

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_foreign_key_fields_must_have_index() {
        // FIXME: Aktuell wird diese Regel für fk auf AbstractEntity nicht enforced!
        var rule = fields()
            .that(are(tableEntityFields()))
            .and(are(FOREIGNKEY_FIELDS))
            .should(have(foreignKeyIndexDefined()))
            .because("this can result in better performance");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_tables_must_not_override_name() {
        var rule = classes()
            .that(are(ENTITY_CLASSES))
            .should()
            .beAnnotatedWith(annotationThatHas(Table.class, NAME_NOT_SET))
            .because("finding classes from db-tables and vice versa otherwise harder");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_tables_must_follow_idx_naming_convention() {
        var rule = classes()
            .that(are(ENTITY_CLASSES))
            .should(have(NAMING_CONVENTION_FOR_IX))
            .because("it's easier to recognize");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_tables_must_follow_uc_naming_convention() {
        var rule = classes()
            .that(are(ENTITY_CLASSES))
            .should(have(NAMING_CONVENTION_FOR_UC))
            .because("it's easier to recognize");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void embeddables_must_be_annotated_with_embedded() {
        var rule = fields()
            .that(are(tableEntityFields()))
            .and()
            .haveRawType(annotatedWith(Embeddable.class))
            .should()
            .beAnnotatedWith(Embedded.class)
            .orShould()
            .beAnnotatedWith(EmbeddedId.class)
            .because("is required for everything to work properly");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void embeddables_must_be_annotated_with_valid() {
        var rule = fields()
            .that(are(tableEntityFields()))
            .and()
            .haveRawType(annotatedWith(Embeddable.class))
            .should()
            .beAnnotatedWith(Valid.class)
            .because("is required for validation");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void enum_fields_must_be_of_string_type() {
        var rule = fields()
            .that(are(tableEntityFields()))
            .and()
            .haveRawType(assignableTo(Enum.class))
            .should()
            .beAnnotatedWith(annotationThatHas(Enumerated.class, VALUE_STRING))
            .because("database migration issues can be prevented");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_fields_can_not_be_transient() {
        var rule = fields()
            .that(are(tableEntityFields()))
            .should()
            .notBeAnnotatedWith(Transient.class)
            .because("it's code smell to have this in entities");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void entity_fields_must_have_column_annotation() {
        var rule = fields()
            .that(are(tableEntityFields()))
            .and()
            .areNotAnnotatedWith(rawType(anyOf(
                Transient.class,
                ManyToOne.class,
                ManyToMany.class,
                OneToMany.class,
                OneToOne.class
            )))
            .and()
            .doNotHaveRawType(PERSISTABLE_CLASSES)
            .should()
            .beAnnotatedWith(Column.class)
            .because("it's more precise to see it's actual");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void nullable_fields_must_have_column_annotation_mirror_same_behaviour() {
        var rule = fields()
            .that(are(persistedFields()))
            .and()
            .areAnnotatedWith(columnAnnotationThatMarksTheColumnNullable())
            .and()
            .areNotAnnotatedWith(Convert.class)
            .should()
            .beAnnotatedWith(Nullable.class)
            .orShould(have(AnnotationPredicates.typeAnnotation(Nullable.class)))
            .because("you might receive null values which need to be declared explicitly");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void prevent_calling_of_EntityManager_merge() {
        var rule = noClasses()
            .should().callMethod(EntityManager.class, "merge", Object.class)
            .because(
                "This might lead to detached entities being merged"
                    + " wich can lead to unwanted side effects"
                    + " (security and optimistic locking problems)");

        rule.check(ArchConst.APP_CLASSES);
    }

    private static DescribedPredicate<? super JavaAnnotation<?>> columnAnnotationThatMarksTheColumnNullable() {
        return annotationThatHas(Column.class, describe(
            "nullable = true",
            Column::nullable
        ));
    }
}
