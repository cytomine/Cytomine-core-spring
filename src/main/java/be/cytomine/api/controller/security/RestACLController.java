package be.cytomine.api.controller.security;

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
import be.cytomine.domain.security.SecUser;
import be.cytomine.exceptions.CytomineException;
import be.cytomine.exceptions.ErrorCode;
import be.cytomine.exceptions.ObjectNotFoundException;
import be.cytomine.service.security.AclAuthService;
import be.cytomine.utils.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.EntityManager;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class RestACLController extends RestCytomineController {

    private final EntityManager entityManager;

    private final AclAuthService aclAuthService;

    @GetMapping("/domain/{domainClassName}/{domainIdent}/user/{user}")
    public ResponseEntity<String> getPermission(
            @PathVariable String domainClassName,
            @PathVariable String domainIdent,
            @PathVariable String user
    ) {
        log.debug("REST request to get permission : {} {} {}", domainClassName, domainIdent, user);

        try {
            // if(domainClassName!=null && domainIdent!=null && user!=null) {
            if(false){
                //CytomineDomain domain = retrieveCytomineDomain(domainClassName,Long.parseLong(domainIdent));
                SecUser secUser = entityManager.find(SecUser.class, Long.parseLong(user));
                return responseSuccess(aclAuthService.get(Long.parseLong(domainIdent),secUser));
            } else {
                throw new ObjectNotFoundException("");
            }
        } catch(CytomineException e) {
            if(e.getClass() == ObjectNotFoundException.class){
                throw new ObjectNotFoundException(
                        "Request not valid: domainClassName="+ domainClassName + ", domainIdent= " + domainIdent + ", user=" + user,
                        ErrorCode.NOT_FOUND_NOT_VALID.getValue(),
                        Map.of("domainClassName", domainClassName, "domainIdent", domainIdent, "user", user));
            }
            return ResponseEntity.status(e.code).body(JsonObject.of("success", false, "errors", e.msg).toJsonString());
        }
    }

//    public CytomineDomain retrieveCytomineDomain(String domainClassName, Long domainIdent) {
//        CytomineDomain domain;
//        try {
//            domain = (CytomineDomain)entityManager.find(Class.forName(domainClassName), domainIdent);//Class.forName(domainClassName, false, Thread.currentThread().getContextClassLoader()).read(domainIdent);
//        } catch(Exception e) {
//            throw new ObjectNotFoundException("Cannot find object " + domainClassName + " with id " + domainIdent);
//        }
//        if(domain!=null) {
//            throw new ObjectNotFoundException("Request not valid: domainClassName="+ domainClassName + ", domainIdent= " + domainIdent);
//        }
//        return domain;
//    }


//    @RestApiMethod(description="Get all ACL for a user and a class.", listing=true)
//    @RestApiParams(params=[
//            @RestApiParam(name="domainClassName", type="string", paramType = RestApiParamType.PATH, description = "The domain class"),
//            @RestApiParam(name="domainIdent", type="long", paramType = RestApiParamType.PATH, description = "The domain id"),
//            @RestApiParam(name="user", type="long", paramType = RestApiParamType.PATH, description = "The user id")
//            ])
//    @RestApiResponseObject(objectIdentifier="List of all permission name (empty if user has no permission)")
//    def list() {
//        try {
//            if(params.domainClassName && params.domainIdent && params.user) {
//                def domain = retrieveCytomineDomain(params.domainClassName,params.long('domainIdent'))
//                responseSuccess(aclAuthService.get(domain,SecUser.read(params.long('user'))) )
//            } else {
//                throw new ObjectNotFoundException("Request not valid: domainClassName=${params.domainClassName}, domainIdent=${params.domainIdent} and user=${params.user}")
//            }
//        } catch(CytomineException e) {
//            response([success: false, errors: e.msg], e.code)
//        }
//    }
}
