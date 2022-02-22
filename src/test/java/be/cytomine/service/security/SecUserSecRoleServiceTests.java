package be.cytomine.service.security;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.security.SecRole;
import be.cytomine.domain.security.SecUserSecRole;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.ForbiddenException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.security.SecRoleRepository;
import be.cytomine.repository.security.SecUserSecRoleRepository;
import be.cytomine.service.CommandService;
import be.cytomine.service.PermissionService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.utils.CommandResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import javax.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_SUPER_ADMIN", username = "superadmin")
@Transactional
public class SecUserSecRoleServiceTests {

    @Autowired
    SecUserSecRoleService secUserSecRoleService;

    @Autowired
    SecUserSecRoleRepository secUserSecRoleRepository;

    @Autowired
    BasicInstanceBuilder builder;

    @Autowired
    CommandService commandService;

    @Autowired
    TransactionService transactionService;

    @Autowired
    PermissionService permissionService;

    @Autowired
    SecurityACLService securityACLService;

    @Autowired
    SecUserService secUserService;

    @Autowired
    SecRoleRepository secRoleRepository;

    @Test
    void get_secUserSecRole_with_success() {
        SecUserSecRole secUserSecRole = builder.given_a_user_role();
        assertThat(secUserSecRoleService.find(secUserSecRole.getSecUser(), secUserSecRole.getSecRole())).isPresent();
    }

    @Test
    void get_unexisting_secUserSecRole_return_null() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_user(), "ROLE_ADMIN");
        assertThat(secUserSecRoleService.find(secUserSecRole.getSecUser(), secUserSecRole.getSecRole())).isPresent();
    }

    @Test
    void list_all_role_for_a_user() {
        assertThat(secUserSecRoleService.list(builder.given_superadmin()).stream()
                .map(SecUserSecRole::getSecRole)
                .map(SecRole::getAuthority))
                .contains("ROLE_SUPER_ADMIN");


        assertThat(secUserSecRoleService.list(builder.given_a_user()).stream()
                .map(SecUserSecRole::getSecRole)
                .map(SecRole::getAuthority))
                .contains("ROLE_USER").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void find_highest_role() {
        User user = builder.given_a_guest();
        assertThat(secUserSecRoleService.getHighest(user)).isEqualTo(secRoleRepository.getGuest());

        secUserSecRoleRepository.save(builder.given_a_not_persisted_user_role(user, secRoleRepository.getUser()));

        assertThat(secUserSecRoleService.getHighest(user)).isEqualTo(secRoleRepository.getUser());

        secUserSecRoleRepository.save(builder.given_a_not_persisted_user_role(user, secRoleRepository.getAdmin()));

        assertThat(secUserSecRoleService.getHighest(user)).isEqualTo(secRoleRepository.getAdmin());

        secUserSecRoleRepository.save(builder.given_a_not_persisted_user_role(user, secRoleRepository.getSuperAdmin()));

        assertThat(secUserSecRoleService.getHighest(user)).isEqualTo(secRoleRepository.getSuperAdmin());
    }


    @Test
    void add_valid_secUser_SecRole_with_success() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_user(), secRoleRepository.getAdmin());

        CommandResponse commandResponse = secUserSecRoleService.add(secUserSecRole.toJsonObject());

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @WithMockUser(username = "user")
    void add_valid_secUser_SecRole_with_success_with_admin_as_a_user() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_user(), secRoleRepository.getAdmin());

        Assertions.assertThrows(ForbiddenException.class, () -> {
            secUserSecRoleService.add(secUserSecRole.toJsonObject());
        });
    }


    @Test
    @WithMockUser(username = "user")
    void add_valid_secUser_SecRole_with_success_with_user_as_a_user() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_guest(), secRoleRepository.getUser());
        secUserSecRoleService.add(secUserSecRole.toJsonObject());
    }

    @Test
    void add_already_existing_secUserSecRole_fails() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_user(), secRoleRepository.getAdmin());
        builder.persistAndReturn(secUserSecRole);
        Assertions.assertThrows(AlreadyExistException.class, () -> {
            secUserSecRoleService.add(secUserSecRole.toJsonObject().withChange("id", null));
        });
    }


    @Test
    void user_can_add_user_role_to_a_guest() {
        Assertions.fail("should we allow that???");
    }

    @Test
    void delete_secUserSecRole_with_success() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_a_user(), secRoleRepository.getAdmin());
        builder.persistAndReturn(secUserSecRole);

        CommandResponse commandResponse = secUserSecRoleService.delete(secUserSecRole, null, null, true);

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @WithMockUser(username = "user")
    void delete_secUserSecRole_with_simple_user_fail() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_default_user(), secRoleRepository.getGuest());
        builder.persistAndReturn(secUserSecRole);
        Assertions.assertThrows(ForbiddenException.class, () -> {
            secUserSecRoleService.delete(secUserSecRole, null, null, true);
        });
    }


    @Test
    @WithMockUser(username = "user")
    void delete_secUserSecRole_to_remove_its_own_role() {
        SecUserSecRole secUserSecRole = builder.given_a_not_persisted_user_role(builder.given_default_user(), secRoleRepository.getAdmin());
        builder.persistAndReturn(secUserSecRole);
        Assertions.assertThrows(ForbiddenException.class, () -> {
            secUserSecRoleService.delete(secUserSecRole, null, null, true);
        });
    }




    @Disabled("wait for software package")
    @Test
    void delete_secUserSecRole_with_success_for_user_algo() {

    }



    @Test
    void re_define_role() {
        User user = builder.given_a_guest();

        secUserSecRoleService.define(user, secRoleRepository.getGuest());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_GUEST");

        secUserSecRoleService.define(user, secRoleRepository.getUser());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_GUEST");

        secUserSecRoleService.define(user, secRoleRepository.getAdmin());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_GUEST", "ROLE_ADMIN");

        secUserSecRoleService.define(user, secRoleRepository.getSuperAdmin());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_GUEST", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");

        secUserSecRoleService.define(user, secRoleRepository.getAdmin());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_GUEST", "ROLE_ADMIN");

        secUserSecRoleService.define(user, secRoleRepository.getUser());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_GUEST");

        secUserSecRoleService.define(user, secRoleRepository.getGuest());
        assertThat(secUserSecRoleService.list(user).stream().map(x -> x.getSecRole().getAuthority()))
                .containsExactlyInAnyOrder("ROLE_GUEST");

    }


}
