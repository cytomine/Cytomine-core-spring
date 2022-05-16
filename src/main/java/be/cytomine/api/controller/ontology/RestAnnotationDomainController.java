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
import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.security.SecUser;
import be.cytomine.dto.SimplifiedAnnotation;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.*;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.dto.CropParameter;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.ontology.AlgoAnnotationService;
import be.cytomine.service.ontology.GenericAnnotationService;
import be.cytomine.service.ontology.ReviewedAnnotationService;
import be.cytomine.service.ontology.UserAnnotationService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.utils.ParamsService;
import be.cytomine.service.utils.SimplifyGeometryService;
import be.cytomine.utils.GeometryUtils;
import be.cytomine.utils.JsonObject;
import com.vividsolutions.jts.io.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestAnnotationDomainController extends RestCytomineController {

    private final AnnotationListingService annotationListingService;

    private final GenericAnnotationService genericAnnotationService;

    private final SecUserService secUserService;

    private final EntityManager entityManager;
    
    private final ParamsService paramsService;

    private final RestUserAnnotationController restUserAnnotationController;

    private final RestAlgoAnnotationController restAlgoAnnotationController;

    private final RestReviewedAnnotationController restReviewedAnnotationController;

    private final ImageServerService imageServerService;

    private final ImageInstanceService imageInstanceService;

    private final ReviewedAnnotationService reviewedAnnotationService;

    private final UserAnnotationService userAnnotationService;

    private final AlgoAnnotationService algoAnnotationService;

    private final SimplifyGeometryService simplifyGeometryService;

    private final AnnotationListingBuilder annotationListingBuilder;

    /**
     * List all ontology visible for the current user
     * For each ontology, print the terms tree
     */

    @RequestMapping(value = { "/annotation/search.json"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> searchSpecified() throws IOException {
        return search();
    }

    @RequestMapping(value = {"/annotation.json"}, method = {RequestMethod.GET})
    public ResponseEntity<String> search() throws IOException {
        JsonObject params = mergeQueryParamsAndBodyParams();
        AnnotationListing annotationListing = annotationListingBuilder.buildAnnotationListing(params);
        List annotations = annotationListingService.listGeneric(annotationListing);
        if (annotationListing instanceof AlgoAnnotationListing) {
            //if algo, we look for user_annotation JOIN algo_annotation_term  too
            params.put("suggestedTerm", params.get("term"));
            params.remove("term");
            params.remove("usersForTermAlgo");
            annotationListing = annotationListingBuilder.buildAnnotationListing(new UserAnnotationListing(entityManager), params);
            annotations.addAll(annotationListingService.listGeneric(annotationListing));
        }

        return responseSuccess(annotations, params.getJSONAttrLong("offset", 0L),params.getJSONAttrLong("max", 0L));
    }

    @RequestMapping(value = {"/project/{project}/annotation/download"}, method = {RequestMethod.GET})
    public void download(
            @PathVariable Long project,
            @RequestParam String format,
            @RequestParam(required = false) String users,
            @RequestParam(required = false) String reviewUsers,
            @RequestParam Boolean reviewed,
            @RequestParam String terms,
            @RequestParam String images,
            @RequestParam(required = false) Long beforeThan,
            @RequestParam(required = false) Long afterThan
    ) throws IOException {
        if(reviewed) {
            restReviewedAnnotationController.downloadDocumentByProject(project, format, terms, reviewUsers, images, beforeThan, afterThan);
        }
        else {
            if (!users.isEmpty() && false) { // SecUser.read(users.first()).algo()
                restAlgoAnnotationController.downloadDocumentByProject(project, format, terms, users, images, beforeThan, afterThan);
            } else {
                restUserAnnotationController.downloadDocumentByProject(project, format, terms, users, images, beforeThan, afterThan);
            }
        }
    }

    @RequestMapping(value = "/annotation/{id}/crop.{format}", method = {RequestMethod.GET, RequestMethod.POST})
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
        log.debug("REST request to get crop for annotation domain");
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);

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
        cropParameter.setResolution(resolution);
        cropParameter.setMagnification(magnification);
        cropParameter.setColormap(colormap);
        cropParameter.setInverse(inverse);
        cropParameter.setGamma(gamma);
        cropParameter.setMaxSize(maxSize);
        cropParameter.setAlpha(alpha);
        cropParameter.setContrast(contrast);
        cropParameter.setThickness(thickness);
        cropParameter.setColor(color);
        cropParameter.setJpegQuality(jpegQuality);
        cropParameter.setMaxBits(bits!=null && bits.equals("max"));
        cropParameter.setBits(bits!=null && !bits.equals("max") ? Integer.parseInt(bits): null);
        cropParameter.setFormat(format);

        responseByteArray(imageServerService.crop(annotation, cropParameter), format);
    }

    @GetMapping(value = {"/annotation/{id}/cropParameters.{format}"})
    public ResponseEntity<String> cropParameters(
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
    ) throws IOException, ParseException {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);

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


        LinkedHashMap<String, Object> result = imageServerService.cropParameters(annotation, cropParameter);
        result.put("location", result.get("location").toString());
        return responseSuccess(JsonObject.toJsonString(result));
    }


