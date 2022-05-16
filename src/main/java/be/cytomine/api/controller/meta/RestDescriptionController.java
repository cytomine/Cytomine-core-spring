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
import be.cytomine.domain.meta.AttachedFile;
import be.cytomine.domain.meta.Configuration;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.command.TransactionService;
import be.cytomine.service.meta.AttachedFileService;
import be.cytomine.service.meta.DescriptionService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestDescriptionController extends RestCytomineController {

    private final DescriptionService descriptionService;

    private final TransactionService transactionService;


    @GetMapping("/description.json")
    public ResponseEntity<String> list() {
        log.debug("REST request to list description");
        return responseSuccess(descriptionService.list());
    }

    @GetMapping("/domain/{domainClassName}/{domainIdent}/description.json")
    public ResponseEntity<String> readByDomain(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent
    )  {
        log.debug("REST request to read description for domain {} {}", domainClassName, domainIdent);

        return responseSuccess(descriptionService.findByDomain(domainClassName.replaceAll("_", "."), domainIdent)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Description",
                        JsonObject.of("domainIdent", domainIdent, "domainClassName", domainClassName.replaceAll("_", ".")).toJsonString(),
                        ErrorCode.NOT_FOUND_DESCRIPTION.getValue(),
                        Map.of("domainClass", domainClassName, "id", domainIdent))));
    }

    @PostMapping({"/description.json", "/domain/{domainClassName}/{domainIdent}/description.json"})
    public ResponseEntity<String> add(
            @PathVariable(required = false) String domainClassName,
            @PathVariable(required = false) Long domainIdent,
            @RequestBody JsonObject json) {
        log.debug("REST request to save description : " + json);
        return add(descriptionService, json);
    }

    @PutMapping("/domain/{domainClassName}/{domainIdent}/description.json")
    public ResponseEntity<String> edit(
            @PathVariable(required = false) String domainClassName,
            @PathVariable(required = false) Long domainIdent,
            @RequestBody JsonObject json
    ) {
        log.debug("REST request to edit description : " + domainClassName + " " + domainIdent);
        return update(descriptionService, json);
    }

    @DeleteMapping("/domain/{domainClassName}/{domainIdent}/description.json")
    public ResponseEntity<String> delete(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent) {
        log.debug("REST request to delete description for {} {}", domainClassName, domainIdent);
        JsonObject jsonObject = JsonObject.of("domainIdent", domainIdent, "domainClassName", domainClassName.replaceAll("_", "."));
        CytomineDomain domain = descriptionService.retrieve(jsonObject);
        return responseSuccess(descriptionService.delete(domain,transactionService.start(), null, true));
    }
}
