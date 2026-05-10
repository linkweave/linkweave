package org.chainlink.api.shared.archunit.predicates;

import java.util.Arrays;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HaveForeignKeyDefined {

    public static DescribedPredicate<JavaField> foreignKeyIndexDefined() {
        return DescribedPredicate.describe("foreignKey index defined", input -> {
            JavaClass owner = input.getOwner();
            if (!owner.isAnnotatedWith(Table.class)) {
                return false;
            }

            String fieldName = input.getName();
            Table tableAnnotation = owner.getAnnotationOfType(Table.class);
            boolean foreignKeyDefined = Arrays.stream(tableAnnotation.indexes())
                .anyMatch(index -> hasFieldInColumnList(index, fieldName));

            return foreignKeyDefined;
        });
    }

    private static boolean hasFieldInColumnList(Index index, String fieldName) {
        String[] columns = index.columnList()
            .split(",");
        return Arrays.stream(columns)
            .map(String::trim)
            .anyMatch(indexColumn -> isForeignKeyColumnOf(indexColumn, fieldName));
    }

    private static boolean isForeignKeyColumnOf(String columnName, String fieldName) {
        return columnName.equalsIgnoreCase(fieldName + "_id");
    }

}
