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
import be.cytomine.domain.ontology.Relation;
import be.cytomine.domain.ontology.Term;
import be.cytomine.exceptions.InvalidRequestException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.RelationRepository;
import be.cytomine.repository.ontology.TermRepository;
import be.cytomine.service.ontology.RelationTermService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestRelationTermController extends RestCytomineController {

    private final RelationTermService relationTermService;

    private final TermRepository termRepository;

    private final RelationRepository relationRepository;

    /**
     * List all relation for a specific term and position
     */
    @GetMapping("/relation/term/{i}/{id}.json")
    public ResponseEntity<String> listByTerm(
            @PathVariable Long id,
            @PathVariable Integer i,
            @RequestParam Map<String,String> allParams
    ) {
        log.debug("REST request to list terms");

        if (i!=1 && i!=2) {
            throw new InvalidRequestException("'i' must be 1 or 2. Current value is " + i);
        }
        return termRepository.findById(id)
                .map( term -> responseSuccess(relationTermService.list(term, String.valueOf(i))))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Term", id));
    }

    /**
     * List all relation for a specific term and position
     */
    @GetMapping("/relation/term/{id}.json")
    public ResponseEntity<String> listByTermAll(
            @PathVariable Long id,
            @RequestParam Map<String,String> allParams
    ) {
        log.debug("REST request to list terms");
        return termRepository.findById(id)
                .map( term -> responseSuccess(relationTermService.list(term)))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Term", id));
    }


    /**
     * Check if a relation exist with term1 and term2
     */
    @GetMapping("/relation/parent/term1/{idTerm1}/term2/{idTerm2}.json")
    public ResponseEntity<String> show(
            @PathVariable Long idTerm1,
            @PathVariable Long idTerm2
    ) {
        return show(relationRepository.getParent().getId(), idTerm1, idTerm2);
    }

    @GetMapping("/relation/{idRelation}/term1/{idTerm1}/term2/{idTerm2}.json")
    public ResponseEntity<String> show(
            @PathVariable Long idRelation,
            @PathVariable Long idTerm1,
            @PathVariable Long idTerm2
    ) {
        log.debug("REST request to get relation term {} {} {}", idRelation, idTerm1, idTerm2);
        Relation relation = relationRepository.findById(idRelation)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Relation", idRelation));;
        Term term1 = termRepository.findById(idTerm1)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Term", idTerm1));
        Term term2 = termRepository.findById(idTerm2).
                orElseThrow(() -> ObjectNotFoundException.notFoundException("Term", idTerm2));

        return relationTermService.find(relation, term1, term2)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("Relation Term", Map.of("Relation", idRelation, "Term", idTerm1, "Term2", idTerm2)));
    }


    /**
     * Add a new relation with two terms
     */
    @PostMapping("/relation/{id}/term.json")
    public ResponseEntity<String> add(@RequestBody JsonObject json) {
        log.debug("REST request to save Term : " + json);
        if (json.isMissing("relation")) {
            json.put("relation", relationRepository.getParent().getId());
        }
        return add(relationTermService, json);
    }

    /**
     * Delete a relation between two terms
     */
    @DeleteMapping("/relation/parent/term1/{idTerm1}/term2/{idTerm2}.json")
    public ResponseEntity<String> delete(
            @PathVariable Long idTerm1,
            @PathVariable Long idTerm2
    ) {
        return delete(relationRepository.getParent().getId(), idTerm1, idTerm2);
    }

    /**
     * Delete a relation between two terms
     */
    @DeleteMapping("/relation/{idRelation}/term1/{idTerm1}/term2/{idTerm2}.json")
    public ResponseEntity<String> delete(
            @PathVariable Long idRelation,
            @PathVariable Long idTerm1,
            @PathVariable Long idTerm2
    ) {
        log.debug("REST request to delete Relation Term");
        return delete(relationTermService, JsonObject.of("relation", idRelation, "term1", idTerm1, "term2", idTerm2), null);
    }
}
