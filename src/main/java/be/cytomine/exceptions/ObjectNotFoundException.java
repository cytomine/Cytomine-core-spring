package be.cytomine.exceptions;

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

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * User: lrollus
 * Date: 17/11/11
 * This exception means that the object was not found on DB
 * It correspond to the HTTP code 404
 */
@Slf4j
public class ObjectNotFoundException extends CytomineException {

    /**
     * Message map with this exception
     * @param message Message
     */
    public ObjectNotFoundException(String message) {
        super(message,404,0,new HashMap<>());
        log.warn(message);
    }

    public ObjectNotFoundException(String message, int errorCode) {
        super(message,404,errorCode,new HashMap<>());
        log.warn(message);
    }

    public ObjectNotFoundException(String message, int errorCode, Map<Object, Object> values) {
        super(message,404,errorCode,values);
        log.warn(message);
    }

    public ObjectNotFoundException(String objectType, Object objectId) {
        super(objectType + " " + objectId + " not found",404,0,new HashMap<>());
        log.warn(super.getMessage());
    }

    public ObjectNotFoundException(String objectType, Object objectId, int errorCode, Map<Object, Object> values) {
        super(objectType + " " + objectId + " not found",404,errorCode,values);
        log.warn(super.getMessage());
    }

    public ObjectNotFoundException(String objectType, String objectId, int errorCode, Map<Object, Object> values) {
        super(objectType + " " + objectId + " not found", 404, errorCode, values);
        log.warn(super.getMessage());
    }

    public ObjectNotFoundException(String objectType, Long objectId, int errorCode, Map<Object, Object> values) {
        this(objectType, String.valueOf(objectId), errorCode, values);
    }

    public static ObjectNotFoundException notFoundException(String object, Long id){
        return notFoundException(object, Long.toString(id));
    }

    public static ObjectNotFoundException notFoundException(String object, String id){
        return new ObjectNotFoundException(
                object,
                id,
                ErrorCode.NOT_FOUND_WITH_ID.getValue(),
                Map.of("object", object, "id", id));
    }
}
