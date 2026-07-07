package org.linkweave.api.admin;

import java.util.UUID;

import org.linkweave.api.auth.RegistrationService;
import org.linkweave.api.shared.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AdminServiceITest {

    @Inject
    AdminService adminService;

    @Inject
    RegistrationService registrationService;

    @Test
    @TestSecurity(user = "support@example.com", roles = {"SUPPORT"})
    void shouldListRegisteredUsersAndExcludeSystemAdmin() {
        // ARRANGE
        String suffix = UUID.randomUUID().toString();
        User zoe = registrationService.register("zoe-" + suffix + "@test.com", "password-12345", "Zoe", "Zimmer");
        User adam = registrationService.register("adam-" + suffix + "@test.com", "password-12345", "Adam", "Aarau");

        // ACT
        AdminUserListJson result = adminService.listAllUsers();

        // ASSERT
        assertThat(result.getUsers())
            .as("system admin is filtered out by UserRepo.findAll")
            .noneMatch(u -> u.getId().equals(User.getSystemAdminId()));
        assertThat(result.getUsers())
            .filteredOn(u -> u.getId().equals(zoe.getId()) || u.getId().equals(adam.getId()))
            .extracting(AdminUserJson::getLastName)
            .containsExactly("Aarau", "Zimmer");
    }
}
