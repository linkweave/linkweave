package org.linkweave.api.shared.archunit.predicates;

import java.io.File;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class MethodPredicates {

    private MethodPredicates() {
    }

    @NonNull
    public static DescribedPredicate<JavaMethodCall> methodOfFileExceptDeleteOnExit() {
        return DescribedPredicate.describe(
            " in java.io.File (except deleteOnExit)",
            javaMethodCall -> javaMethodCall.getTarget().getOwner().isEquivalentTo(File.class)
                && !javaMethodCall.getTarget()
                .getName()
                .equals("deleteOnExit"));
    }
}