//            @RestApiParam(name="idImage", type="long", paramType = RestApiParamType.QUERY,description = "The image id"),
//            @RestApiParam(name="geometry", type="string", paramType = RestApiParamType.QUERY,description = "(Optional) WKT form of the geometry (if not set, set annotation param)"),
//            @RestApiParam(name="annotation", type="long", paramType = RestApiParamType.QUERY,description = "(Optional) The annotation id for the geometry (if not set, set geometry param)"),
//            @RestApiParam(name="user", type="long", paramType = RestApiParamType.QUERY,description = "The annotation user id (may be an algo) "),
//            @RestApiParam(name="terms", type="list", paramType = RestApiParamType.QUERY,description = "The annotation terms id")
//            ])
    @GetMapping("/imageinstance/{image}/annotation/included.json")
    public ResponseEntity<String> listIncludedAnnotation(
            @PathVariable(name="image") Long imageId
    ) throws IOException {
        JsonObject jsonObject = mergeQueryParamsAndBodyParams();
        jsonObject.put("image", imageId);
        return responseSuccess(getIncludedAnnotation(
                jsonObject,
                paramsService.getPropertyGroupToShow(jsonObject)
        ));
    }

    //TODO
//    @RestApiMethod(description="Get all annotation that intersect a geometry or another annotation. Unlike the simple list, extra parameter (show/hide) are not available. ")
//    @RestApiResponseObject(objectIdentifier = "file")
//    @RestApiParams(params=[
//            @RestApiParam(name="idImage", type="long", paramType = RestApiParamType.QUERY,description = "The image id"),
//            @RestApiParam(name="geometry", type="string", paramType = RestApiParamType.QUERY,description = "(Optional) WKT form of the geometry (if not set, set annotation param)"),
//            @RestApiParam(name="annotation", type="long", paramType = RestApiParamType.QUERY,description = "(Optional) The annotation id for the geometry (if not set, set geometry param)"),
//            @RestApiParam(name="user", type="long", paramType = RestApiParamType.QUERY,description = "The annotation user id (may be an algo) "),
//            @RestApiParam(name="terms", type="list", paramType = RestApiParamType.QUERY,description = "The annotation terms id")
//            ])
//    def downloadIncludedAnnotation() {
//        ImageInstance image = imageInstanceService.read(params.long('idImage'))
//        def lists = getIncludedAnnotation(params,['basic','meta','gis','image','term'])
//        downloadPdf(lists, image.project)
//    }

    private List getIncludedAnnotation(JsonObject params, List<String> propertiesToShow){

        ImageInstance image = imageInstanceService.find(params.getJSONAttrLong("image"))
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , params.getJSONAttrStr("image")));

        //get area
        String geometry = params.getJSONAttrStr("geometry");
        AnnotationDomain annotation = null;
        if(geometry==null) {
            annotation = AnnotationDomain.getAnnotationDomain(entityManager, params.getJSONAttrLong("annotation"));
            geometry = annotation.getLocation().toText();
        }

        //get user
        Long idUser = params.getJSONAttrLong("user");
        SecUser user = null;
        if (idUser!=0) {
            user = secUserService.find(params.getJSONAttrLong("user")).orElse(null);
        }

        //get term
        List<Long> terms = paramsService.getParamsTermList(params.getJSONAttrStr("terms"),image.getProject());

        List response;
        if(user==null) {
            //goto reviewed
            response = reviewedAnnotationService.listIncluded(image,geometry,terms,annotation,propertiesToShow);
        } else if (user.isAlgo()) {
            //goto algo
            response = algoAnnotationService.listIncluded(image,geometry,user,terms,annotation,propertiesToShow);
        }  else {
            //goto user annotation
            response = userAnnotationService.listIncluded(image,geometry,user,terms,annotation,propertiesToShow);
        }
        return response;
    }



    /**
     * Read a specific annotation
     * It's better to avoid the user of this method if we know the correct type of an annotation id
     * Annotation x => annotation/x.json is slower than userannotation/x.json or algoannotation/x.json
     */
    @RequestMapping(value = "/annotation/{id}.json", method = {RequestMethod.GET})
    public ResponseEntity<String> show(@PathVariable Long id) throws IOException {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
        if (annotation.isUserAnnotation()) {
            return restUserAnnotationController.show(id);
        } else if (annotation.isAlgoAnnotation()) {
            return restAlgoAnnotationController.show(id);
        } else if (annotation.isReviewedAnnotation()) {
            return restReviewedAnnotationController.show(id);
        } else {
            throw new CytomineMethodNotYetImplementedException("ROI annotation not yet implemented");
            // TODO
        }
    }

    /**
     * Add an annotation
     * Redirect to the controller depending on the user type
     */
    @RequestMapping(value = "/annotation.json", method = {RequestMethod.POST})
    public ResponseEntity<String> add(@RequestBody String json,
                                      @RequestParam(required = false, defaultValue = "false") Boolean roi,
                                      @RequestParam(required = false) Long minPoint,
                                      @RequestParam(required = false) Long maxPoint
    ) throws IOException {
        log.debug("REST request to create new annotation(s)");
        SecUser secUser = secUserService.getCurrentUser();
        if(roi) {
            throw new CytomineMethodNotYetImplementedException("ROI annotation not yet implemented");
        } else if (secUser.isAlgo()) {
            return restAlgoAnnotationController.add(json, minPoint, maxPoint);
        } else {
            ResponseEntity<String> response = restUserAnnotationController.add(json, minPoint, maxPoint);
            log.debug("REST request to create new annotation(s) finished");
            return response;
        }
    }

    /**
     * Update an annotation
     * Redirect to the good controller with the annotation type
     */
    @RequestMapping(value = "/annotation/{id}.json", method = {RequestMethod.PUT})
    public ResponseEntity<String> update(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") Boolean fill,
            @RequestBody JsonObject jsonObject

    ) throws IOException {
        if (fill) {
            return fillAnnotation(id);
        } else {
            AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
            if (annotation.isUserAnnotation()) {
                return restUserAnnotationController.edit(id.toString(), jsonObject);
            } else if (annotation.isAlgoAnnotation()) {
                return restAlgoAnnotationController.edit(id.toString(), jsonObject);
            } else if (annotation.isReviewedAnnotation()) {
                return restReviewedAnnotationController.edit(id.toString(), jsonObject);
            } else {
                throw new CytomineMethodNotYetImplementedException("ROI annotation not yet implemented");
                // TODO
            }
        }
    }


    /**
     * Delete an annotation
     * Redirect to the good controller with the current user type
     */
    @RequestMapping(value = "/annotation/{id}.json", method = {RequestMethod.DELETE})
    public ResponseEntity<String> delete(
            @PathVariable Long id
    ) throws IOException {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
        if (annotation.isUserAnnotation()) {
            return restUserAnnotationController.delete(id.toString());
        } else if (annotation.isAlgoAnnotation()) {
            return restAlgoAnnotationController.delete(id.toString());
        } else if (annotation.isReviewedAnnotation()) {
            return restReviewedAnnotationController.delete(id.toString());
        } else {
            throw new CytomineMethodNotYetImplementedException("ROI annotation not yet implemented");
            // TODO
        }
    }

    @RequestMapping(value = "/annotation/{id}/simplify.json", method = {RequestMethod.PUT})
    public ResponseEntity<String> simplify(
            @PathVariable Long id,
            @RequestParam(required = false) Long minPoint,
            @RequestParam(required = false) Long maxPoint

    )  {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);
        SimplifiedAnnotation simplifiedAnnotation = simplifyGeometryService.simplifyPolygon(annotation.getLocation(), minPoint, maxPoint);
        annotation.setLocation(simplifiedAnnotation.getNewAnnotation());
        annotation.setGeometryCompression(simplifiedAnnotation.getRate());
        userAnnotationService.saveDomain(annotation);
        return responseSuccess(annotation);
    }

    @RequestMapping(value = "/simplify.json", method = {RequestMethod.PUT})
    public ResponseEntity<String> retrieveSimplify(
            @RequestBody JsonObject jsonObject,
            @RequestParam(required = false) Long minPoint,
            @RequestParam(required = false) Long maxPoint

    )  {
        SimplifiedAnnotation simplifiedAnnotation = simplifyGeometryService.simplifyPolygon(jsonObject.getJSONAttrStr("wkt"), minPoint, maxPoint);
        return responseSuccess(JsonObject.of("wkt", simplifiedAnnotation.getNewAnnotation().toText()));
    }


