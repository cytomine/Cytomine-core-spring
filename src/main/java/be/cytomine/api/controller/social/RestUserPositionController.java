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
import be.cytomine.domain.security.SecUser;
import be.cytomine.domain.social.LastUserPosition;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.dto.AreaDTO;
import be.cytomine.service.dto.Point;
import be.cytomine.service.image.ImageInstanceService;
import be.cytomine.service.image.SliceInstanceService;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.service.ontology.TermService;
import be.cytomine.service.security.SecUserService;
import be.cytomine.service.social.UserPositionService;
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
public class RestUserPositionController extends RestCytomineController {

    private final UserPositionService userPositionService;

    private final ImageInstanceService imageInstanceService;

    private final SliceInstanceService sliceInstanceService;

    private final CurrentUserService currentUserService;

    private final SecUserService secUserService;


    //{"image":6836067,"zoom":1,"rotation":0,"bottomLeftX":-2344,"bottomLeftY":1032,"bottomRightX":6784,"bottomRightY":1032,"topLeftX":-2344,"topLeftY":2336,"topRightX":6784,"topRightY":2336,"broadcast":false}

    @PostMapping("/imageinstance/{id}/position.json")
    public ResponseEntity<String> addFromImageInstance(
            @PathVariable Long id,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request add user position for imageinstance {id}");
        ImageInstance imageInstance =
                imageInstanceService.find(id)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance", id));

        SliceInstance referenceSlice = imageInstanceService.getReferenceSlice(imageInstance.getId());
        return add(imageInstance, referenceSlice, json);
    }

    @PostMapping("/sliceinstance/{id}/position.json")
    public ResponseEntity<String> addFromSliceInstance(
            @PathVariable Long id,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request add user position for sliceinstance {}", id);
        SliceInstance referenceSlice = sliceInstanceService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance", id));

        return add(referenceSlice.getImage(), referenceSlice, json);
    }

    public ResponseEntity<String> add(
            ImageInstance imageInstance,
            SliceInstance sliceInstance,
            JsonObject json
    ) {
        Date date = new Date();
        SecUser user = currentUserService.getCurrentUser();

        Point topLeft = new Point(json.getJSONAttrDouble("topLeftX", 0d), json.getJSONAttrDouble("topLeftY", 0d));
        Point topRight = new Point(json.getJSONAttrDouble("topRightX", 0d), json.getJSONAttrDouble("topRightY", 0d));
        Point bottomRight = new Point(json.getJSONAttrDouble("bottomRightX", 0d), json.getJSONAttrDouble("bottomRightY", 0d));
        Point bottomLeft = new Point(json.getJSONAttrDouble("bottomLeftX", 0d), json.getJSONAttrDouble("bottomLeftY", 0d));
        AreaDTO areaDTO = new AreaDTO(topLeft, topRight, bottomRight, bottomLeft);

        Integer zoom = json.getJSONAttrInteger("zoom", 0);
        Double rotation = json.getJSONAttrDouble("rotation", 0d);
        Boolean broadcast = json.getJSONAttrBoolean("broadcast" , false);

        return responseSuccess(userPositionService.add(date, user, sliceInstance, imageInstance, areaDTO, zoom, rotation, broadcast));
    }

    @GetMapping("/imageinstance/{image}/position/{user}.json")
    public ResponseEntity<String> getLastUserPosition(
            @PathVariable("image") Long imageId,
            @PathVariable("user") Long userId,
            @RequestParam(required = false, defaultValue = "false") Boolean broadcast,
            @RequestParam(required = false) Long sliceId
    ) {
        ImageInstance imageInstance =
                imageInstanceService.find(imageId)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance", imageId));

        SecUser user = secUserService.find(userId)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("SecUser", userId));

        SliceInstance sliceInstance = null;
        if (sliceId!=null) {
            sliceInstance = sliceInstanceService.find(sliceId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance", sliceId));

        }
        return  responseSuccess(userPositionService.lastPositionByUser(imageInstance, sliceInstance, user, broadcast).map(LastUserPosition::toJsonObject).orElse(new JsonObject()));
    }

    @GetMapping("/imageinstance/{image}/positions.json")
    public ResponseEntity<String> list(
            @PathVariable("image") Long imageId,
            @RequestParam(value = "user", required = false) Long userId,
            @RequestParam(value = "slice", required = false) Long sliceId,
            @RequestParam(required = false) Long afterThan,
            @RequestParam(required = false) Long beforeThan,
            @RequestParam(required = false, defaultValue = "false") Boolean showDetails,
            @RequestParam(required = false, defaultValue = "0") Long max,
            @RequestParam(required = false, defaultValue = "0") Long offset
    ) {
        ImageInstance imageInstance =
                imageInstanceService.find(imageId)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance", imageId));

        SecUser user = null;
        if (userId!=null) {
            user = secUserService.find(userId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("Project", userId));
        }
        SliceInstance sliceInstance = null;
        if (sliceId!=null) {
            sliceInstance = sliceInstanceService.find(sliceId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance", sliceId));

        }
        if (showDetails) {
            return responseSuccess(userPositionService.list(imageInstance, user, sliceInstance, afterThan, beforeThan, max.intValue(), offset.intValue()));
        } else {
            return responseSuccess(userPositionService.summarize(imageInstance, user, sliceInstance, afterThan, beforeThan));
        }
    }


    @GetMapping("/imageinstance/{image}/online.json")
    public ResponseEntity<String> listOnlineUsersByImage(
            @PathVariable("image") Long imageId,
            @RequestParam(value = "slice", required = false) Long sliceId,
            @RequestParam(required = false, defaultValue = "false") Boolean broadcast
    ) {
        ImageInstance imageInstance =
                imageInstanceService.find(imageId)
                        .orElseThrow(() -> ObjectNotFoundException.notFoundException("ImageInstance", imageId));

        SliceInstance sliceInstance = null;
        if (sliceId!=null) {
            sliceInstance = sliceInstanceService.find(sliceId)
                    .orElseThrow(() -> ObjectNotFoundException.notFoundException("SliceInstance", sliceId));
        }
        return responseSuccess(JsonObject.of("users", userPositionService.listOnlineUsersByImage(imageInstance, sliceInstance, broadcast)));
    }

}
