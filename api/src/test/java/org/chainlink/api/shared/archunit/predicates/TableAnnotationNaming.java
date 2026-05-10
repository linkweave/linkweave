package org.chainlink.api.shared.archunit.predicates;

import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import jakarta.persistence.Table;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TableAnnotationNaming {

    public static <T> DescribedPredicate<JavaClass> tableAnnotationNaming(
        String description,
        Function<Table, T[]> iteration,
        Function<T, String> valueName,
        Function<String, Pattern> namingPattern
    ) {
        return DescribedPredicate.describe(
            description + " Pattern: " + namingPattern.apply("tablename"),
            javaClass -> validateNamingConditions(javaClass, iteration, valueName, namingPattern)
        );
    }

    private static <T> boolean validateNamingConditions(
        JavaClass javaClass,
        Function<Table, T[]> iteration,
        Function<T, String> valueName,
        Function<String, Pattern> namingPattern
    ) {
        String classSimpleName = javaClass.getSimpleName()
            .toLowerCase();
        Pattern pattern = namingPattern.apply(classSimpleName);

        return javaClass.tryGetAnnotationOfType(Table.class)
            .map(iteration)
            .map(values -> validateNamingCondition(values, valueName, pattern))
            .orElse(false);
    }

    private static <T> boolean validateNamingCondition(
        T[] values,
        Function<T, String> valueName,
        Pattern pattern
    ) {
        return Arrays.stream(values)
            .map(valueName)
            .allMatch(name -> pattern.matcher(name).matches());
    }

}
