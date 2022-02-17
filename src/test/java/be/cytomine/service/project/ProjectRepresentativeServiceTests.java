package be.cytomine.service.project;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.CytomineCoreApplication;
import be.cytomine.domain.project.ProjectRepresentativeUser;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.project.ProjectRepresentativeUserRepository;
import be.cytomine.service.CommandService;
import be.cytomine.service.PermissionService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.utils.CommandResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import javax.transaction.Transactional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(classes = CytomineCoreApplication.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_SUPER_ADMIN", username = "superadmin")
@Transactional
public class ProjectRepresentativeServiceTests {

    @Autowired
    ProjectRepresentativeUserService projectRepresentativeUserService;

    @Autowired
    ProjectRepresentativeUserRepository projectRepresentativeUserRepository;

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

    @Test
    void get_projectRepresentativeUser_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();
        assertThat(projectRepresentativeUser).isEqualTo(projectRepresentativeUserService.get(projectRepresentativeUser.getId()));
    }

    @Test
    void get_unexisting_projectRepresentativeUser_return_null() {
        assertThat(projectRepresentativeUserService.get(0L)).isNull();
    }

    @Test
    void find_projectRepresentativeUser_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();
        assertThat(projectRepresentativeUserService.find(projectRepresentativeUser.getId()).isPresent());
        assertThat(projectRepresentativeUser).isEqualTo(projectRepresentativeUserService.find(projectRepresentativeUser.getId()).get());
    }

    @Test
    void find_unexisting_projectRepresentativeUser_return_empty() {
        assertThat(projectRepresentativeUserService.find(0L)).isEmpty();
    }

    @Test
    void find_projectRepresentativeUser_with_project_and_user_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();
        assertThat(projectRepresentativeUserService.find(projectRepresentativeUser.getProject(), projectRepresentativeUser.getUser()).isPresent());
        assertThat(projectRepresentativeUser).isEqualTo(projectRepresentativeUserService.find(projectRepresentativeUser.getProject(), projectRepresentativeUser.getUser()).get());
    }

    @Test
    void find_unexisting_projectRepresentativeUser_with_project_and_user_return_empty() {
        assertThat(projectRepresentativeUserService.find(builder.given_a_project(), builder.given_superadmin())).isEmpty();
    }


    @Test
    void list_all_projectRepresentativeUser_by_project_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();
        ProjectRepresentativeUser projectRepresentativeUserFromAnotherProject = builder.given_a_project_representative_user();
        assertThat(projectRepresentativeUser).isIn(projectRepresentativeUserService.listByProject(projectRepresentativeUser.getProject()));
        assertThat(projectRepresentativeUserFromAnotherProject).isNotIn(projectRepresentativeUserService.listByProject(projectRepresentativeUser.getProject()));


    }

    @Test
    void add_valid_projectRepresentativeUser_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_not_persisted_project_representative_user();

        CommandResponse commandResponse = projectRepresentativeUserService.add(projectRepresentativeUser.toJsonObject());

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        assertThat(projectRepresentativeUserService.find(commandResponse.getObject().getId())).isPresent();
        ProjectRepresentativeUser created = projectRepresentativeUserService.find(commandResponse.getObject().getId()).get();
        assertThat(created.getProject()).isEqualTo(projectRepresentativeUser.getProject());
    }

    @Test
    void add_projectRepresentativeUser_with_bad_project() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_not_persisted_project_representative_user();
        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            projectRepresentativeUserService.add(projectRepresentativeUser.toJsonObject().withChange("project", 0L));
        });
    }

    @Test
    void add_projectRepresentativeUser_with_bad_user() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_not_persisted_project_representative_user();
        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            projectRepresentativeUserService.add(projectRepresentativeUser.toJsonObject().withChange("user", 0L));
        });
    }

    @Test
    void add_already_existing_projectRepresentativeUser_fails() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();
        Assertions.assertThrows(AlreadyExistException.class, () -> {
            projectRepresentativeUserService.add(projectRepresentativeUser.toJsonObject().withChange("id", null));
        });
    }

    @Test
    void delete_projectRepresentativeUser_with_success() {
        ProjectRepresentativeUser projectRepresentativeUser = builder.given_a_project_representative_user();

        CommandResponse commandResponse = projectRepresentativeUserService.delete(projectRepresentativeUser, null, null, true);

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        assertThat(projectRepresentativeUserService.find(projectRepresentativeUser.getId()).isEmpty());
    }
}