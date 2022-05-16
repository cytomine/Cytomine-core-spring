package be.cytomine.api.controller.image;

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
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.dto.CropParameter;
import be.cytomine.service.dto.ImageParameter;
import be.cytomine.service.dto.WindowParameter;
import be.cytomine.service.image.*;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.project.ProjectService;
import be.cytomine.utils.JsonObject;
import com.vividsolutions.jts.io.ParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestSliceInstanceController extends RestCytomineController {

    private final SliceInstanceService sliceInstanceService;

    private final ImageInstanceService imageInstanceService;

    private final ProjectService projectService;

    private final ImageServerService imageServerService;

    private final UploadedFileService uploadedFileService;

    private final SliceCoordinatesService sliceCoordinatesService;

    private final ImagePropertiesService imagePropertiesService;


    @GetMapping("/imageinstance/{id}/sliceinstance.json")
    public ResponseEntity<String> listByImageInstance(
            @PathVariable Long id
    ) {
        log.debug("REST request to list slice instance for image {}", id);
        ImageInstance imageInstance = imageInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , id));
        return responseSuccess(sliceInstanceService.list(imageInstance));
    }


    @GetMapping("/sliceinstance/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get slice instance {}", id);
        return sliceInstanceService.find(id)
                .map(this::responseSuccess)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
    }


    @GetMapping("/imageinstance/{id}/{channel}/{zStack}/{time}/sliceinstance.json")
    public ResponseEntity<String> getByImageInstanceAndCoordinates(
            @PathVariable Long id,
            @PathVariable Integer channel,
            @PathVariable Integer zStack,
            @PathVariable Integer time
    ) {
        log.debug("REST request to get slice instance for  image {} and coordinates {}-{}-{}", id, channel, zStack, time);
        ImageInstance imageInstance = imageInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance" , id));

        SliceInstance sliceInstance = sliceInstanceService.find(imageInstance, channel,zStack, time)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance [" + channel + "-" + zStack + "-" + time + "]" , id));
        return responseSuccess(sliceInstance);
    }
    
    @PostMapping("/sliceinstance.json")
    public ResponseEntity<String> add(@RequestBody String json) {
        log.debug("REST request to save sliceinstance : " + json);
        return add(sliceInstanceService, json);
    }

    @PutMapping("/sliceinstance/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit sliceinstance : " + id);
        return update(sliceInstanceService, json);
    }

    @DeleteMapping("/sliceinstance/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id) {
        log.debug("REST request to delete sliceinstance : " + id);
        return delete(sliceInstanceService, JsonObject.of("id", id), null);
    }

//
//    // TODO:MIGRATION GET params vs POST params!
    @RequestMapping(value = "/sliceinstance/{id}/thumb.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void thumb(
            @PathVariable Long id,
            @PathVariable String format,
            @RequestParam(required = false) Boolean refresh,
            @RequestParam(defaultValue = "512", required = false) Integer maxSize,
            @RequestParam(required = false) String colormap,
            @RequestParam(required = false) Boolean inverse,
            @RequestParam(required = false) Double contrast,
            @RequestParam(required = false) Double gamma,
            @RequestParam(required = false) String bits

    ) {
        log.debug("REST request get sliceinstance {} thumb {}", id, format);
        ImageParameter thumbParameter = new ImageParameter();
        thumbParameter.setFormat(format);
        thumbParameter.setMaxSize(maxSize);
        thumbParameter.setColormap(colormap);
        thumbParameter.setInverse(inverse);
        thumbParameter.setContrast(contrast);
        thumbParameter.setGamma(gamma);
        thumbParameter.setMaxBits(bits!=null && bits.equals("max"));
        thumbParameter.setBits(bits!=null && !bits.equals("max") ? Integer.parseInt(bits): null);
        thumbParameter.setRefresh(refresh);

        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
        responseByteArray(imageServerService.thumb(sliceInstance, thumbParameter), format);
    }

    @RequestMapping(value = "/sliceinstance/{id}/crop.{format}", method = {RequestMethod.GET, RequestMethod.POST})
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
        log.debug("REST request to get associated image of a slice instance");
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));

        CropParameter cropParameter = new CropParameter();
        cropParameter.setGeometry(geometry);
        cropParameter.setLocation(location);
