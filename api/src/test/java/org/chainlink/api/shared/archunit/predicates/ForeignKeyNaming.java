package org.chainlink.api.shared.archunit.predicates;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaField;
import jakarta.persistence.JoinColumn;
import lombok.experimental.UtilityClass;
import org.chainlink.api.shared.archunit.ArchUtil;

@UtilityClass
public class ForeignKeyNaming {

    public static DescribedPredicate<JavaField> foreignKeyNaming(
        String description,
        BiFunction<String, String, Pattern> namingPattern
    ) {
        return DescribedPredicate.describe(
            description + " Pattern: " + namingPattern.apply("ownertable", "referencetable"),
            javaField -> javaField.tryGetAnnotationOfType(JoinColumn.class)
                .map(annotation -> validateNamingCondition(javaField, annotation.foreignKey().name(), namingPattern))
                .orElse(false)
        );
    }

    private static boolean validateNamingCondition(
        JavaField field,
        String fkName,
        BiFunction<String, String, Pattern> namingPattern
    ) {
        String ownerSimpleName = ArchUtil.tableNamePatternOf(field.getOwner().getSimpleName());
        String referenceSimpleName = ArchUtil.tableNamePatternOf(field.getRawType().getSimpleName());
        return namingPattern.apply(ownerSimpleName, referenceSimpleName)
            .matcher(fkName)
            .matches();
    }

}
