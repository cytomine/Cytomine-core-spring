package be.cytomine.api.controller.project;

/*
* Copyright (c) 2009-2022. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.api.controller.RestCytomineController;
import be.cytomine.domain.command.CommandHistory;
import be.cytomine.domain.ontology.Ontology;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.CurrentRoleService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.search.ProjectSearchExtension;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import be.cytomine.utils.filters.SearchParameterEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestProjectController extends RestCytomineController {

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;

    private final TaskService taskService;

    private final CurrentUserService currentUserService;

    private final CurrentRoleService currentRoleService;
    
    private final OntologyRepository ontologyRepository;
    private final SecUserService secUserService;

    /**
     * List all ontology visible for the current user
     * For each ontology, print the terms tree
     */
    @GetMapping("/project.json")
    public ResponseEntity<String> list(
            @RequestParam(value = "withMembersCount", defaultValue = "false", required = false) Boolean withMembersCount,
            @RequestParam(value = "withLastActivity", defaultValue = "false", required = false) Boolean withLastActivity,
            @RequestParam(value = "withDescription", defaultValue = "false", required = false) Boolean withDescription,
            @RequestParam(value = "withCurrentUserRoles", defaultValue = "false", required = false) Boolean withCurrentUserRoles,
            @RequestParam(value = "sort", defaultValue = "created", required = false) String sort,
            @RequestParam(value = "order", defaultValue = "desc", required = false) String order,
            @RequestParam(value = "offset", defaultValue = "0", required = false) Long offset,
            @RequestParam(value = "max", defaultValue = "0", required = false) Long max

    ) {
        log.debug("REST request to list projects");
        SecUser user = currentUserService.getCurrentUser();

        if(currentRoleService.isAdminByNow(user)) {
            //if user is admin, we print all available project
            user = null;
        }

        ProjectSearchExtension projectSearchExtension = new ProjectSearchExtension();
        projectSearchExtension.setWithMembersCount(withMembersCount);
        projectSearchExtension.setWithLastActivity(withLastActivity);
        projectSearchExtension.setWithDescription(withDescription);
        projectSearchExtension.setWithCurrentUserRoles(withCurrentUserRoles);
        List<SearchParameterEntry> searchParameterEntryList = super.retrieveSearchParameters();
        return responseSuccess(projectService.list(user, projectSearchExtension, searchParameterEntryList, sort, order, max, offset));
    }


    @GetMapping("/project/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get project : {}", id);
        return projectService.find(id)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("Project", id));
    }


    @PostMapping("/project.json")
    public ResponseEntity<String> add(@RequestBody JsonObject json, @RequestParam(required = false) Long task) {
        log.debug("REST request to save Project : " + json);
        Task existingTask = taskService.get(task);
        log.info("task {} is found for id = {}", existingTask, task);
        return add(projectService, json, existingTask);
    }

    @PutMapping("/project/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json, @RequestParam(required = false) Long task) {
        log.debug("REST request to edit Project : " + id);
        Task existingTask = taskService.get(task);
        return update(projectService, json, existingTask);
    }

    @DeleteMapping("/project/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id, @RequestParam(required = false) Long task) {
        log.debug("REST request to delete Project : " + id);
        Task existingTask = taskService.get(task);
        return delete(projectService, JsonObject.of("id", id), existingTask);
    }

    /**
     * Get last action done on a specific project
     * ex: "user x add a new annotation on image y",...
     */
    @GetMapping("/project/{id}/last/{max}.json")
    public ResponseEntity<String> lastAction(
            @PathVariable Long id,
            @PathVariable Long max
            
    ) {
        log.debug("REST request to list last project actions");
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", id));
        List<CommandHistory> commandHistories = projectService.lastAction(project, max.intValue());
        return responseSuccess(commandHistories);
    }


    @GetMapping("/project/method/lastopened.json")
    public ResponseEntity<String> listLastOpened(
            @RequestParam(required = false, defaultValue = "0") Long max
    ) {
        log.debug("REST request to list last opened");
        
        return responseSuccess(projectService.listLastOpened((User) currentUserService.getCurrentUser(), max));
    }

    // TODO:
