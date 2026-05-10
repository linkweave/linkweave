package org.chainlink.api.shared.archunit.predicates;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import lombok.experimental.UtilityClass;

import static com.tngtech.archunit.core.domain.Formatters.formatNamesOf;
import static java.util.Arrays.stream;

@UtilityClass
public class AnyOf {

    public static DescribedPredicate<JavaClass> anyOf(Class<?>... classes) {
        return DescribedPredicate.describe(
            "any of " + formatNamesOf(classes),
            javaClass -> stream(classes).anyMatch(javaClass::isEquivalentTo)
        );
    }

}
