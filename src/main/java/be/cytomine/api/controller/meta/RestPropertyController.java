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
import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.ontology.AnnotationDomain;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.ontology.AnnotationDomainRepository;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.meta.PropertyService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.utils.GeometryUtils;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestPropertyController extends RestCytomineController {

    private final PropertyService propertyService;

    private final TransactionService transactionService;
    
    private final ProjectService projectService;
    
    private final ImageInstanceService imageInstanceService;

    private final AnnotationDomainRepository annotationDomainRepository;

    private final SecUserService secUserService;

    @GetMapping("/project/{project}/property.json")
    public ResponseEntity<String> listByProject(
            @PathVariable("project") Long projectId
    ) {
        log.debug("REST request to list property for project {}", projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(propertyService.list(project));
    }


    @GetMapping("/annotation/{annotation}/property.json")
    public ResponseEntity<String> listByAnnotation(
            @PathVariable("annotation") Long annotationId
    ) {
        log.debug("REST request to list property for annotation {}", annotationId);
        AnnotationDomain annotation = annotationDomainRepository.findById(annotationId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        return responseSuccess(propertyService.list(annotation));
    }

    @GetMapping("/imageinstance/{imageinstance}/property.json")
    public ResponseEntity<String> listByImageInstance(
            @PathVariable("imageinstance") Long imageInstanceId
    ) {
        log.debug("REST request to list property for imageInstance {}", imageInstanceId);
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , imageInstanceId));
        return responseSuccess(propertyService.list(imageInstance));
    }

    @GetMapping("/domain/{domainClassName}/{domainIdent}/property.json")
    public ResponseEntity<String> listByDomain(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent
    ) {
        log.debug("REST request to list property for {}} {}", domainClassName, domainIdent);
        CytomineDomain cytomineDomain = projectService.getCytomineDomain(domainClassName, domainIdent);
        if (cytomineDomain == null) {
            throw ObjectNotFoundException.notFoundException(domainClassName, domainIdent);
        }
        return responseSuccess(propertyService.list(cytomineDomain));
    }

    @GetMapping("/annotation/property/key.json")
    public ResponseEntity<String> listKeyForAnnotation(
            @RequestParam(value = "idProject", required = false) Long projectId,
            @RequestParam(value = "idImage", required = false) Long imageInstanceId,
            @RequestParam(value = "user", required = false, defaultValue = "false") Boolean withUser
    ) {
        log.debug("REST request to list keys for project {} image {} and withUser {}", projectId, imageInstanceId, withUser);
        if (projectId != null) {
            Project project = projectService.find(projectId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
            List<Map<String, Object>> maps = propertyService.listKeysForAnnotation(project, null, withUser);
            return responseSuccess(withUser ? maps : maps.stream().map(x -> x.get("key")).toList());
        }
        if (imageInstanceId != null) {
            ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , imageInstanceId));
            List<Map<String, Object>> maps = propertyService.listKeysForAnnotation(null, imageInstance, withUser);
            return responseSuccess(withUser ? maps : maps.stream().map(x -> x.get("key")).toList());
        }
        throw new WrongArgumentException("You must specify at least 'idProject' or 'idImage'");
    }

    @GetMapping("/imageinstance/property/key.json")
    public ResponseEntity<String> listKeyForImageInstance(
            @RequestParam(value = "idProject", required = true) Long projectId
    ) {
        log.debug("REST request to list keys for image in project {}", projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(propertyService.listKeysForImageInstance(project));
    }

    @GetMapping("/user/{user}/imageinstance/{image}/annotationposition.json")
    public ResponseEntity<String> listAnnotationPosition(
            @PathVariable(value = "user") Long userId,
            @PathVariable(value = "image") Long imageInstanceId,
            @RequestParam(value = "key", required = true) String key,
            @RequestParam(value = "bbox", required = false) String bbox
    ) throws ParseException {
        log.debug("REST request to list annotation position");
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , imageInstanceId));
        SecUser secUser = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUser" , userId));
        Geometry boundingbox = null;
        if (bbox!=null) {
            boundingbox = GeometryUtils.createBoundingBox(bbox);
        }
        return responseSuccess(propertyService.listAnnotationCenterPosition(secUser, imageInstance, boundingbox, key));
    }


    @GetMapping("/project/{project}/key/{key}/property.json")
    public ResponseEntity<String> showProject(
            @PathVariable("project") Long projectId,
            @PathVariable String key
    ) {
        log.debug("REST request to show property {} for project {}", key, projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(propertyService.findByDomainAndKey(project, key)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , projectId + "/" + key)));
    }
    
    @GetMapping("/project/{project}/property/{id}.json")
    public ResponseEntity<String> showProject(
            @PathVariable("project") Long projectId,
            @PathVariable Long id
    ) {
        log.debug("REST request to show property {} for project {}", id, projectId);
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
        return responseSuccess(propertyService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , projectId + "/" + id)));
    }

    @GetMapping("/imageinstance/{imageinstance}/key/{key}/property.json")
    public ResponseEntity<String> showImageInstance(
            @PathVariable("imageinstance") Long imageInstanceId,
            @PathVariable String key
    ) {
        log.debug("REST request to show property {} for imageInstance {}", key, imageInstanceId);
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , imageInstanceId));
        return responseSuccess(propertyService.findByDomainAndKey(imageInstance, key)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , imageInstanceId + "/" + key)));
    }

    @GetMapping("/imageinstance/{imageinstance}/property/{id}.json")
    public ResponseEntity<String> showImageInstance(
            @PathVariable("imageinstance") Long imageInstanceId,
            @PathVariable Long id
    ) {
        log.debug("REST request to show property {} for imageInstance {}", id, imageInstanceId);
        ImageInstance imageInstance = imageInstanceService.find(imageInstanceId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , imageInstanceId));
        return responseSuccess(propertyService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , imageInstanceId + "/" + id)));
    }

    @GetMapping("/annotation/{annotation}/key/{key}/property.json")
    public ResponseEntity<String> showAnnotation(
            @PathVariable("annotation") Long annotationId,
            @PathVariable String key
    ) {
        log.debug("REST request to show property {} for annotation {}", key, annotationId);
        AnnotationDomain annotation = annotationDomainRepository.findById(annotationId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        return responseSuccess(propertyService.findByDomainAndKey(annotation, key)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , annotationId + "/" + key)));
    }

    @GetMapping("/annotation/{annotation}/property/{id}.json")
    public ResponseEntity<String> showAnnotation(
            @PathVariable("annotation") Long annotationId,
            @PathVariable Long id
    ) {
        log.debug("REST request to show property {} for annotation {}", id, annotationId);
        AnnotationDomain annotation = annotationDomainRepository.findById(annotationId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        return responseSuccess(propertyService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , annotationId + "/" + id)));
    }

    @GetMapping("/domain/{domainClassName}/{domainIdent}/key/{key}/property.json")
    public ResponseEntity<String> showByDomain(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent,
            @PathVariable String key
    ) {
        log.debug("REST request to show property {} for domain {} {}", key, domainClassName, domainIdent);
        CytomineDomain domain = Optional.ofNullable(projectService.getCytomineDomain(domainClassName, domainIdent))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Domain", domainClassName + "/" + domainIdent));
        return responseSuccess(propertyService.findByDomainAndKey(domain, key)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , domain + "/" + key)));
    }

    @GetMapping("/domain/{domainClassName}/{domainIdent}/property/{id}.json")
    public ResponseEntity<String> showByDomain(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent,
            @PathVariable Long id
    ) {
        log.debug("REST request to show property {} for domain {} {}", id, domainClassName, domainIdent);
        CytomineDomain domain = Optional.ofNullable(projectService.getCytomineDomain(domainClassName, domainIdent))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Domain", domainClassName + "/" + domainIdent));
        return responseSuccess(propertyService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Property" , domain + "/" + id)));
    }

//
//    @PostMapping("/project/{project}/property.json")
//    public ResponseEntity<String> addPropertyProject(
//            @PathVariable("project") Long projectId,
//            @RequestBody JsonObject jsonObject
//    ) {
//        log.debug("REST request to add property for project {} {}", projectId, jsonObject.toJsonString());
//        Project project = projectService.find(projectId)
//                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
//        return responseSuccess(propertyService.add(jsonObject));
//    }
//
//    @PutMapping("/project/{project}/property/{id}.json")
//    public ResponseEntity<String> updatePropertyProject(
//            @PathVariable("project") Long projectId,
//            @PathVariable Long id,
//            @RequestBody JsonObject jsonObject
//    ) {
//        log.debug("REST request to delete property for project {} / {}", projectId, id);
//        Project project = projectService.find(projectId)
//                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
//        return update(propertyService, jsonObject);
//    }
//
//    @DeleteMapping("/project/{project}/property/{id}.json")
//    public ResponseEntity<String> deletePropertyProject(
//            @PathVariable("project") Long projectId,
//            @PathVariable Long id
//    ) {
//        log.debug("REST request to delete property for project {} / {}", projectId, id);
//        Project project = projectService.find(projectId)
//                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , projectId));
//        return delete(propertyService, JsonObject.of("id", id), null);
//    }

    @PostMapping("/domain/{domainClassName}/{domainIdent}/property.json")
    public ResponseEntity<String> addProperty(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent,
            @RequestBody JsonObject jsonObject
    ) {
        log.debug("REST request to add property for domain {} {}", domainClassName, domainIdent);
        CytomineDomain domain = Optional.ofNullable(projectService.getCytomineDomain(domainClassName, domainIdent))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Domain", domainClassName + "/" + domainIdent));
        jsonObject.putIfAbsent("domainClassName", domainClassName);
        jsonObject.putIfAbsent("domainIdent", domainIdent);
        return responseSuccess(propertyService.add(jsonObject));
    }

    @PostMapping("/property.json")
    public ResponseEntity<String> addProperty(
            @RequestBody JsonObject jsonObject
    ) {
        log.debug("REST request to add property for domain");
        return responseSuccess(propertyService.add(jsonObject));
    }

    @PutMapping("/domain/{domainClassName}/{domainIdent}/property/{id}.json")
    public ResponseEntity<String> updatePropertyProject(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent,
            @PathVariable Long id,
            @RequestBody JsonObject jsonObject
    ) {
        log.debug("REST request to delete property for domain {} / {}", domainClassName, domainIdent);
        return update(propertyService, jsonObject);
    }

    @DeleteMapping("/domain/{domainClassName}/{domainIdent}/property/{id}.json")
    public ResponseEntity<String> deletePropertyProject(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent,
            @PathVariable Long id
    ) {
        log.debug("REST request to delete property {}", id);
        return delete(propertyService, JsonObject.of("id", id), null);
    }
}
