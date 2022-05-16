package be.cytomine.domain.ontology;

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
import be.cytomine.domain.image.AbstractImage;
import be.cytomine.domain.image.ImageInstance;
import be.cytomine.domain.image.SliceInstance;
import be.cytomine.domain.project.Project;
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.CytomineMethodNotYetImplementedException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.dto.Point;
import be.cytomine.utils.GisUtils;
import be.cytomine.utils.JsonObject;
import com.vividsolutions.jts.geom.Geometry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Getter
@Setter
@Slf4j
public abstract class AnnotationDomain extends CytomineDomain implements Serializable {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slice_id", nullable = true)
    protected SliceInstance slice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    protected ImageInstance image; // Redundant with slice, used for speed up in security checks

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    protected Project project;

    @NotNull
    //@Type(type = "org.hibernate.spatial.JTSGeometryType")
    @Column(columnDefinition = "geometry")
    protected Geometry location;

    @NotBlank
    @NotNull
    protected String wktLocation; // Redundant, better to use this than getting WKT from location properties

    protected Double geometryCompression;

    protected Double area;

    Integer areaUnit;

    Double perimeter;

    Integer perimeterUnit;

    long countComments = 0L;

//    @Override
//    public String toJSON() {
//        return null;
//    }
//
//    @Override
//    public JsonObject toJsonObject() {
//        return null;
//    }

    public void beforeCreate() {
        if(project==null) {
            project = image.getProject();
        }

        // TODO: migration
//        if (slice==null) {
//            slice = image.getReferenceSlice
//        }
        this.computeGIS();
        wktLocation = location.toText();
    }

    public void beforeUpdate() {
        if(project==null) {
            project = image.getProject();
        }

        // TODO: migration
//        if (slice==null) {
//            slice = image.getReferenceSlice
//        }
        this.computeGIS();
        wktLocation = location.toText();
    }

    /**
     * Get all terms map with the annotation
     * @return Terms list
     */
    public abstract List<Term> terms();

    /**
     * Get all annotation terms id
     * @return Terms id list
     */
    public abstract List<Long> termsId();

    /**
     * Check if its an user annotation
     */
    public abstract boolean isUserAnnotation();

    /**
     * Check if its an algo annotation
     */
    public abstract boolean isAlgoAnnotation();

    /**
     * Check if its a review annotation
     */
    public abstract boolean isReviewedAnnotation();

    public abstract boolean isRoiAnnotation();

    /**
     * Get all terms for automatic review
     * If review is done "for all" (without manual user control), we add these term to the new review annotation
     * @return
     */
    public abstract List<Term> termsForReview();


    public String getFilename() {
        return Optional.ofNullable(this.image)
                .map(ImageInstance::getBaseImage)
                .map(AbstractImage::getOriginalFilename).orElse(null);
    }

    public String retrieveAreaUnit() {
        return GisUtils.retrieveUnit(areaUnit);
    }

    public String retrievePerimeterUnit() {
        return GisUtils.retrieveUnit(perimeterUnit);
    }

    public CytomineDomain container() {
        return project;
    }

    private void computeGIS() {
        if (this.image.getPhysicalSizeX() == null) {
            area = (double)Math.round(this.location.getArea());
            areaUnit = GisUtils.PIXELS2v;

            perimeter = (double)Math.round(this.location.getLength());
            perimeterUnit = GisUtils.PIXELv;
        }
        else {
            area = this.location.getArea() * image.getPhysicalSizeX() * image.getPhysicalSizeX();
            areaUnit = GisUtils.MICRON2v;

            perimeter = this.location.getLength() * image.getPhysicalSizeX() / 1000;
            perimeterUnit = GisUtils.MMv;
        }
    }

    public Point getCentroid() {
        com.vividsolutions.jts.geom.Point centroid = location.getCentroid();
        return new Point(centroid.getX(), centroid.getY());
    }


    // TODO: cannot be perform here with spring
//    /**
//     * Get user/algo/reviewed annotation with id
//     * Check the correct type and return it
//     * @param id Annotation id
//     * @return Annotation
//     */
//    public static AnnotationDomain getAnnotationDomain(String id, String className = null) {
//        try {
//            getAnnotationDomain(Long.parseLong(id), className)
//        } catch(NumberFormatException e) {
//            throw new ObjectNotFoundException("Annotation ${id} not found")
//        }
//    }
//

    public static Optional<AnnotationDomain> findAnnotationDomain(EntityManager entityManager, Long id) {
        return Optional.ofNullable(getAnnotationDomain(entityManager, id, ""));
    }
    public static AnnotationDomain getAnnotationDomain(EntityManager entityManager, Long id) {
        return getAnnotationDomain(entityManager, id, "");
    }

    /**
     * Get user/algo/reviewed annotation with id
     * Check the correct type and return it
     * @param id Annotation id
     * @return Annotation
     */
    public static AnnotationDomain getAnnotationDomain(EntityManager entityManager, Long id, String className) {
        // TODO: move in AnnotationDomainRepo
        Class domain = null;
        if (className!=null) {
            switch (className) {
                case "be.cytomine.domain.ontology.UserAnnotation":
                    domain = UserAnnotation.class;
                    break;
                case "be.cytomine.domain.ontology.AlgoAnnotation":
                    domain = AlgoAnnotation.class;
                    break;
                case "be.cytomine.domain.ontology.ReviewedAnnotation":
                    domain = ReviewedAnnotation.class;
                    break;
                case "be.cytomine.domain.processing.RoiAnnotation":
                    throw new CytomineMethodNotYetImplementedException("migration");
                    //domain = RoiAnnotation.class;
                    //break;
            }
        }


        AnnotationDomain annotation;
        if (domain!=null) {
            annotation = (AnnotationDomain)entityManager.find(domain, id);
        } else {
            annotation = entityManager.find(UserAnnotation.class, id);
            if (annotation==null) annotation = entityManager.find(AlgoAnnotation.class, id);
            if (annotation==null) annotation = entityManager.find(ReviewedAnnotation.class, id);
//            if (annotation==null) annotation = entityManager.find(RoiAnnotation.class, id);
        }

        if (annotation!=null) {
            return annotation;
        }
        else {
            throw ObjectNotFoundException.notFoundException("Annotation", id);
        }
    }

    abstract Long getUserId();

    public static JsonObject getDataFromDomain(CytomineDomain domain) {
        JsonObject returnArray = CytomineDomain.getDataFromDomain(domain);
        AnnotationDomain annotationDomain = (AnnotationDomain)domain;
        returnArray.put("slice", Optional.ofNullable(annotationDomain.getSlice()).map(CytomineDomain::getId).orElse(null));
        returnArray.put("image", Optional.ofNullable(annotationDomain.getImage()).map(CytomineDomain::getId).orElse(null));
        returnArray.put("project", Optional.ofNullable(annotationDomain.getProject()).map(CytomineDomain::getId).orElse(null));
        returnArray.put("user", annotationDomain.getUserId());

        returnArray.put("location", annotationDomain.location.toString()); //TODO: totext?
        returnArray.put("geometryCompression", annotationDomain.geometryCompression);
        returnArray.put("centroid", annotationDomain.getCentroid());

        returnArray.put("area", annotationDomain.getArea());
        returnArray.put("areaUnit", annotationDomain.retrieveAreaUnit());
        returnArray.put("perimeter", annotationDomain.getPerimeter());
        returnArray.put("perimeterUnit", annotationDomain.retrievePerimeterUnit());

        returnArray.put("term", annotationDomain.termsId());
        returnArray.put("nbComments", annotationDomain.countComments);

        return returnArray;
    }

    public abstract SecUser user();

}
