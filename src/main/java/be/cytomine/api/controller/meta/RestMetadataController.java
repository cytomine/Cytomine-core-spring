package be.cytomine.api.controller.meta;

/*
 * Copyright (c) 2009-2023. Authors: see NOTICE file.
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
import be.cytomine.service.search.MetadataSearchService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/")
@RequiredArgsConstructor
@RestController
@Slf4j
public class RestMetadataController extends RestCytomineController {

    private final MetadataSearchService metadataSearchService;

    @PostMapping("search.json")
    public ResponseEntity<String> search(@RequestBody JsonObject body) {
        log.debug("Rest request to search image using metadata:" + body);
        return responseSuccess(metadataSearchService.search(body));
    }

    @GetMapping("search/autocomplete.json")
    public ResponseEntity<String> autocomplete(
        @RequestParam String key,
        @RequestParam String searchTerm
    ) {
        log.debug("REST request to autocomplete \"{}\" for key {}", searchTerm, key);
        return responseSuccess(metadataSearchService.searchAutoCompletion(key, searchTerm));
    }
}
