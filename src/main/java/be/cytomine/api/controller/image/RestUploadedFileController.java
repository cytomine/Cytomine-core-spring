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
import be.cytomine.api.controller.utils.RequestParams;
import be.cytomine.domain.image.UploadedFile;
import be.cytomine.domain.security.User;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.CurrentUserService;
import be.cytomine.service.image.AbstractImageService;
import be.cytomine.service.image.UploadedFileService;
import be.cytomine.service.middleware.ImageServerService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestUploadedFileController extends RestCytomineController {

    private final AbstractImageService abstractImageService;

    private final UploadedFileService uploadedFileService;

    private final ImageServerService imageServerService;

    private final CurrentUserService currentUserService;


    @GetMapping("/uploadedfile.json")
    public ResponseEntity<String> list(
            @RequestParam(defaultValue = "false") Boolean onlyRootsWithDetails,
            @RequestParam(defaultValue = "false") Boolean onlyRoots,
            @RequestParam(required = false) Long parent,
            @RequestParam(required = false)  Long root,
            @RequestParam(defaultValue = "false") Boolean all
    ) {
        log.debug("REST request to list uploaded files");

        RequestParams requestParams = retrievePageableParameters();
        if (root!=null) {
            return responseSuccess(uploadedFileService.listHierarchicalTree((User) currentUserService.getCurrentUser(), root));
        } else if (onlyRootsWithDetails) {
            return responseSuccess(uploadedFileService.list(retrieveSearchParameters(), requestParams.getSort(), requestParams.getOrder()));
        } else if (all) {
            return responseSuccess(uploadedFileService.list(retrievePageable()));
        } else {
            return responseSuccess(uploadedFileService.list(currentUserService.getCurrentUser(), parent, onlyRoots, retrievePageable()));
        }
    }

    @GetMapping("/uploadedfile/{id}.json")
    public ResponseEntity<String> show(
            @PathVariable Long id
    ) {
        log.debug("REST request to get uploadedFile {}", id);
        return uploadedFileService.find(id)
                .map(this::responseSuccess)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AbstractImage" , id));
    }

    @PostMapping(value = "/uploadedfile.json")
    public ResponseEntity<String> add(@RequestBody String json) {
        log.debug("REST request to save uploadedFile : " + json);
        return add(uploadedFileService, json);
    }

//    //TODO: hack, as IMS request body type seems to be "application/octet-stream"
//    @PostMapping(value = "/uploadedfile.json", consumes = {"application/octet-stream"})
//    public ResponseEntity<String> addBis() throws IOException {
//        log.debug("REST request to save uploadedFile from octet-stream");
//        String bodyData = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
//        JsonObject json = new JsonObject();
//        if (!bodyData.isEmpty()) {
//            Map<String, Object> bodyMap = JsonObject.toMap(bodyData);
//            json.putAll(bodyMap);
//        }
//        log.debug("REST request to save uploadedFile : " + json);
//        return add(uploadedFileService, json);
//    }

    @PutMapping("/uploadedfile/{id}.json")
    public ResponseEntity<String> edit(@PathVariable String id, @RequestBody JsonObject json) {
        log.debug("REST request to edit uploadedFile : " + id);
        return update(uploadedFileService, json);
    }

    @DeleteMapping("/uploadedfile/{id}.json")
    public ResponseEntity<String> delete(@PathVariable String id) {
        log.debug("REST request to delete uploadedFile : " + id);
        return delete(uploadedFileService, JsonObject.of("id", id), null);
    }


    @GetMapping("/uploadedfile/{id}/download")
    public RedirectView download(@PathVariable Long id) throws IOException {
        log.debug("REST request to download uploadedFile");
        UploadedFile uploadedFile = uploadedFileService.find(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("UploadedFile", id));
        // TODO: in abstract image, there is no check fos download auth!?
        String url = imageServerService.downloadUri(uploadedFile);
        return new RedirectView(url);
    }
}
