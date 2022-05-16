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
import be.cytomine.api.controller.utils.AnnotationListingBuilder;
import be.cytomine.domain.ontology.UserAnnotation;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.dto.JsonInput;
import be.cytomine.dto.JsonMultipleObject;
import be.cytomine.dto.JsonSingleObject;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.UserAnnotationListing;
import be.cytomine.service.ModelService;
import be.cytomine.service.dto.CropParameter;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.ontology.SharedAnnotationService;
import be.cytomine.service.ontology.UserAnnotationService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.report.ReportService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.io.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestUserAnnotationController extends RestCytomineController {

    private final UserAnnotationService userAnnotationService;

    private final ProjectService projectService;

    private final SecUserService secUserService;

    private final ReportService reportService;

    private final ImageServerService imageServerService;

    private final SharedAnnotationService sharedAnnotationService;

    private final AnnotationListingBuilder annotationListingBuilder;

    @GetMapping("/userannotation.json")
    public ResponseEntity<String> listLight(
    ) {
        log.debug("REST request to list user annotation light");
        return responseSuccess(userAnnotationService.listLight());
    }


    @GetMapping("/user/{idUser}/userannotation/count.json")
    public ResponseEntity<String> countByUser(
            @PathVariable(value = "idUser") Long idUser,
            @RequestParam(value="project", required = false) Long idProject
    ) {
        log.debug("REST request to count user annotation by user/project");
        SecUser user = secUserService.find(idUser)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", idUser));
        Project project = null;
        if (idProject!=null) {
            project = projectService.find(idProject)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , idProject));
        }
        return responseSuccess(JsonObject.of("total", userAnnotationService.count((User)user, project)));
    }


    @GetMapping("/project/{idProject}/userannotation/count.json")
    public ResponseEntity<String> countByProject(
            @PathVariable(value = "idProject") Long idProject,
            @RequestParam(value="startDate", required = false) Long startDate,
            @RequestParam(value="endDate", required = false) Long endDate
    ) {
        log.debug("REST request to count user annotation by user/project");
        Project project= projectService.find(idProject)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project" , idProject));
        Date start = (startDate!=null? new Date(startDate) : null);
        Date end = (endDate!=null? new Date(endDate) : null);
        return responseSuccess(JsonObject.of("total", userAnnotationService.countByProject(project, start, end)));
    }

    /**
     * Download a report (pdf, xls,...) with user annotation data from a specific project
     */
    @GetMapping("/project/{project}/userannotation/download")
    public void downloadDocumentByProject(
            @PathVariable Long project,
            @RequestParam String format,
            @RequestParam String terms,
            @RequestParam String users,
            @RequestParam String images,
            @RequestParam(required = false) Long beforeThan,
            @RequestParam(required = false) Long afterThan
    ) throws IOException {
        users = secUserService.fillEmptyUserIds(users, project);
        JsonObject params = mergeQueryParamsAndBodyParams();
        byte[] report = annotationListingBuilder.buildAnnotationReport(project, users, params, terms, format);
        responseReportFile(reportService.getAnnotationReportFileName(format, project), report, format);
    }

    /**
     * Add comment on an annotation to other user
     */
    @PostMapping("/userannotation/{annotation}/comment.json")
    public ResponseEntity<String> addComment(
            @PathVariable(value = "annotation") Long annotationId,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request to create comment for annotation : " + json);
        UserAnnotation annotation = userAnnotationService.find(annotationId)
                .orElseThrow(()-> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        json.put("annotationIdent", annotation.getId());
        json.put("annotationClassName", annotation.getClass().getName());
        return responseSuccess(sharedAnnotationService.add(json));
    }


    /**
     * Show a single comment for an annotation
     */
    @GetMapping("/userannotation/{annotation}/comment/{id}.json")
    public ResponseEntity<String> showComment(
            @PathVariable(value = "annotation") Long annotationId,
            @PathVariable(value = "id") Long commentId
    ) {
        log.debug("REST request to read comment {} for annotation {}", commentId, annotationId);
        UserAnnotation annotation = userAnnotationService.find(annotationId)
                .orElseThrow(()-> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        return responseSuccess(sharedAnnotationService.find(commentId).orElseThrow(() ->
                ObjectNotFoundException.notFoundException("SharedAnnotation", commentId)));
    }

    /**
     * List all comments for an annotation
     */
    @GetMapping("/userannotation/{annotation}/comment.json")
    public ResponseEntity<String> listComments(
            @PathVariable(value = "annotation") Long annotationId
    ) {
        log.debug("REST request to read comments for annotation {}", annotationId);
        UserAnnotation annotation = userAnnotationService.find(annotationId)
                .orElseThrow(()-> ObjectNotFoundException.notFoundException("Annotation", annotationId));
        return responseSuccess(sharedAnnotationService.listComments(annotation));
    }


    @GetMapping("/userannotation/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get Term : {}", id);
        return userAnnotationService.find(id)
                .map(this::responseSuccess)
                .orElseGet(() -> responseNotFound("UserAnnotation", id));
    }


    /**
     * Add a new term
     * Use next add relation-term to add relation with another term
     * @param json JSON with Term data
     * @return Response map with .code = http response code and .data.term = new created Term
     */
    @PostMapping("/userannotation.json")
    public ResponseEntity<String> add(
            @RequestBody String json,
            @RequestParam(required = false) Long minPoint,
            @RequestParam(required = false) Long maxPoint
    ) {
        log.debug("REST request to save user annotation");
        JsonInput data;
        try {
            data = new ObjectMapper().readValue(json, JsonMultipleObject.class);
            for (JsonObject datum : ((JsonMultipleObject) data)) {
                datum.putIfAbsent("minPoint", minPoint);
                datum.putIfAbsent("maxPoint", maxPoint);
            }
            // If fails to parse as a single object, parse as a list
        } catch (Exception ex) {
            try {
                data = new ObjectMapper().readValue(json, JsonSingleObject.class);
                ((JsonSingleObject)data).putIfAbsent("minPoint", minPoint);
                ((JsonSingleObject)data).putIfAbsent("maxPoint", maxPoint);
            } catch (JsonProcessingException e) {
                throw new WrongArgumentException("Json not valid");
            }
        }
        return add(userAnnotationService, data);
    }

    public CommandResponse addOne(ModelService service, JsonObject json) {
        if (json.isMissing("location")) {
            throw new WrongArgumentException("Annotation must have a valid geometry:" + json.get("location"));
        }
        return service.add(json);
    }


    @PutMapping("/userannotation/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit user annotation : " + id);
        return update(userAnnotationService, json);
    }

    @DeleteMapping("/userannotation/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id) {
        log.debug("REST request to delete an annotation : " + id);
        return delete(userAnnotationService, JsonObject.of("id", id), null);
    }


    @PostMapping("/userannotation/{id}/repeat.json")
    public ResponseEntity<String> repeat(
            @RequestBody JsonObject json,
            @PathVariable Long id
    ) {
        log.debug("REST request to repeat user annotation : {} ", id);

        UserAnnotation annotation = userAnnotationService.find(id)
                .orElseThrow(()-> ObjectNotFoundException.notFoundException("Annotation", id));
        return responseSuccess(userAnnotationService.repeat(
                annotation, json.getJSONAttrLong("repeat", 1L), json.getJSONAttrInteger("slice", null)));
    }


    @RequestMapping(value = "/userannotation/{id}/crop.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void crop(
            @PathVariable Long id,
            @PathVariable String format,
            @RequestParam(defaultValue = "256") Integer maxSize,
            @RequestParam(required = false) String geometry,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String boundaries,
            @RequestParam(defaultValue = "false") Boolean complete,
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) Double increaseArea,
            @RequestParam(required = false) Boolean safe,
            @RequestParam(required = false) Boolean square,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean draw,
            @RequestParam(required = false) Boolean mask,
            @RequestParam(required = false) Boolean alphaMask,
            @RequestParam(required = false) Boolean drawScaleBar,
            @RequestParam(required = false) Double resolution,
            @RequestParam(required = false) Double magnification,
            @RequestParam(required = false) String colormap,
            @RequestParam(required = false) Boolean inverse,
            @RequestParam(required = false) Double contrast,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) String bits,
            @RequestParam(required = false) Integer alpha,
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Integer jpegQuality
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request to get associated image of a abstract image");
        UserAnnotation userAnnotation = userAnnotationService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("UserAnnotation", id));

        CropParameter cropParameter = new CropParameter();
        cropParameter.setGeometry(geometry);
        cropParameter.setLocation(location);
        cropParameter.setComplete(complete);
        cropParameter.setZoom(zoom);
        cropParameter.setIncreaseArea(increaseArea);
        cropParameter.setSafe(safe);
        cropParameter.setSquare(square);
        cropParameter.setType(type);
        cropParameter.setDraw(draw);
        cropParameter.setMask(mask);
        cropParameter.setAlphaMask(alphaMask);
        cropParameter.setDrawScaleBar(drawScaleBar);
        cropParameter.setMaxSize(maxSize);
        cropParameter.setResolution(resolution);
        cropParameter.setMagnification(magnification);
        cropParameter.setColormap(colormap);
        cropParameter.setInverse(inverse);
        cropParameter.setGamma(gamma);
        cropParameter.setAlpha(alpha);
        cropParameter.setContrast(contrast);
        cropParameter.setThickness(thickness);
        cropParameter.setColor(color);
        cropParameter.setJpegQuality(jpegQuality);
        cropParameter.setMaxBits(bits!=null && bits.equals("max"));
        cropParameter.setBits(bits!=null && !bits.equals("max") ? Integer.parseInt(bits): null);
        cropParameter.setFormat(format);

        responseByteArray(imageServerService.crop(userAnnotation, cropParameter), format);
    }

    @RequestMapping(value = "/userannotation/{id}/mask.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void cropMask(
            @PathVariable Long id,
            @PathVariable String format,
            @RequestParam(defaultValue = "256") Integer maxSize,
            @RequestParam(required = false) String geometry,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String boundaries,
            @RequestParam(defaultValue = "false") Boolean complete,
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) Double increaseArea,
            @RequestParam(required = false) Boolean safe,
            @RequestParam(required = false) Boolean square,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean draw,
            @RequestParam(required = false) Boolean drawScaleBar,
            @RequestParam(required = false) Double resolution,
            @RequestParam(required = false) Double magnification,
            @RequestParam(required = false) String colormap,
            @RequestParam(required = false) Boolean inverse,
            @RequestParam(required = false) Double contrast,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) String bits,
            @RequestParam(required = false) Integer alpha,
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Integer jpegQuality
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request to get associated image of a abstract image");
        UserAnnotation userAnnotation = userAnnotationService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("UserAnnotation", id));

        CropParameter cropParameter = new CropParameter();
        cropParameter.setGeometry(geometry);
        cropParameter.setLocation(location);
        cropParameter.setComplete(complete);
        cropParameter.setMaxSize(maxSize);
        cropParameter.setZoom(zoom);
        cropParameter.setIncreaseArea(increaseArea);
        cropParameter.setSafe(safe);
        cropParameter.setSquare(square);
        cropParameter.setType(type);
        cropParameter.setDraw(draw);
        cropParameter.setMask(true);
        cropParameter.setAlphaMask(false);
        cropParameter.setDrawScaleBar(drawScaleBar);
        cropParameter.setResolution(resolution);
        cropParameter.setMagnification(magnification);
        cropParameter.setColormap(colormap);
        cropParameter.setInverse(inverse);
        cropParameter.setGamma(gamma);
        cropParameter.setAlpha(alpha);
        cropParameter.setContrast(contrast);
        cropParameter.setThickness(thickness);
        cropParameter.setColor(color);
        cropParameter.setJpegQuality(jpegQuality);
        cropParameter.setMaxBits(bits!=null && bits.equals("max"));
        cropParameter.setBits(bits!=null && !bits.equals("max") ? Integer.parseInt(bits): null);
        cropParameter.setFormat(format);

        responseByteArray(imageServerService.crop(userAnnotation, cropParameter), format);
    }

    @RequestMapping(value = "/userannotation/{id}/alphamask.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void cropAlphaMask(
            @PathVariable Long id,
            @PathVariable String format,
            @RequestParam(defaultValue = "256") Integer maxSize,
            @RequestParam(required = false) String geometry,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String boundaries,
            @RequestParam(defaultValue = "false") Boolean complete,
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) Double increaseArea,
            @RequestParam(required = false) Boolean safe,
            @RequestParam(required = false) Boolean square,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean draw,
            @RequestParam(required = false) Boolean drawScaleBar,
            @RequestParam(required = false) Double resolution,
            @RequestParam(required = false) Double magnification,
            @RequestParam(required = false) String colormap,
            @RequestParam(required = false) Boolean inverse,
            @RequestParam(required = false) Double contrast,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) String bits,
            @RequestParam(required = false) Integer alpha,
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Integer jpegQuality
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request to get associated image of a abstract image");
        UserAnnotation userAnnotation = userAnnotationService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("UserAnnotation", id));

        CropParameter cropParameter = new CropParameter();
        cropParameter.setGeometry(geometry);
        cropParameter.setLocation(location);
        cropParameter.setComplete(complete);
        cropParameter.setZoom(zoom);
        cropParameter.setIncreaseArea(increaseArea);
        cropParameter.setSafe(safe);
        cropParameter.setSquare(square);
        cropParameter.setType(type);
        cropParameter.setDraw(draw);
        cropParameter.setAlphaMask(true);
        cropParameter.setDrawScaleBar(drawScaleBar);
        cropParameter.setResolution(resolution);
        cropParameter.setMagnification(magnification);
        cropParameter.setColormap(colormap);
        cropParameter.setInverse(inverse);
        cropParameter.setGamma(gamma);
        cropParameter.setAlpha(alpha);
        cropParameter.setContrast(contrast);
        cropParameter.setThickness(thickness);
        cropParameter.setColor(color);
        cropParameter.setJpegQuality(jpegQuality);
        cropParameter.setMaxBits(bits!=null && bits.equals("max"));
        cropParameter.setBits(bits!=null && !bits.equals("max") ? Integer.parseInt(bits): null);
        cropParameter.setFormat(format);

        responseByteArray(imageServerService.crop(userAnnotation, cropParameter), format);
    }
}
