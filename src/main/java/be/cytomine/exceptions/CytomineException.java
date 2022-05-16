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

import java.util.HashMap;
import java.util.Map;

/**
 * User: lrollus
 * Date: 17/11/11
 * This exception is the top exception for all cytomine exception
 * It store a message and a code, corresponding to an HTTP code
 */
public abstract class CytomineException extends RuntimeException {

    /**
     * Http code for an exception
     */
    public int code;

    /**
     * Message for exception
     */
    public String msg;

    /**
     * Error code for WebUI message
     */
    public int errorCode;

    /**
     * Values of the exception
     */
    public Map<Object, Object> values;

    /**
     * Message map with this exception
     * @param msg Message
     * @param code Http code
     */
    public CytomineException(String msg, int code) {
        this(msg,code,0,new HashMap<>());
    }

    public CytomineException(String msg, int code, int errorCode) {
        this(msg,code,errorCode,new HashMap<>());
    }

    public CytomineException(String msg, int code, Throwable cause) {
        this(msg,code,0,new HashMap<>(), cause);
    }

    public CytomineException(String msg, int code, int errorCode, Throwable cause) {
        this(msg,code,errorCode,new HashMap<>(), cause);
    }

    public CytomineException(String msg, int code, Map<Object, Object> values) {
        this(msg, code, 0, values, null);
    }

    public CytomineException(String msg, int code, int errorCode, Map<Object, Object> values) {
        this(msg, code, errorCode, values, null);
    }

    public CytomineException(String msg, int code, int errorCode, Map<Object, Object> values, Throwable cause) {
        super(msg, cause);
        this.msg=msg;
        this.code = code;
        this.errorCode = errorCode;
        this.values = values;
    }

    public String toString() {
        return this.msg;
    }
}