//        cropParameter.setBoundaries(boundaries);
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

        responseByteArray(imageServerService.crop(sliceInstance.getBaseSlice(), cropParameter), format);
    }

    @RequestMapping(value = "/sliceinstance/{id}/window_url-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> windowUrl(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h,
            @RequestParam(defaultValue = "false", required = false) Boolean withExterior
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get sliceinstance {} window url {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(withExterior);
        windowParameter.setFormat(format);
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
        String url = imageServerService.windowUrl(sliceInstance.getBaseSlice(), windowParameter);
        return responseSuccess(JsonObject.of("url", url));
    }

    @RequestMapping(value = "/sliceinstance/{id}/window-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void window(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h,
            @RequestParam(defaultValue = "false", required = false) Boolean withExterior
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get sliceinstance {} window {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(withExterior);
        windowParameter.setFormat(format);
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));

        //TODO:
//        if (params.mask || params.alphaMask || params.alphaMask || params.draw || params.type in ['draw', 'mask', 'alphaMask', 'alphamask'])
//            params.location = getWKTGeometry(sliceInstance, params)
        responseByteArray(imageServerService.window(sliceInstance.getBaseSlice(), windowParameter), format);
    }

    @RequestMapping(value = "/sliceinstance/{id}/camera_url-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> cameraUrl(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get sliceinstance {} camera url {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(false);
        windowParameter.setFormat(format);
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
        String url = imageServerService.windowUrl(sliceInstance.getBaseSlice(), windowParameter);
        return responseSuccess(JsonObject.of("url", url));
    }

    @RequestMapping(value = "/sliceinstance/{id}/camera-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void camera(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get sliceinstance {} camera {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(false);
        windowParameter.setFormat(format);
        SliceInstance sliceInstance = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance" , id));
        responseByteArray(imageServerService.window(sliceInstance.getBaseSlice(), windowParameter), format);
    }

    //TODO

//    //todo : move into a service
//    def userAnnotationService
//    def reviewedAnnotationService
//    def termService
//    def secUserService
//    def annotationListingService
//    public String getWKTGeometry(SliceInstance sliceInstance, params) {
//        def geometries = []
//        if (params.annotations && !params.reviewed) {
//            def idAnnotations = params.annotations.split(',')
//            idAnnotations.each { idAnnotation ->
//                    def annot = userAnnotationService.read(idAnnotation)
//                if (annot)
//                    geometries << annot.location
//            }
//        }
//        else if (params.annotations && params.reviewed) {
//            def idAnnotations = params.annotations.split(',')
//            idAnnotations.each { idAnnotation ->
//                    def annot = reviewedAnnotationService.read(idAnnotation)
//                if (annot)
//                    geometries << annot.location
//            }
//        }
//        else if (!params.annotations) {
//            def project = sliceInstance.image.project
//            List<Long> termsIDS = params.terms?.split(',')?.collect {
//                Long.parseLong(it)
//            }
//            if (!termsIDS) { //don't filter by term, take everything
//                termsIDS = termService.getAllTermId(project)
//            }
//
//            List<Long> userIDS = params.users?.split(",")?.collect {
//                Long.parseLong(it)
//            }
//            if (!userIDS) { //don't filter by users, take everything
//                userIDS = secUserService.listLayers(project).collect { it.id}
//            }
//            List<Long> sliceIDS = [sliceInstance.id]
//
//            log.info params
//            //Create a geometry corresponding to the ROI of the request (x,y,w,h)
//            int x
//            int y
//            int w
//            int h
//            try {
//                x = params.int('topLeftX')
//                y = params.int('topLeftY')
//                w = params.int('width')
//                h = params.int('height')
//            }catch (Exception e) {
//                x = params.int('x')
//                y = params.int('y')
//                w = params.int('w')
//                h = params.int('h')
//            }
//            Geometry roiGeometry = GeometryUtils.createBoundingBox(
//                    x,                                      //minX
//                    x + w,                                  //maxX
//                    sliceInstance.baseSlice.image.height - (y + h),    //minX
//                    sliceInstance.baseSlice.image.height - y           //maxY
//            )
//
//
//            //Fetch annotations with the requested term on the request image
//            if (params.review) {
//                ReviewedAnnotationListing ral = new ReviewedAnnotationListing(
//                        project: project.id, terms: termsIDS, reviewUsers: userIDS, slices:sliceIDS, bbox:roiGeometry,
//                        columnToPrint:['basic', 'meta', 'wkt', 'term']
//                )
//                def result = annotationListingService.listGeneric(ral)
//                log.info "annotations=${result.size()}"
//                geometries = result.collect {
//                    new WKTReader().read(it["location"])
//                }
//
//            } else {
//                log.info "roiGeometry=${roiGeometry}"
//                log.info "termsIDS=${termsIDS}"
//                log.info "userIDS=${userIDS}"
//                Collection<UserAnnotation> annotations = userAnnotationService.list(sliceInstance, roiGeometry, termsIDS, userIDS)
//                log.info "annotations=${annotations.size()}"
//                geometries = annotations.collect { geometry ->
//                        geometry.getLocation()
//                }
//            }
//        }
//        GeometryCollection geometryCollection = new GeometryCollection((Geometry[])geometries, new GeometryFactory())
//        return new WKTWriter().write(geometryCollection)
//    }

}
