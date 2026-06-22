package org.linkweave.api.shared.archunit;

import java.util.Properties;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethod;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PreventEnvironmentAccessTest {

    @Test
    void no_direct_properties_and_environment_access() {
        var rule = noClasses()
            .should(callMethod(System.class, "getProperty", String.class)
                .or(callMethod(System.class, "getProperty", String.class, String.class))
                .or(callMethod(System.class, "setProperty", String.class, String.class))
                .or(callMethod(System.class, "clearProperty", String.class))
                .or(callMethod(System.class, "getProperties"))
                .or(callMethod(System.class, "setProperties", Properties.class))
                .or(callMethod(System.class, "getenv"))
                .or(callMethod(System.class, "getenv", String.class))
                .or(callMethod(Boolean.class, "getBoolean", String.class))
                .as("access properties through System or Boolean"))
            .because("it should be done through well defined classes or frameworks e.g. smallrye-config");

        rule.check(ArchConst.APP_CLASSES);
    }
}
