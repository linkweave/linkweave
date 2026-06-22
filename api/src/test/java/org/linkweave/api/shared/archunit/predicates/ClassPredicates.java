package org.linkweave.api.shared.archunit.predicates;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import lombok.experimental.UtilityClass;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;

@UtilityClass
public class ClassPredicates {

    public static DescribedPredicate<JavaClass> interfaceWithSuffix(String suffix) {
        return Predicates.INTERFACES.and(simpleNameEndingWith(suffix).forSubtype());
    }

}
