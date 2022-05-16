package be.cytomine.service.ontology;

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

import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.command.*;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.ontology.*;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.security.User;
import be.cytomine.dto.AnnotationLight;
import be.cytomine.dto.SimplifiedAnnotation;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.AlgoAnnotationListing;
import be.cytomine.repository.image.ImageInstanceRepository;
import be.cytomine.repository.ontology.AlgoAnnotationRepository;
import be.cytomine.repository.ontology.SharedAnnotationRepository;
import be.cytomine.service.AnnotationListingService;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.ModelService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.dto.BoundariesCropParameter;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.SliceCoordinatesService;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.meta.PropertyService;
import be.cytomine.service.security.SecurityACLService;
import be.cytomine.service.utils.SimplifyGeometryService;
import be.cytomine.service.utils.ValidateGeometryService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.GeometryUtils;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.Task;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.security.acls.domain.BasePermission.DELETE;
import static org.springframework.security.acls.domain.BasePermission.READ;

@Slf4j
@Service
@Transactional
public class AlgoAnnotationService extends ModelService {

    @Autowired
    private AlgoAnnotationRepository algoAnnotationRepository;

    @Autowired
    private SecurityACLService securityACLService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private AnnotationListingService annotationListingService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SliceInstanceService sliceInstanceService;

    @Autowired
    private ImageInstanceRepository imageInstanceRepository;

    @Autowired
    private SimplifyGeometryService simplifyGeometryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ValidateGeometryService validateGeometryService;

    @Autowired
    private AlgoAnnotationTermService algoAnnotationTermService;

    @Autowired
    private SliceCoordinatesService sliceCoordinatesService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private AnnotationTrackService annotationTrackService;

    @Autowired
    private ReviewedAnnotationService reviewedAnnotationService;

    @Autowired
    private SharedAnnotationService sharedAnnotationService;

    @Autowired
    private SharedAnnotationRepository sharedAnnotationRepository;


    @Override
    public Class currentDomain() {
        return AlgoAnnotation.class;
    }

    @Override
    public CytomineDomain createFromJSON(JsonObject json) {
        return new AlgoAnnotation().buildDomainFromJson(json, entityManager);
    }

    public AlgoAnnotation get(Long id) {
        return find(id).orElse(null);
    }

    public Optional<AlgoAnnotation> find(Long id) {
        Optional<AlgoAnnotation> optionalAlgoAnnotation = algoAnnotationRepository.findById(id);
        optionalAlgoAnnotation.ifPresent(algoAnnotation -> securityACLService.check(algoAnnotation.container(),READ));
        return optionalAlgoAnnotation;
    }

    public Long countByProject(Project project) {
        return countByProject(project, null, null);
    }

    public Long countByProject(Project project, Date startDate, Date endDate) {
        if (startDate!=null && endDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedBetween(project, startDate, endDate);
        } else if (startDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedAfter(project, startDate);
        } else if (endDate!=null) {
            return algoAnnotationRepository.countByProjectAndCreatedBefore(project, endDate);
        } else {
            return algoAnnotationRepository.countByProject(project);
        }
    }


    public List list(Project project, List<String> propertiesToShow) {
        securityACLService.check(project,READ);
        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
        algoAnnotationListing.setProject(project.getId());
        return annotationListingService.executeRequest(algoAnnotationListing);
    }

    //TODO:
//    public List list(Job job, List<String> propertiesToShow) {
//        securityACLService.check(project,READ);
//        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
//        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
//        algoAnnotationListing.setProject(project.getId());
//        return annotationListingService.executeRequest(algoAnnotationListing);
//    }

    public List listIncluded(ImageInstance image, String geometry, SecUser user, List<Long> terms, AnnotationDomain annotation, List<String> propertiesToShow) {
        securityACLService.check(image.container(), READ);
        AlgoAnnotationListing algoAnnotationListing = new AlgoAnnotationListing(entityManager);
        algoAnnotationListing.setColumnsToPrint(propertiesToShow);
        algoAnnotationListing.setImage(image.getId());
        algoAnnotationListing.setUser(user.getId());
        algoAnnotationListing.setTerms(terms);
        algoAnnotationListing.setExcludedAnnotation((annotation!=null? annotation.getId() : null));
        algoAnnotationListing.setBbox(geometry);
        return annotationListingService.executeRequest(algoAnnotationListing);
    }

