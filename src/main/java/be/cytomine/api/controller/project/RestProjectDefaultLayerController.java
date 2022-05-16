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
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.project.ProjectDefaultLayerRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.project.ProjectDefaultLayerService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestProjectDefaultLayerController extends RestCytomineController {

    private final ProjectDefaultLayerService projectDefaultLayerService;

    private final ProjectDefaultLayerRepository projectDefaultLayerRepository;

    private final ProjectRepository projectRepository;

    private final TaskService taskService;

    private final ProjectService projectService;


    @GetMapping("/project/{id}/defaultlayer.json")
    public ResponseEntity<String> listByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list projectDefaultLayers for project {}", id);
        Project project = projectService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", id));
        return responseSuccess(projectDefaultLayerService.listByProject(project));
    }

    @GetMapping("/project/{project}/defaultlayer/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable("project") Long projectId,
            @PathVariable Long id
    ) {
        log.debug("REST request to get ProjectDefaultLayer : {}", id);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return projectDefaultLayerService.find(id)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("ProjectDefaultLayer", id));
    }


    @PostMapping("/project/{id}/defaultlayer.json")
    public ResponseEntity<String> add(
            @PathVariable Long id,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request to save ProjectDefaultLayer : " + json);
        return add(projectDefaultLayerService, json);
    }

    @PutMapping("/project/{project}/defaultlayer/{id}.json")
    public ResponseEntity<String> edit(
            @PathVariable("project") Long projectId,
            @PathVariable Long id,
            @RequestBody JsonObject json) {
        log.debug("REST request to edit ProjectDefaultLayer : " + id);
        return update(projectDefaultLayerService, json);
    }

    @DeleteMapping("/project/{project}/defaultlayer/{id}.json")
    public ResponseEntity<String> delete(
            @PathVariable("project") Long projectId,
            @PathVariable Long id,
            @RequestParam(required = false) Long task) {
        log.debug("REST request to delete ProjectDefaultLayer : " + id);
        Task existingTask = taskService.get(task);
        return delete(projectDefaultLayerService, JsonObject.of("id", id), existingTask);
    }

}
