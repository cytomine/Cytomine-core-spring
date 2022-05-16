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
import be.cytomine.domain.image.AbstractImage;
import be.cytomine.domain.meta.AttachedFile;
import be.cytomine.domain.project.Project;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.exceptions.WrongArgumentException;
import be.cytomine.repository.ontology.AnnotationDomainRepository;
import be.cytomine.service.meta.AttachedFileService;
import be.cytomine.service.ontology.TermService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.AbstractMultipartHttpServletRequest;

import java.io.IOException;
import java.util.List;

import static org.springframework.security.acls.domain.BasePermission.READ;
import static org.springframework.security.acls.domain.BasePermission.WRITE;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestAttachedFileController extends RestCytomineController {

    private final AttachedFileService attachedFileService;


    @GetMapping("/attachedfile.json")
    public ResponseEntity<String> list() {
        log.debug("REST request to list attached file");
        return responseSuccess(attachedFileService.list());
    }

    @GetMapping("/domain/{domainClassName}/{domainIdent}/attachedfile.json")
    public ResponseEntity<String> listByDomain(
            @PathVariable String domainClassName,
            @PathVariable Long domainIdent
    )  {
        log.debug("REST request to list attached file for domain {} {}", domainClassName, domainIdent);

        return responseSuccess(attachedFileService.findAllByDomain(domainClassName, domainIdent));
    }

    @GetMapping("/attachedfile/{id}.json")
    public ResponseEntity<String> show(@PathVariable Long id) {
        log.debug("REST request to show attached file {}", id);
        return responseSuccess(attachedFileService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AttachedFile" , id)));
    }


    @GetMapping(value = {"/attachedfile/{id}/download.json", "/attachedfile/{id}/download"})
    public void download(@PathVariable Long id) throws IOException {
        log.debug("REST request to download attached file {}", id);
        AttachedFile attachedFile = attachedFileService.findById(id)
                .orElseThrow(() -> ObjectNotFoundException.notFoundException("AttachedFile" , id));
        responseFile(attachedFile.getFilename(), attachedFile.getData());
    }

    @RequestMapping(value = "/attachedfile.json", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<String> upload(
        @RequestParam(required = false)  Long domainIdent,
        @RequestParam(required = false)  String domainClassName,
        @RequestParam(required = false)  String filename,
        @RequestParam(required = false)  String key,
        @RequestPart("files[]") List<MultipartFile> files
        ) throws ClassNotFoundException, IOException {
        log.debug("REST request to upload attached file");

        MultipartFile f = files.get(0);

        if(filename==null) {
            filename = f.getOriginalFilename();
            if (filename.contains("/")) {
                String[] parts = filename.split("/");
                filename = parts[parts.length-1];
            }
        }
        log.info("Upload {} for domain {} {}", filename, domainClassName, domainIdent);
        log.info("File size = {}", f.getSize());
        AttachedFile attachedFile = attachedFileService.create(filename,f.getBytes(),key,domainIdent,domainClassName);
        return responseSuccess(attachedFile);

//        if(request instanceof AbstractMultipartHttpServletRequest) {
//            MultipartFile f = ((AbstractMultipartHttpServletRequest) request).getFile("files[]");
//
//            if(domainClassName == null) domainClassName = ((AbstractMultipartHttpServletRequest) request).getParameter("domainClassName");
//            if(domainIdent == null) domainIdent = Long.parseLong(((AbstractMultipartHttpServletRequest) request).getParameter("domainIdent"));
//
//            String filename = ((AbstractMultipartHttpServletRequest) request).getParameter("filename");
//            if(filename==null) filename = f.getOriginalFilename();
//
//            log.info("Upload {} for domain {} {}", filename, domainClassName, domainIdent);
//            log.info("File size = {}", f.getSize());
//
//            AttachedFile attachedFile = attachedFileService.create(filename,f.getBytes(),key,domainIdent,domainClassName);
//            return responseSuccess(attachedFile);
//        } else {
//            throw new WrongArgumentException("No File attached");
//        }
    }

    @DeleteMapping("/attachedfile/{id}.json")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        log.debug("REST request to delete attached file {}", id);
        return delete(attachedFileService, JsonObject.of("id", id), null);
    }
}
