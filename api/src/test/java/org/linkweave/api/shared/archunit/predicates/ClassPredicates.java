package org.linkweave.api.shared.archunit.predicates;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import lombok.experimental.UtilityClass;
import org.linkweave.infrastructure.stereotypes.NoTransactionService;
import org.linkweave.infrastructure.stereotypes.Service;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;

@UtilityClass
public class ClassPredicates {

    public static DescribedPredicate<JavaClass> interfaceWithSuffix(String suffix) {
        return Predicates.INTERFACES.and(simpleNameEndingWith(suffix).forSubtype());
    }

    /**
     * The canonical meaning of "service" for the architecture rules: either
     * stereotype, or the interface naming convention.
     *
     * <p>Shared by {@code LayeringTest}, {@code TransactionsTest} and
     * {@code PortsAndAdaptersTest} on purpose. A second, narrower definition in
     * one of them would let a class satisfy one rule while escaping another, and
     * the escape would be invisible — every test would still be green.
     */
    public static DescribedPredicate<JavaClass> service() {
        return interfaceWithSuffix("Service")
            .or(metaAnnotatedWith(Service.class).forSubtype())
            .or(metaAnnotatedWith(NoTransactionService.class).forSubtype());
    }

}
