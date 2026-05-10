package org.chainlink.api.shared.archunit;

import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.chainlink.infrastructure.stereotypes.Service;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayNameGeneration(ReplaceUnderscores.class)
class NamingTest {

    /**
     * Must match definition of {@link LayeringTest#JSON_DTO}
     */
    @Test
    void classes_in_json_package_have_naming_convention_or_are_builders() {
        var rule = classes()
            .that()
            .resideInAPackage("..json..")
            .and()
            .resideOutsideOfPackage("..errorhandling.json..")
            .and()
            .areNotEnums()
            .should()
            .haveSimpleNameEndingWith("Json")
            .orShould()
            .haveSimpleNameEndingWith("Builder");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void resources_have_JaxResource_stereotype() {
        var rule = classes()
            .that()
            .areNotInterfaces()
            .and()
            .haveSimpleNameEndingWith("Resource")
            .should()
            .beAnnotatedWith(JaxResource.class);

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void resources_must_have_Resource_suffix() {
        var rule = classes()
            .that()
            .areNotInterfaces()
            .and()
            .areAnnotatedWith(JaxResource.class)
            .should()
            .haveSimpleNameEndingWith("Resource");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void services_have_Service_stereotype() {
        var rule = classes()
            .that()
            .areNotInterfaces()
            .and(Predicates.simpleNameEndingWith("Service")
                .or(Predicates.simpleNameEndingWith("ServiceImpl"))
            )
            .should()
            .beAnnotatedWith(Service.class);

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void services_must_have_Service_suffix() {
        var rule = classes()
            .that()
            .areNotInterfaces()
            .and()
            .areAnnotatedWith(Service.class)
            .should()
            .haveSimpleNameEndingWith("Service")
            .orShould()
            .haveSimpleNameEndingWith("ServiceImpl");

        rule.check(ArchConst.APP_CLASSES);
    }

    @Test
    void repos_have_Repository_stereotype() {
        var rule = classes()
            .that()
            .resideInAPackage("..repo..")
            .and()
            .areNotInterfaces()
            .and(Predicates.simpleNameEndingWith("Repo")
                .or(Predicates.simpleNameEndingWith("RepoImpl"))
            )
            .should()
            .beAnnotatedWith(Repository.class);

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void repos_have_Repo_suffix() {
        var rule = classes()
            .that()
            .areNotInterfaces()
            .and()
            .areAnnotatedWith(Repository.class)
            .should()
            .haveSimpleNameEndingWith("Repo")
            .orShould()
            .haveSimpleNameEndingWith("RepoImpl");

        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }
}
