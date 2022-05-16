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
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.ontology.AnnotationIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestAnnotationIndexController extends RestCytomineController {

    private final AnnotationIndexService annotationIndexService;

    private final SliceInstanceService sliceInstanceService;
    /**
     * List all ontology visible for the current user
     * For each ontology, print the terms tree
     */
    @GetMapping("/sliceinstance/{id}/annotationindex.json")
    public ResponseEntity<String> listBySlice(
            @PathVariable Long id
    ) {
        log.debug("REST request to get annotationindex for slice {}", id);
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
        return responseSuccess(annotationIndexService.list(sliceInstance));
    }

}
