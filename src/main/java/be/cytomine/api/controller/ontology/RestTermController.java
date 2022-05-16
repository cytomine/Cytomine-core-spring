package be.cytomine.api.controller.ontology;

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
import be.cytomine.dto.JsonInput;
import be.cytomine.dto.JsonMultipleObject;
import be.cytomine.dto.JsonSingleObject;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.ontology.TermService;
import be.cytomine.utils.JsonObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestTermController extends RestCytomineController {

    private final TermService termService;

    private final OntologyRepository ontologyRepository;

    private final ProjectRepository projectRepository;

    @GetMapping("/term.json")
    public ResponseEntity<String> list(
    ) {
        log.debug("REST request to list terms");
        return responseSuccess(termService.list());
    }

    @GetMapping("/term/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get Term : {}", id);
        return termService.find(id)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("Term", id));
    }


    /**
     * Add a new term
     * Use next add relation-term to add relation with another term
     * @param json JSON with Term data
     * @return Response map with .code = http response code and .data.term = new created Term
     */
    @PostMapping("/term.json")
    public ResponseEntity<String> add(@RequestBody String json) throws JsonProcessingException {
        log.debug("REST request to save Term: " + json);
        return add(termService, json);
    }

    /**
     * Update a term
     * @param id Term id
     * @param json JSON with the new Term data
     * @return Response map with .code = http response code and .data.newTerm = new created Term and  .data.oldTerm = old term value
     */
    @PutMapping("/term/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit Term : " + id);
        return update(termService, json);
    }

    /**
     * Delete a term
     * @param id Term id
     * @return Response map with .code = http response code and .data.term = deleted term value
     */
    @DeleteMapping("/term/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id) {
        log.debug("REST request to delete Term : " + id);
        return delete(termService, JsonObject.of("id", id), null);
    }


    @GetMapping("/ontology/{id}/term.json")
    public ResponseEntity<String> listByOntology(
            @PathVariable Long id
    ) {
        log.debug("REST request to list terms for ontology {}", id);
        return ontologyRepository.findById(id)
                .map( ontology -> responseSuccess(termService.list(ontology)))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Ontology", id));
    }

    @GetMapping("/project/{id}/term.json")
    public ResponseEntity<String> listByProject(
            @PathVariable Long id
    ) {
        log.debug("REST request to list terms for project {}", id);
        return projectRepository.findById(id)
                .map( ontology -> responseSuccess(termService.list(ontology)))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Ontology", id));
    }
}
