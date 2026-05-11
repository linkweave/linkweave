package org.chainlink.api.shared.archunit.predicates;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import static org.chainlink.api.shared.archunit.ArchUtil.getActualType;

@UtilityClass
public class RequireSiblingMethod {

    public static <A extends Annotation> DescribedPredicate<? super JavaMethod> requireSiblingMethod() {
        return DescribedPredicate.describe(
            "a sibling method with 'require' prefix and same return type",
            javaMethod -> {
                var expectedType = getActualType(javaMethod.getReturnType());

                List<JavaClass> relatedClasses = new ArrayList<>();
                var owner = javaMethod.getOwner();
                relatedClasses.add(owner);
                relatedClasses.addAll(owner.getAllRawInterfaces());
                relatedClasses.addAll(owner.getAllRawSuperclasses());

                var present = relatedClasses.stream()
                    .map(JavaClass::getMethods)
                    .flatMap(Collection::stream)
                    .anyMatch(m -> m.getName().equals("require" + StringUtils.capitalize(javaMethod.getName()))
                        && m.getReturnType().equals(expectedType));
                return present;
            }
        );
    }

}