    /**
     * Add the new domain with JSON data
     * @param jsonObject New domain data
     * @return Response structure (created domain data,..)
     */
    @Override
    public CommandResponse add(JsonObject jsonObject) {
        SliceInstance slice = null;
        ImageInstance image = null;

        if (!jsonObject.isMissing("slice")) {

            Object sliceId = jsonObject.get("slice");
            slice = sliceInstanceService.find(jsonObject.getJSONAttrLong("slice"))
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "SliceInstance with id " + sliceId,
                            ErrorCode.NOT_FOUND_INSTANCE.getValue(),
                            Map.of("instance", "SliceInstance", "id", sliceId)));
            image = slice.getImage();
        } else if (!jsonObject.isMissing("image")) {

            Object imageId = jsonObject.get("image");
            image = imageInstanceRepository.findById(jsonObject.getJSONAttrLong("image"))
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "ImageInstance with id " + imageId,
                            ErrorCode.NOT_FOUND_INSTANCE.getValue(),
                            Map.of("instance", "ImageInstance", "id", imageId)));
            slice = sliceCoordinatesService.getReferenceSlice(image);

        }
        Project project = slice.getProject();

        if (jsonObject.isMissing("location")) {
            throw new WrongArgumentException("Annotation must have a valid geometry:" + jsonObject.get("location"));
        }

        jsonObject.put("slice", slice.getId());
        jsonObject.put("image", image.getId());
        jsonObject.put("project", project.getId());

        securityACLService.check(project, READ);
        securityACLService.checkIsNotReadOnly(project);

        SecUser currentUser = currentUserService.getCurrentUser();
        if (!currentUser.isAlgo()) {
            throw new WrongArgumentException("user "+currentUser+" is not an userjob");
        }
        jsonObject.put("user", currentUser.getId());



        Geometry annotationShape;
        try {
            annotationShape = new WKTReader().read(jsonObject.getJSONAttrStr("location"));
        }
        catch (Exception ignored) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid");
        }


        Envelope envelope = annotationShape.getEnvelopeInternal();
        boolean isSizeDefined = image.getBaseImage().getWidth()!=null && image.getBaseImage().getHeight()!=null;
        if (isSizeDefined && (envelope.getMinX() < 0 || envelope.getMinY() < 0 ||
                envelope.getMaxX() > image.getBaseImage().getWidth() ||
                envelope.getMaxY() > image.getBaseImage().getHeight())) {
            double maxX = Math.min(envelope.getMaxX(), image.getBaseImage().getWidth());
            double maxY = Math.min(envelope.getMaxY(), image.getBaseImage().getHeight());
            Geometry insideBounds = null;
            try {
                insideBounds = new WKTReader().read("POLYGON((0 0,0 " + maxY + "," + maxX + " " + maxY + "," + maxX + " 0,0 0))");
            } catch (ParseException e) {
                throw new WrongArgumentException("Annotation cannot be parsed with maxX/maxY:" + e.getMessage());
            }
            annotationShape = annotationShape.intersection(insideBounds);
        }

        if(!(annotationShape.getGeometryType().equals("LineString"))) {
            BoundariesCropParameter boundaries = GeometryUtils.getGeometryBoundaries(annotationShape);
            if (boundaries == null || boundaries.getWidth() == 0 || boundaries.getHeight() == 0) {
                throw new WrongArgumentException("Annotation dimension not valid");
            }
        }

        //simplify annotation
        try {
            SimplifiedAnnotation simplifiedAnnotation =
                    simplifyGeometryService.simplifyPolygon(annotationShape, jsonObject.getJSONAttrLong("minPoint", null), jsonObject.getJSONAttrLong("maxPoint", null));
            jsonObject.put("location", simplifiedAnnotation.getNewAnnotation());
            jsonObject.put("geometryCompression", simplifiedAnnotation.getRate());
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e);
        }

        if (jsonObject.isMissing("location")) {
            jsonObject.put("location", annotationShape);
            jsonObject.put("geometryCompression", 0.0d);
        }

        if (jsonObject.get("location") instanceof Geometry) {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid((Geometry)jsonObject.get("location")));
        } else {
            jsonObject.put("location", validateGeometryService.tryToMakeItValidIfNotValid(jsonObject.getJSONAttrStr("location")));
        }

        //Start transaction
        Transaction transaction = transactionService.start();

        CommandResponse commandResponse = executeCommand(new AddCommand(currentUser, transaction), null, jsonObject);
        AlgoAnnotation addedAnnotation = (AlgoAnnotation)commandResponse.getObject();

        if (addedAnnotation == null) {
            return commandResponse;
        }


        // Add annotation-term if any
        List<Long> termIds = new ArrayList<>();
        termIds.addAll(jsonObject.getJSONAttrListLong("term", new ArrayList<>()));
        termIds.addAll(jsonObject.getJSONAttrListLong("terms", new ArrayList<>()));

        List<Term> terms = new ArrayList<>();
        for (Long termId : termIds) {

            CommandResponse response = algoAnnotationTermService.addAlgoAnnotationTerm(addedAnnotation, termId, currentUser.getId(), currentUser, transaction);
            terms.add(((AlgoAnnotationTerm)(response.getObject())).getTerm());
        }

        ((Map<String, Object>)commandResponse.getData().get("annotation")).put("term", terms);

        // Add properties if any
        Map<String, String> properties = new HashMap<>();
        properties.putAll(jsonObject.getJSONAttrMapString("property", new HashMap<>()));
        properties.putAll(jsonObject.getJSONAttrMapString("properties", new HashMap<>()));

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            propertyService.addProperty(AlgoAnnotation.class.getName(), addedAnnotation.getId(), key, value, currentUser, transaction);
        }

        List<Long> tracksIds = new ArrayList<>();
        tracksIds.addAll(jsonObject.getJSONAttrListLong("track", new ArrayList<>()));
        tracksIds.addAll(jsonObject.getJSONAttrListLong("tracks", new ArrayList<>()));
        if (!tracksIds.isEmpty()) {

            List<AnnotationTrack> annotationTracks = new ArrayList<>();
            for (Long trackId : tracksIds) {
                CommandResponse response =
                        annotationTrackService.addAnnotationTrack(AlgoAnnotation.class.getName(), addedAnnotation.getId(), trackId, addedAnnotation.getSlice().getId(), transaction);
                annotationTracks.add((AnnotationTrack) response.getData().get("annotationtrack"));
            }
            ((Map<String, Object>)commandResponse.getData().get("annotation")).put("annotationTrack", annotationTracks);
            ((Map<String, Object>)commandResponse.getData().get("annotation")).put("track", annotationTracks.stream().map(x -> x.getTrack()).collect(Collectors.toList()));
        }

        return commandResponse;
    }

    protected void beforeAdd(CytomineDomain domain) {
        // this will be done in the PrePersist method ; but the validation is done before PrePersist
        ((AlgoAnnotation)domain).setWktLocation(((AlgoAnnotation)domain).getLocation().toText());
    }

    protected void afterAdd(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }


    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return Response structure (new domain data, old domain data..)
     */
    public CommandResponse update(CytomineDomain domain, JsonObject jsonNewData, Transaction transaction) {
        SecUser currentUser = currentUserService.getCurrentUser();
        //securityACLService.checkIsSameUserOrAdminContainer(annotation,annotation.user,currentUser)
        securityACLService.checkFullOrRestrictedForOwner(domain, ((AlgoAnnotation)domain).getUser());

        // TODO: what about image/project ??
        AlgoAnnotation annotation = (AlgoAnnotation)domain;
        ImageInstance image = annotation.getImage();

        Geometry annotationShape;
        try {
            annotationShape = new WKTReader().read(jsonNewData.getJSONAttrStr("location"));
        }
        catch (ParseException ignored) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid");
        }

        Envelope envelope = annotationShape.getEnvelopeInternal();
        boolean isSizeDefined = image.getBaseImage().getWidth()!=null && image.getBaseImage().getHeight()!=null;
        if (isSizeDefined && (envelope.getMinX() < 0 || envelope.getMinY() < 0 ||
                envelope.getMaxX() > image.getBaseImage().getWidth() ||
                envelope.getMaxY() > image.getBaseImage().getHeight())) {
            double maxX = Math.min(envelope.getMaxX(), image.getBaseImage().getWidth());
            double maxY = Math.min(envelope.getMaxY(), image.getBaseImage().getHeight());
            Geometry insideBounds = null;
            try {
                insideBounds = new WKTReader().read("POLYGON((0 0,0 " + maxY + "," + maxX + " " + maxY + "," + maxX + " 0,0 0))");
            } catch (ParseException e) {
                throw new WrongArgumentException("Annotation cannot be parsed with maxX/maxY:" + e.getMessage());
            }
            annotationShape = annotationShape.intersection(insideBounds);
        }


        //simplify annotation
        try {
            double rate = jsonNewData.getJSONAttrDouble("geometryCompression", 0d);
            SimplifiedAnnotation data = simplifyGeometryService.simplifyPolygon(annotationShape, rate);
            jsonNewData.put("location", data.getNewAnnotation());
            jsonNewData.put("geometryCompression", data.getRate());
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e);
        }

        CommandResponse result = executeCommand(new EditCommand(currentUser, null), domain, jsonNewData);
        return result;
    }


    protected void afterUpdate(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }



    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    @Override
    public CommandResponse delete(CytomineDomain domain, Transaction transaction, Task task, boolean printMessage) {
        SecUser currentUser = currentUserService.getCurrentUser();
        securityACLService.check(domain.container(),DELETE);
        Command c = new DeleteCommand(currentUser, transaction);
        return executeCommand(c,domain, null);
    }


    protected void afterDelete(CytomineDomain domain, CommandResponse response) {
        response.getData().put("annotation", response.getData().get("algoannotation"));
        response.getData().remove("algoannotation");
    }


    public List<Object> getStringParamsI18n(CytomineDomain domain) {
        AlgoAnnotation annotation = (AlgoAnnotation)domain;
        return List.of(currentUserService.getCurrentUser().toString(), annotation.getImage().getBlindInstanceFilename(), ((AlgoAnnotation) domain).getUser().toString());
    }

    public void deleteDependencies(CytomineDomain domain, Transaction transaction, Task task) {
        deleteDependentAlgoAnnotationTerm((AlgoAnnotation)domain, transaction, task);
        deleteDependentReviewedAnnotation((AlgoAnnotation)domain, transaction, task);
        deleteDependentSharedAnnotation((AlgoAnnotation)domain, transaction, task);
        deleteDependentAnnotationTrack((AlgoAnnotation)domain, transaction, task);
    }

    public void deleteDependentAlgoAnnotationTerm(AlgoAnnotation ao, Transaction transaction, Task task) {
        for (AlgoAnnotationTerm algoAnnotationTerm : algoAnnotationTermService.list(ao)) {
            algoAnnotationTermService.delete(algoAnnotationTerm,transaction,task,false);
        }
    }
    public void deleteDependentReviewedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task) {
        Optional<ReviewedAnnotation> reviewed = reviewedAnnotationService.findByParent(aa);
        reviewed.ifPresent(reviewedAnnotation -> reviewedAnnotationService.delete(reviewedAnnotation, transaction, task, false));
    }
    public void deleteDependentSharedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task) {
        for (SharedAnnotation sharedAnnotation : sharedAnnotationRepository.findAllByAnnotationIdentOrderByCreatedDesc(aa.getId())) {
            sharedAnnotationService.delete(sharedAnnotation,transaction,task,false);
        }
    }
    public void deleteDependentAnnotationTrack(AlgoAnnotation ua, Transaction transaction, Task task) {
        for (AnnotationTrack annotationTrack : annotationTrackService.list(ua)) {
            annotationTrackService.delete(annotationTrack, transaction, task, false);
        }
    }

}
