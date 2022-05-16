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
import be.cytomine.domain.image.AbstractImage;
import be.cytomine.domain.image.AbstractSlice;
import be.cytomine.domain.image.UploadedFile;
import be.cytomine.domain.security.SecUser;
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
public class RestAbstractSliceController extends RestCytomineController {

    private final AbstractSliceService abstractSliceService;

    private final AbstractImageService abstractImageService;

    private final ProjectService projectService;

    private final ImageServerService imageServerService;

    private final UploadedFileService uploadedFileService;

    private final SliceCoordinatesService sliceCoordinatesService;

    private final ImagePropertiesService imagePropertiesService;

    @GetMapping("/abstractimage/{id}/abstractslice.json")
    public ResponseEntity<String> listByAbstractSlice(
            @PathVariable Long id
    ) {
        log.debug("REST request to list abstract slice for image {}", id);
        AbstractImage abstractImage = abstractImageService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractImage" , id));
        return responseSuccess(abstractSliceService.list(abstractImage));
    }

    @GetMapping("/uploadedfile/{id}/abstractslice.json")
    public ResponseEntity<String> listByUploadedFile(
            @PathVariable Long id
    ) {
        log.debug("REST request to list abstract slice for image {}", id);
        UploadedFile uploadedFile = uploadedFileService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("UploadedFile", id));
        return responseSuccess(abstractSliceService.list(uploadedFile));
    }


    @GetMapping("/abstractslice/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get abstract slice {}", id);
        return abstractSliceService.find(id)
                .map(this::responseSuccess)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
    }


    @GetMapping("/abstractimage/{id}/{channel}/{zStack}/{time}/abstractslice.json")
    public ResponseEntity<String> getByAbstractSliceAndCoordinates(
            @PathVariable Long id,
            @PathVariable Integer channel,
            @PathVariable Integer zStack,
            @PathVariable Integer time
    ) {
        log.debug("REST request to get abstract slice for  image {} and coordinates {}-{}-{}", id, channel, zStack, time);
        AbstractImage abstractImage = abstractImageService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));

        AbstractSlice abstractSlice = abstractSliceService.find(abstractImage, channel,zStack, time)
                .orElseThrow(() -> new ObjectNotFoundException("AbstractSlice [" + channel + "-" + zStack + "-" + time + "]", id));
        return responseSuccess(abstractSlice);
    }
    
    @PostMapping("/abstractslice.json")
    public ResponseEntity<String> add(@RequestBody String json) {
        log.debug("REST request to save abstractslice : " + json);
        return add(abstractSliceService, json);
    }

    @PutMapping("/abstractslice/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit abstractslice : " + id);
        return update(abstractSliceService, json);
    }

    @DeleteMapping("/abstractslice/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id) {
        log.debug("REST request to delete abstractslice : " + id);
        return delete(abstractSliceService, JsonObject.of("id", id), null);
    }

    @GetMapping("/abstractslice/{id}/user.json")
    public ResponseEntity<String> showUploaderOfImage(@PathVariable Long id) {
        log.debug("REST request to show image uploader");
        SecUser user = abstractSliceService.findImageUploaded(id);
        if (user !=null) {
            return responseSuccess(user);
        } else {
            return responseNotFound("AbstractSlice", "User", id);
        }

    }
//
//    // TODO:MIGRATION GET params vs POST params!
    @RequestMapping(value = "/abstractslice/{id}/thumb.{format}", method = {RequestMethod.GET, RequestMethod.POST})
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
        log.debug("REST request get abstractslice {} thumb {}", id, format);
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

        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
        responseByteArray(imageServerService.thumb(abstractSlice, thumbParameter), format);
    }

    @RequestMapping(value = "/abstractslice/{id}/crop.{format}", method = {RequestMethod.GET, RequestMethod.POST})
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
        log.debug("REST request to get associated image of a abstract slice");
        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));

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

        responseByteArray(imageServerService.crop(abstractSlice, cropParameter), format);
    }

    @RequestMapping(value = "/abstractslice/{id}/window_url-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> windowUrl(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h,
            @RequestParam(defaultValue = "false", required = false) Boolean withExterior
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get abstractslice {} window url {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(withExterior);
        windowParameter.setFormat(format);
        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
        String url = imageServerService.windowUrl(abstractSlice, windowParameter);
        return responseSuccess(JsonObject.of("url", url));
    }

    @RequestMapping(value = "/abstractslice/{id}/window-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void window(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h,
            @RequestParam(defaultValue = "false", required = false) Boolean withExterior
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get abstractslice {} window {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(withExterior);
        windowParameter.setFormat(format);
        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
        responseByteArray(imageServerService.window(abstractSlice, windowParameter), format);
    }

    @RequestMapping(value = "/abstractslice/{id}/camera_url-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> cameraUrl(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get abstractslice {} camera url {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(false);
        windowParameter.setFormat(format);
        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
        String url = imageServerService.windowUrl(abstractSlice, windowParameter);
        return responseSuccess(JsonObject.of("url", url));
    }

    @RequestMapping(value = "/abstractslice/{id}/camera-{x}-{y}-{w}-{h}.{format}", method = {RequestMethod.GET, RequestMethod.POST})
    public void camera(
            @PathVariable Long id,
            @PathVariable String format,
            @PathVariable Integer x,
            @PathVariable Integer y,
            @PathVariable Integer w,
            @PathVariable Integer h
    ) throws UnsupportedEncodingException, ParseException {
        log.debug("REST request get abstractslice {} camera {}", id, format);
        WindowParameter windowParameter = new WindowParameter();
        windowParameter.setX(x);
        windowParameter.setY(y);
        windowParameter.setW(w);
        windowParameter.setH(h);
        windowParameter.setWithExterior(false);
        windowParameter.setFormat(format);
        AbstractSlice abstractSlice = abstractSliceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractSlice" , id));
        responseByteArray(imageServerService.window(abstractSlice, windowParameter), format);
    }

}
