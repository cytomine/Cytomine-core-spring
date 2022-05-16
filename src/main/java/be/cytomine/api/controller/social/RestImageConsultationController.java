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
import be.cytomine.api.controller.utils.RequestParams;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ontology.TermService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.service.report.ReportService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.social.ImageConsultationService;
import be.cytomine.utils.JsonObject;
import liquibase.pro.packaged.D;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestImageConsultationController extends RestCytomineController {

    private final ImageConsultationService imageConsultationService;

    private final CurrentUserService currentUserService;

    private final ProjectService projectService;

    private final SecUserService secUserService;

    private final ReportService reportService;

    @PostMapping("/imageinstance/{id}/consultation.json")
    public ResponseEntity<String> add(
            @PathVariable("id") Long imageId,
            @RequestBody JsonObject json
    ) {
        log.info("add an image consultation for image {}", imageId);
        SecUser user = currentUserService.getCurrentUser();
        String session = RequestContextHolder.currentRequestAttributes().getSessionId();
        String mode = json.getJSONAttrStr("mode");
        return responseSuccess(imageConsultationService.add(user, imageId, session, mode, new Date()));
    }

    @GetMapping("/project/{project}/lastImages.json")
    public ResponseEntity<String> lastImageOfUsersByProject(
            @PathVariable("project") Long projectId
    ) {
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", projectId));
        return responseSuccess(imageConsultationService.lastImageOfUsersByProject(project, null, "created", "desc", 0L, 0L));
    }

    @GetMapping("/imageinstance/method/lastopened.json")
    public ResponseEntity<String> listLastOpenImage() {
        log.debug("REST request to get last image instance opened for user");
        RequestParams requestParams = retrievePageableParameters();
//        SecUser secUser = currentUserService.getCurrentUser();
        return responseSuccess(imageConsultationService.listLastOpened(requestParams.getMax()));
        //return responseSuccess(new ArrayList());
    }

    @GetMapping("/project/{project}/user/{user}/imageconsultation.json")
    public ResponseEntity<String> listImageConsultationByProjectAndUser(
            @PathVariable("project") Long projectId,
            @PathVariable("user") Long userId,
            @RequestParam(required = false, defaultValue = "false") Boolean distinctImages,
            @RequestParam(required = false, defaultValue = "0") Integer max,
            @RequestParam(required = false, defaultValue = "0") Integer offset
    ) {
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", projectId));
        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUser", userId));

        if (distinctImages) {
            return responseSuccess(imageConsultationService.listImageConsultationByProjectAndUserWithDistinctImage(project, user));
        } else {
            return responseSuccess(imageConsultationService.listImageConsultationByProjectAndUserNoImageDistinct(project, user, max, offset));
        }
    }

    @GetMapping("/project/{project}/imageconsultation/count.json")
    public ResponseEntity<String> countByProject(
            @PathVariable("project") Long projectId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate
    ) {
        Project project = projectService.find(projectId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", projectId));

        return responseSuccess(JsonObject.of("total", imageConsultationService.countByProject(project, startDate, endDate)));
    }

    @GetMapping("/imageconsultation/resume.json")
    public ResponseEntity<String> resumeByUserAndProject(
            @RequestParam(value = "user") Long userId,
            @RequestParam(value = "project") Long projectId,
            @RequestParam(required = false, value = "export") String export
    ) throws IOException {
        List<JsonObject> results = imageConsultationService.resumeByUserAndProject(userId, projectId);
        if (export!=null && export.equals("csv")) {

            Project project = projectService.find(projectId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", projectId));
            User user = secUserService.findUser(userId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("User", userId));

            byte[] report = reportService.generateImageConsultationReport(project.getName(), user.getUsername(), results);
            responseReportFile(reportService.getImageConsultationReportFileName(export, projectId, userId), report, export);
            return null;
        } else {
            return responseSuccess(results);
        }
    }

//
//    @RestApiMethod(description = "Summarize the consulted images for a given user and a given project")
//    @RestApiParams(params=[
//            @RestApiParam(name="user", type="long", paramType = RestApiParamType.QUERY, description = "The user id", required=true),
//            @RestApiParam(name="project", type="long", paramType = RestApiParamType.QUERY, description = "The project id", required=true),
//            @RestApiParam(name="export", type="string", paramType = RestApiParamType.QUERY, description = "The export format (supported: csv). Otherwise, return a json", required=false),
//    ])
//    def resumeByUserAndProject() {
//        def result = imageConsultationService.resumeByUserAndProject(Long.parseLong(params.user), Long.parseLong(params.project))
//
//        if(params.export.equals("csv")) {
//            Long user = Long.parseLong(params.user)
//            Long project = Long.parseLong(params.project)
//            SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyyMMdd_hhmmss");
//            String now = simpleFormat.format(new Date())
//            response.contentType = grailsApplication.config.grails.mime.types[params.format]
//            response.setHeader("Content-disposition", "attachment; filename=image_consultations_of_user_${user}_project_${project}_${now}.${params.export}")
//
//            def exporterIdentifier = params.export;
//            def exportResult = []
//            List fields = ["time", "first", "last", "frequency", "imageId", "imageName", "imageThumb", "numberOfCreatedAnnotations"]
//            Map labels = ["time": "Cumulated duration (ms)", "first" : "First consultation", "last" : "Last consultation", "frequency" :"Number of consultations","imageId": "Id of image", "imageName": "Name", "imageThumb": "Thumb", "numberOfCreatedAnnotations": "Number of created annotations"]
//            result.each {
//                def data = [:]
//                data.time = it.time ?: 0;
//                data.first = it.first
//                data.last = it.last
//                data.frequency = it.frequency
//                data.imageId = it.image
//                data.imageName = it.imageName
//                data.imageThumb = it.imageThumb
//                data.numberOfCreatedAnnotations = it.countCreatedAnnotations
//                exportResult << data
//            }
//
//            String title = "Consultations of images into project ${project} by user ${user}"
//            exportService.export(exporterIdentifier, response.outputStream, exportResult, fields, labels, null, ["column.widths": [0.12, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12, 0.12], "title": title, "csv.encoding": "UTF-8", "separator": ";"])
//        } else {
//            responseSuccess(result)
//        }
//    }
//



}