//TODO
//
//    @RequestMapping(value = "/annotation/{id}/profile.json", method = {RequestMethod.GET})
//    public ResponseEntity<String> profile(
//            @PathVariable Long id
//
//    )  {
//        try {
//            AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(params.long('id'))
//            if (!annotation) {
//                throw new ObjectNotFoundException("Annotation ${params.long('id')} not found!")
//            }
//
//            if (!annotation.image.baseImage.hasProfile()) {
//                throw new ObjectNotFoundException("No profile for abstract image ${annotation.image.baseImage}")
//            }
//
//            CompanionFile cf = CompanionFile.findByImageAndType(annotation.image.baseImage, "HDF5")
//
//            responseSuccess(imageServerService.profile(cf, annotation, params))
//        }
//        catch (CytomineException e) {
//            responseError(e)
//        }
//    }


    /**
     * Fill an annotation.
     * Remove empty space in the polygon
     */
    @RequestMapping(value = "/annotation/{id}/fill.json", method = {RequestMethod.POST}) // TODO: should be PUT
    public ResponseEntity<String> fillAnnotation(
            @PathVariable Long id
    ) throws IOException {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(entityManager, id);

        //Is the first polygon always the big 'boundary' polygon?
        String newGeom = GeometryUtils.fillPolygon(annotation.getLocation().toText());
        JsonObject jsonObject = annotation.toJsonObject()
                .withChange("location", newGeom);

        if (annotation.isUserAnnotation()) {
            return responseSuccess(userAnnotationService.update(annotation, jsonObject));
        } else if (annotation.isAlgoAnnotation()) {
            return responseSuccess(algoAnnotationService.update(annotation, jsonObject));
        } else  {
            return responseSuccess(reviewedAnnotationService.update(annotation, jsonObject));
        }
    }

    /**
     * Add/Remove a geometry Y to/from the annotation geometry X.
     * Y must have intersection with X
     */
    @PostMapping("/annotationcorrection.json")
    public ResponseEntity<String> addCorrection(
            @RequestBody JsonObject jsonObject
    ) throws ParseException {
//        def json = request.JSON
//        String location = json.location
//        boolean review = json.review
//        Long idImage = json.image
//        boolean remove = json.remove
//        def layers = json.layers
//        Long idAnnotation = json.annotation
        String location = jsonObject.getJSONAttrStr("location");
        List<Long> layers = jsonObject.getJSONAttrListLong("layers");
        Long image = jsonObject.getJSONAttrLong("image");
        Boolean remove = jsonObject.getJSONAttrBoolean("remove", false);

        List<Long> idsReviewedAnnotation = new ArrayList<>();
        List<Long> idsUserAnnotation = new ArrayList<>();
        if (jsonObject.containsKey("annotation")) {
            if (jsonObject.getJSONAttrBoolean("review", false)) {
                idsReviewedAnnotation.add(jsonObject.getJSONAttrLong("annotation"));
            } else {
                idsUserAnnotation.add(jsonObject.getJSONAttrLong("annotation"));
            }
        } else {

            //if review mode, priority is done to reviewed annotation correction
            if (jsonObject.getJSONAttrBoolean("review", false)) {
                idsReviewedAnnotation = genericAnnotationService.findAnnotationThatTouch(location, layers, image, "reviewed_annotation")
                        .stream().map(CytomineDomain::getId).collect(Collectors.toList());
            }

            //there is no reviewed intersect annotation or user is not in review mode
            if (idsReviewedAnnotation.isEmpty()) {
                idsUserAnnotation = genericAnnotationService.findAnnotationThatTouch(location, layers, image, "user_annotation")
                        .stream().map(CytomineDomain::getId).collect(Collectors.toList());
            }
        }
        log.info("idsReviewedAnnotation="+idsReviewedAnnotation);
        log.info("idsUserAnnotation="+idsUserAnnotation);


        //there is no user/reviewed intersect
        if (idsUserAnnotation.isEmpty() && idsReviewedAnnotation.isEmpty()) {
            throw new WrongArgumentException("There is no intersect annotation!");
        }

        if (idsUserAnnotation.isEmpty()) {
            return responseSuccess(reviewedAnnotationService.doCorrectReviewedAnnotation(idsReviewedAnnotation, location, remove));
        } else {
            return responseSuccess(userAnnotationService.doCorrectUserAnnotation(idsUserAnnotation, location, remove));
        }

    }
}
