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
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.ontology.Track;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.AnnotationDomainRepository;
import be.cytomine.repository.ontology.OntologyRepository;
import be.cytomine.repository.project.ProjectRepository;
import be.cytomine.service.ontology.AnnotationTrackService;
import be.cytomine.service.ontology.TrackService;
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
public class RestAnnotationTrackController extends RestCytomineController {

    private final AnnotationTrackService annotationTrackService;

    private final TrackService trackService;

    private final AnnotationDomainRepository annotationDomainRepository;

    private final ProjectRepository projectRepository;

    @GetMapping("/track/{id}/annotationtrack.json")
    public ResponseEntity<String> listByTrack(
            @PathVariable Long id
    ) {
        log.debug("REST request to list annotationTracks for track {}", id);
        return responseSuccess(annotationTrackService.list(trackService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Track", id))));
    }

    @GetMapping("/annotation/{id}/annotationtrack.json")
    public ResponseEntity<String> listByAnnotation(
            @PathVariable Long id
    ) {
        log.debug("REST request to list annotationTracks for annotation {}", id);
        return responseSuccess(annotationTrackService.list(annotationDomainRepository.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", id))));
    }


    @GetMapping("/annotationtrack/{annotation}/{track}.json")
    public ResponseEntity<String> show(
            @PathVariable(value = "annotation") Long annotationId,
            @PathVariable(value = "track") Long trackId
            ) {
        log.debug("REST request to get AnnotationTrack : {} {}", annotationId, trackId);
        AnnotationDomain annotationDomain = annotationDomainRepository.findById(annotationId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        Track track = trackService.find(trackId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Track", trackId));

        return annotationTrackService.find(annotationDomain, track)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("AnnotationTrack", Map.of("annotation", annotationId, "track", trackId)));
    }

    @DeleteMapping("/annotationtrack/{annotation}/{track}.json")
    public ResponseEntity<String> delete(
            @PathVariable(value = "annotation") Long annotationId,
            @PathVariable(value = "track") Long trackId
    ) {
        log.debug("REST request to get AnnotationTrack : {} {}", annotationId, trackId);
        return delete(annotationTrackService, JsonObject.of("annotationIdent", annotationId, "track", trackId), null);
    }

    @PostMapping("/annotationtrack.json")
    public ResponseEntity<String> add(@RequestBody String json) {
        log.debug("REST request to save AnnotationTrack : " + json);
        return add(annotationTrackService, json);
    }

}
