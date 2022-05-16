package be.cytomine.api.controller.meta;

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
import be.cytomine.domain.meta.Configuration;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.meta.ConfigurationRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.meta.ConfigurationService;
import be.cytomine.service.utils.TaskService;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestConfigurationController extends RestCytomineController {

    private final ConfigurationService configurationService;

    private final ConfigurationRepository configurationRepository;

    private final ProjectRepository projectRepository;

    private final TaskService taskService;

    /**
     * List all configuration visible for the current user
     * For each configuration, print the terms tree
     */
    @GetMapping("/configuration.json")
    public ResponseEntity<String> list(
    ) {
        log.debug("REST request to list configurations");
        return responseSuccess(configurationService.list());
    }

    @GetMapping("/configuration/key/{key}.json")
    public ResponseEntity<String> show(
            @PathVariable String key
    ) {
        log.debug("REST request to get Configuration : {}", key);
        return configurationService.findByKey(key)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("Configuration", key));
    }

    @PostMapping("/configuration.json")
    public ResponseEntity<String> add(@RequestBody String json) {
        log.debug("REST request to save Configuration : " + json);
        return add(configurationService, json);
    }

    @PutMapping("/configuration/key/{key}.json")
    public ResponseEntity<String> edit(@PathVariable String key, @RequestBody JsonObject json) {
        log.debug("REST request to edit Configuration : " + key);
        try {
            Configuration configuration = configurationService.findByKey(key)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Configuration" , key));
            json.put("id", configuration.getId());
            return update(configurationService, json);
        } catch (ObjectNotFoundException ex) {
            return add(configurationService, json);
        }
    }

    @DeleteMapping("/configuration/key/{key}.json")
    public ResponseEntity<String> delete(@PathVariable String key, @RequestParam(required = false) Long task) {
        log.debug("REST request to delete Configuration : " + key);
        Task existingTask = taskService.get(task);
        Configuration configuration = configurationService.findByKey(key)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Configuration" , key));
        return delete(configurationService, JsonObject.of("id", configuration.getId()), existingTask);
    }

}
