package org.linkweave.api.shared.archunit;

import org.linkweave.api.shared.abstractentity.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.envers.Audited;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Guards the one failure mode Envers gives no feedback about: an entity that is
 * simply never audited.
 *
 * <p>{@link Audited} is annotated with {@code @Retention} and {@code @Target}
 * only — it is <strong>not</strong> {@code @Inherited}. Declaring it on the
 * {@link AbstractEntity} {@link MappedSuperclass} therefore makes the
 * superclass's own fields auditable <em>within entities that are themselves
 * audited</em>; it does not audit the subclasses. For years every entity except
 * User/UserSettings/UserPermission inherited that annotation and wrote nothing,
 * while hand-written {@code *_AUD} tables made the schema look correct. Nothing
 * fails at startup or at runtime — the rows just never appear.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EnversAuditingTest {

    @Test
    void entities_must_declare_audited_on_the_class_itself() {
        var rule = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .and(assignableTo(AbstractEntity.class))
            .should()
            .beAnnotatedWith(Audited.class)
            .because(
                "@Audited is not @Inherited: inheriting it from AbstractEntity audits nothing,"
                    + " and the omission is silent — no error, just an empty _AUD table."
                    + " Declare it on the entity class and mirror any new column into <Entity>_AUD"
                    + " in a Flyway migration");

        rule.check(ArchConst.APP_CLASSES);
    }
}
