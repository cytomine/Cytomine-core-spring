package be.cytomine.api.controller.social;

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
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.repository.ontology.AnnotationDomainRepository;
import be.cytomine.repositorynosql.social.AnnotationActionRepository;
import be.cytomine.service.social.AnnotationActionService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

/**
 * Controller for user position
 * Position of the user (x,y) on an image for a time
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestAnnotationActionController extends RestCytomineController {

    private final AnnotationActionService annotationActionService;

    private final AnnotationActionRepository annotationActionRepository;

    private final AnnotationDomainRepository annotationDomainRepository;

    private final CurrentUserService currentUserService;

    private final SecUserService secUserService;

    private final ImageInstanceService imageInstanceService;

    private final SliceInstanceService sliceInstanceService;

    private final ProjectService projectService;

    // example: //{"annotationIdent":6897878,"action":"select"}
    @PostMapping("/annotation_action.json")
    public ResponseEntity<String> add(
            @RequestBody JsonObject json
    ) {
        log.debug("REST request add annotation action");
        AnnotationDomain annotationDomain = annotationDomainRepository.findById(json.getJSONAttrLong("annotationIdent", 0L))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", json.getJSONAttrStr("annotationIdent")));
        return responseSuccess(annotationActionService.add(annotationDomain, currentUserService.getCurrentUser(), json.getJSONAttrStr("action"), new Date()));
    }

    @GetMapping("/imageinstance/{image}/annotation_action.json")
    public ResponseEntity<String> listByImage(
            @PathVariable("image") Long imageId,
            @RequestParam(value = "user", required = false) Long userId,
            @RequestParam(value = "afterThan", required = false) Long afterThan,
            @RequestParam(value = "beforeThan", required = false) Long beforeThan
    ) {
        ImageInstance image = imageInstanceService.find(imageId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance",imageId));

        User user = null;
        if (userId!=null) {
            user = secUserService.findUser(userId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", userId));
        }
        return responseSuccess(annotationActionService.list(image, user, afterThan, beforeThan));
    }

    @GetMapping("/sliceinstance/{slice}/annotation_action.json")
    public ResponseEntity<String> listBySlice(
            @PathVariable("slice") Long sliceId,
            @RequestParam(value = "user", required = false) Long userId,
            @RequestParam(value = "afterThan", required = false) Long afterThan,
            @RequestParam(value = "beforeThan", required = false) Long beforeThan
    ) {
        SliceInstance sliceInstance = sliceInstanceService.find(sliceId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstace", sliceId));

        User user = null;
        if (userId!=null) {
            user = secUserService.findUser(userId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", userId));
        }
        return responseSuccess(annotationActionService.list(sliceInstance, user, afterThan, beforeThan));
    }


    @GetMapping("/project/{project}/annotation_action/count.json")
    public ResponseEntity<String> countByProject(
            @PathVariable("project") Long projectId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate
    ) {
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", projectId));

        return responseSuccess(JsonObject.of("total", annotationActionService.countByProject(project, startDate, endDate)));
    }

}
