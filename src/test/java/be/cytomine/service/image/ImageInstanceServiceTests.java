package be.cytomine.service.image;

import be.cytomine.BasicInstanceBuilder;
import be.cytomine.TestApplication;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.NestedImageInstance;
import be.cytomine.domain.project.Project;
import be.cytomine.exceptions.AlreadyExistException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.image.UploadedFileRepository;
import be.cytomine.service.CommandService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "superadmin")
@Transactional
public class ImageInstanceServiceTests {

    @Autowired
    ImageInstanceService imageInstanceService;

    @Autowired
    UploadedFileRepository uploadedFileRepository;

    @Autowired
    BasicInstanceBuilder builder;

    @Autowired
    CommandService commandService;

    @Autowired
    TransactionService transactionService;

    @Autowired
    EntityManager entityManager;


    @Test
    void list_all_image_by_projects() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance1);
        ImageInstance imageInstance2 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance2);

        Page<Map<String, Object>> page = imageInstanceService.list(imageInstance1.getProject(), new ArrayList<>(), "id", "asc", 0L, 0L, false);

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).contains(imageInstance1.getId());
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).doesNotContain(imageInstance1.getId());
    }

    @Test
    void list_all_image_by_projects_ignore_nested_image_instance() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        NestedImageInstance nestedImageInstance1 = builder.given_a_nested_image_instance();
        nestedImageInstance1.setProject(imageInstance1.getProject());
        builder.persistAndReturn(nestedImageInstance1);
        Page<Map<String, Object>> page = imageInstanceService.list(imageInstance1.getProject(), new ArrayList<>(), "id", "asc", 0L, 0L, false);

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).contains(imageInstance1.getId());
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).doesNotContain(nestedImageInstance1.getId());
    }

    @Test
    void search_images() {
        Assertions.fail("todo: implement all search specific test");
    }

    @Test
    void search_images_with_last_activities() {
        Assertions.fail("todo: implement all search specific test + todo with impl mongodb");
    }

    @Test
    void list_all_image_by_project_light() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance1);
        ImageInstance imageInstance2 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance2);

        Page<Map<String, Object>> page = imageInstanceService.list(imageInstance1.getProject(), new ArrayList<>(), "id", "asc", 0L, 0L, true);

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).contains(imageInstance1.getId());
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).doesNotContain(imageInstance1.getId());
    }

    @Test
    void list_all_image_by_project_light_ignore_nested_image_instance() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance1);
        NestedImageInstance nestedImageInstance1 = builder.given_a_nested_image_instance();
        nestedImageInstance1.setProject(imageInstance1.getProject());
        builder.persistAndReturn(nestedImageInstance1);

        Page<Map<String, Object>> page = imageInstanceService.list(imageInstance1.getProject(), new ArrayList<>(), "id", "asc", 0L, 0L, true);

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).contains(imageInstance1.getId());
        assertThat(page.getContent().stream().map(x -> x.get("id")).collect(Collectors.toList())).doesNotContain(nestedImageInstance1.getId());
    }

    @Test
    void list_all_image_ids_for_project() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance1);
        ImageInstance imageInstance2 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance2);

        assertThat(imageInstanceService.getAllImageId(imageInstance1.getProject())).contains(imageInstance1.getId());
        assertThat(imageInstanceService.getAllImageId(imageInstance1.getProject())).doesNotContain(imageInstance2.getId());
    }

    @Test
    void list_all_image_ids_for_project_ignore_nested_image() {
        ImageInstance imageInstance1 = builder.given_an_image_instance();
        builder.persistAndReturn(imageInstance1);
        NestedImageInstance nestedImageInstance1 = builder.given_a_nested_image_instance();
        nestedImageInstance1.setProject(imageInstance1.getProject());
        builder.persistAndReturn(nestedImageInstance1);

        assertThat(imageInstanceService.getAllImageId(imageInstance1.getProject())).contains(imageInstance1.getId());
        assertThat(imageInstanceService.getAllImageId(imageInstance1.getProject())).doesNotContain(nestedImageInstance1.getId());
    }

    @Test
    void list_images_with_tree_structure() {
        Assertions.fail("todo: implement tree structure");
    }


    @Test
    void get_image_intance_with_success() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        assertThat(imageInstance).isEqualTo(imageInstanceService.get(imageInstance.getId()));
    }

    @Test
    void get_unexisting_imageInstance_return_null() {
        AssertionsForClassTypes.assertThat(imageInstanceService.get(0L)).isNull();
    }

    @Test
    void find_imageInstance_with_success() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        AssertionsForClassTypes.assertThat(imageInstanceService.find(imageInstance.getId()).isPresent());
        assertThat(imageInstance).isEqualTo(imageInstanceService.find(imageInstance.getId()).get());
    }

    @Test
    void find_unexisting_imageInstance_return_empty() {
        AssertionsForClassTypes.assertThat(imageInstanceService.find(0L)).isEmpty();
    }

    @Test
    void add_valid_image_instance_with_success() {
        ImageInstance imageInstance = builder.given_a_not_persisted_image_instance();

        CommandResponse commandResponse = imageInstanceService.add(imageInstance.toJsonObject());

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        AssertionsForClassTypes.assertThat(imageInstanceService.find(commandResponse.getObject().getId())).isPresent();
        ImageInstance created = imageInstanceService.find(commandResponse.getObject().getId()).get();
    }



    @Test
    void add_already_existing_image_instance_fails() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        Assertions.assertThrows(AlreadyExistException.class, () -> {
            imageInstanceService.add(imageInstance.toJsonObject().withChange("id", null));
        });
    }

    @Test
    void add_valid_image_instance_with_unexsting_abstract_image_fails() {
        ImageInstance imageInstance = builder.given_a_not_persisted_image_instance(null, builder.given_a_project());
        Assertions.assertThrows(AlreadyExistException.class, () -> {
            imageInstanceService.add(imageInstance.toJsonObject());
        });
    }

    @Test
    void edit_image_instance_with_success() {
        Project project1 = builder.given_a_project();
        Project project2 = builder.given_a_project();

        ImageInstance imageInstance = builder.given_a_not_persisted_image_instance(
                builder.given_an_abstract_image(), project1);
        imageInstance = builder.persistAndReturn(imageInstance);

        JsonObject jsonObject = imageInstance.toJsonObject();
        jsonObject.put("project", project2.getId());

        CommandResponse commandResponse = imageInstanceService.edit(jsonObject, true);
        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        AssertionsForClassTypes.assertThat(imageInstanceService.find(commandResponse.getObject().getId())).isPresent();
        ImageInstance updated = imageInstanceService.find(commandResponse.getObject().getId()).get();

        assertThat(updated.getProject().getId()).isEqualTo(project2.getId());
    }

    @Test
    void edit_image_instance_magnification_no_impact_in_abstract_image() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        imageInstance.getBaseImage().setMagnification(10);
        builder.persistAndReturn(imageInstance.getBaseImage());

        JsonObject jsonObject = imageInstance.toJsonObject();
        jsonObject.put("magnification", 20);

        CommandResponse commandResponse = imageInstanceService.update(imageInstance, jsonObject);
        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        AssertionsForClassTypes.assertThat(imageInstanceService.find(commandResponse.getObject().getId())).isPresent();
        ImageInstance updated = imageInstanceService.find(commandResponse.getObject().getId()).get();
        entityManager.refresh(imageInstance);

        assertThat(updated.getMagnification()).isEqualTo(20);
        assertThat(updated.getBaseImage().getMagnification()).isNotEqualTo(20);

    }

    @Test
    void edit_image_instance_resolution_modifies_annotation() {
        Assertions.fail("todo: implement annotation");
    }


    @Test
    void edit_image_instance_with_unexsting_abstract_image_fails() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        Assertions.assertThrows(WrongArgumentException.class, () -> {
            imageInstanceService.add(imageInstance.toJsonObject().withChange("baseImage", null));
        });
    }

    @Test
    void edit_image_instance_with_unexsting_project_fails() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            imageInstanceService.add(imageInstance.toJsonObject().withChange("project", null));
        });
    }

    @Test
    void edit_image_instance_with_unexsting_user_fails() {
        ImageInstance imageInstance = builder.given_an_image_instance();
        Assertions.assertThrows(ObjectNotFoundException.class, () -> {
            imageInstanceService.add(imageInstance.toJsonObject().withChange("user", null));
        });
    }


    @Test
    void delete_image_instance_with_success() {
        ImageInstance imageInstance = builder.given_an_image_instance();

        CommandResponse commandResponse = imageInstanceService.delete(imageInstance, null, null, true);

        assertThat(commandResponse).isNotNull();
        assertThat(commandResponse.getStatus()).isEqualTo(200);
        AssertionsForClassTypes.assertThat(imageInstanceService.find(imageInstance.getId()).isEmpty());
    }

    @Test
    void delete_uploadedFile_with_dependencies_with_success() {
        fail("not yet implemented");
    }

}