//    /**
//     * List all project available for this user, that can use a software
//     */
//    @GetMapping("/software/{id}/project.json")
//    public ResponseEntity<String> listBySoftware(
//            @PathVariable Long id
//    ) {
//        log.debug("REST request to list last project actions");
//        Project project = projectRepository.findById(id)
//                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
//        return responseSuccess(projectService.listBySoftware(project));
//    }

    /**
     * List all project available for this user, that use a ontology
     */
    @GetMapping("/ontology/{id}/project.json")
    public ResponseEntity<String> listByOntology(
            @PathVariable Long id
    ) {
        log.debug("REST request to list project with ontology {}", id);
        Ontology ontology = ontologyRepository.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Ontology", id));
        return responseSuccess(projectService.listByOntology(ontology));
    }

    /**
     * List all project available for the current user, that can be used by a user
     */
    @GetMapping("/user/{id}/project.json")
    public ResponseEntity<String> listByUser(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Long max,
            @RequestParam(required = false, defaultValue = "0") Long offset

    ) {
        log.debug("REST request to list project with user {}", id);
        User user = secUserService.findUser(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", id));
        Page<JsonObject> result = projectService.list(user, new ProjectSearchExtension(), new ArrayList<>(), "created", "desc", max, offset);
        return responseSuccess(result);
    }

    /**
     * List all project available for the current user
     */
    @GetMapping("/user/{id}/project/light.json")
    public ResponseEntity<String> listLightByUser(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") Boolean creator,
            @RequestParam(required = false, defaultValue = "false") Boolean admin,
            @RequestParam(required = false, defaultValue = "false") Boolean user

    ) {
        log.debug("REST request to list project with user {}", id);
        User requestedUser = secUserService.findUser(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", id));

        if(creator) {
            return responseSuccess(projectService.listByCreator(requestedUser));
        } else if(admin) {
            return responseSuccess(projectService.listByAdmin(requestedUser));
        } else {
            return responseSuccess(projectService.listByUser(requestedUser));
        }
    }

    @GetMapping("/bounds/project.json")
    public ResponseEntity<String> bounds(
            @RequestParam(required = false, defaultValue = "false") Boolean withMembersCount
    ) {
        log.debug("REST request get bounds for projects");

        return responseSuccess(JsonObject.toJsonString(projectService.computeBounds(withMembersCount)));
    }


    @GetMapping({"/commandhistory.json", "/project/{id}/commandhistory.json"})
    public ResponseEntity<String> listCommandHistory(
            @PathVariable(required = false) Long id,
            @RequestParam(required = false) Long user,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate,
            @RequestParam(required = false, defaultValue = "false") Boolean fullData,
            @RequestParam(required = false, defaultValue = "0") Long max,
            @RequestParam(required = false, defaultValue = "0") Long offset

    ) {
        log.debug("REST request to list history with project {}", id);
        List<Project> projects = new ArrayList<>();

        if(id!=null) {
            projects.add(projectRepository.findById(id)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", id)));
        } else {
            projects.addAll(projectService.listForCurrentUser());
        }

        return responseSuccess(JsonObject.toJsonString(projectService.findCommandHistory(projects, user, max, offset, fullData, startDate, endDate)));
    }




//    @RestApiMethod(description="Invite a not yer existing user to the project")
//    @RestApiParams(params=[
//            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The project id"),
//            @RestApiParam(name="json", type="string", paramType = RestApiParamType.QUERY,description = "The user name and email of the invited user"),
//    ])
//
    @PostMapping("/project/{id}/invitation.json")
    public ResponseEntity<String> inviteNewUser(
            @PathVariable Long id,
            @RequestBody JsonObject json
    ) throws MessagingException {
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", id));
        User user = projectService.inviteUser(project, json.getJSONAttrStr("name"),
                json.getJSONAttrStr("firstname", "firstname"),
                json.getJSONAttrStr("lastname", "lastname"),
                json.getJSONAttrStr("mail"));
        return responseSuccess(user);
    }
}
