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
import be.cytomine.domain.project.Project;
import be.cytomine.domain.project.ProjectRepresentativeUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.project.ProjectRepresentativeUserRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.project.ProjectRepresentativeUserService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestProjectRepresentativeUserController extends RestCytomineController {

    private final ProjectRepresentativeUserService projectRepresentativeUserService;

    private final ProjectRepresentativeUserRepository projectRepresentativeUserRepository;

    private final ProjectRepository projectRepository;

    private final TaskService taskService;

    private final ProjectService projectService;

    private final SecUserService secUserService;


    @GetMapping("/project/{id}/representative.json")
    public ResponseEntity<String> listByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list projectRepresentativeUsers for project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , id));
        return responseSuccess(projectRepresentativeUserService.listByProject(project));
    }

    @GetMapping("/project/{project}/representative/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable("project") Long projectId,
            @PathVariable Long id
    ) {
        log.debug("REST request to get ProjectRepresentativeUser : {}", id);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return projectRepresentativeUserService.find(id)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("ProjectRepresentativeUser", id));
    }
    
    @PostMapping("/project/{id}/representative.json")
    public ResponseEntity<String> add(
            @PathVariable Long id,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request to save ProjectRepresentativeUser : " + json);
        return add(projectRepresentativeUserService, json);
    }

    @DeleteMapping({"/project/{project}/representative/{id}.json", "/project/{project}/representative.json"})
    public ResponseEntity<String> delete(
            @PathVariable(value = "project", required = true) Long projectId,
            @PathVariable(required = false) Long id,
            @RequestParam(value = "user", required = false) Long userId,
            @RequestParam(required = false) Long task) {
        log.debug("REST request to delete ProjectRepresentativeUser");

        ProjectRepresentativeUser projectRepresentativeUser;
        if(id!=null) {
            projectRepresentativeUser = projectRepresentativeUserService.find(id)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("ProjectRepresentativeUser", id));
        } else {
            Project project = projectService.find(projectId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
            User user = secUserService.findUser(userId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));
            projectRepresentativeUser = projectRepresentativeUserService.find(project, user)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("ProjectRepresentativeUser", JsonObject.of("project", projectId, "user", userId).toJsonString()));
        }
        Task existingTask = taskService.get(task);
        return delete(projectRepresentativeUserService, JsonObject.of("id", projectRepresentativeUser.getId()), existingTask);
    }

}
