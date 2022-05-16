package be.cytomine.api.controller;

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

import be.cytomine.api.controller.utils.RequestParams;
import be.cytomine.domain.CytomineDomain;
import be.cytomine.domain.CytomineSocialDomain;
import be.cytomine.dto.JsonInput;
import be.cytomine.dto.JsonMultipleObject;
import be.cytomine.dto.JsonSingleObject;
import be.cytomine.exceptions.*;
import be.cytomine.service.ModelService;
import be.cytomine.service.command.TransactionService;
import be.cytomine.utils.CommandResponse;
import be.cytomine.utils.JsonObject;
import be.cytomine.utils.OffsetBasedPageRequest;
import be.cytomine.utils.Task;
import be.cytomine.utils.filters.SearchParameterEntry;
import be.cytomine.utils.filters.SearchParametersUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class RestCytomineController {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    protected HttpServletRequest request;

    @Autowired
    protected HttpServletResponse response;

//    /**
//     * Response a successful HTTP message
//     * @param data Message content
//     */
//    protected JsonObject responseSuccess(Object data, Map<String,String> params) {
//        if(data instanceof List) {
//            return responseList((List)data, params);
//        } else if(data instanceof Collection) {
//            List list = new ArrayList();
//            list.addAll((Collection)data);
//            return responseList(list, params);
//        }
//        else {
//            response(data);
//        }
//    }

    protected RequestParams retrievePageableParameters() {
        RequestParams requestParams = retrieveRequestParam();
        requestParams.putIfAbsent("offset", "0");
        requestParams.putIfAbsent("max", "0");
        requestParams.putIfAbsent("sort", "created");
        requestParams.putIfAbsent("order", "desc");
        return requestParams;
    }

    protected Pageable retrievePageable() {
        RequestParams requestParams = retrievePageableParameters();

        Sort sort = Sort.by(requestParams.get("sort")).ascending();
        if (requestParams.get("order").equals("desc")) {
            sort = Sort.by(requestParams.get("sort")).descending();
        }

        int realMax = requestParams.get("max").equals("0") ? Integer.MAX_VALUE : Integer.parseInt(requestParams.get("max"));
//        int pageIndex =  Math.floor(Double.parseDouble(requestParams.get("offset")) / (double)realMax);
//
//        return PageRequest.of(
//                pageIndex,
//                realMax,
//                sort);

        return new OffsetBasedPageRequest(Long.parseLong(requestParams.get("offset")), realMax, sort);
    }

    protected JsonObject mergeQueryParamsAndBodyParams() throws IOException {
        JsonObject response = new JsonObject();
        Map<String, String[]> parameterMap = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if (entry.getValue()!= null  && entry.getValue().length>1) {
                throw new CytomineMethodNotYetImplementedException("Multiple request params are not supported in this method");
            } else if(entry.getValue()!= null && entry.getValue().length==1) {
                response.put(entry.getKey(), entry.getValue()[0]);
            }
        }

        if (request.getMethod().equals("POST")) {
            String bodyData = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            if (!bodyData.isEmpty()) {
                Map<String, Object> bodyMap = JsonObject.toMap(bodyData);
                response.putAll(bodyMap);
            }
        }

        return response;
    }

    protected List<SearchParameterEntry> retrieveSearchParameters() {
        return SearchParametersUtils.getSearchParameters(retrieveRequestParam());
    }

    private JsonObject buildJsonList(List list, Integer offsetParameter, Integer maxParameter) {

        Integer offset = offsetParameter != null ? offsetParameter : 0;
        Integer max = (maxParameter != null && maxParameter!=0) ? maxParameter : Integer.MAX_VALUE;

        List subList;
        if (offset >= list.size()) {
            subList = new ArrayList();
        } else {
            int maxForCollection = Math.min(list.size() - offset, max);
            subList = list.subList(offset,offset + maxForCollection);
        }
        return JsonObject.of("collection", subList, "offset", offset, "perPage", Math.min(max, list.size()), "size", list.size(), "totalPages", (int)Math.ceil((double)list.size()/(double)max));

    }

    private JsonObject buildJsonList(Page page, Map<String,String> params) {
        // TODO: should we need params if we have page
        return buildJsonList(page, Integer.parseInt(params.get("offset")), Integer.parseInt(params.get("max")));
    }

    private JsonObject buildJsonList(Page page, Integer offsetParameter, Integer maxParameter) {
        // TODO: should we need params if we have page
        List finalContent = page.getContent();
        if (page.getContent().size() > 0 && page.getContent().get(0) instanceof CytomineDomain) {
            finalContent = convertCytomineDomainListToJSON(page.getContent());
        } else if (page.getContent().size() > 0 && page.getContent().get(0) instanceof CytomineSocialDomain) {
            finalContent = convertCytomineSocialDomainListToJSON(page.getContent());
        }
        Integer offset = offsetParameter != null ? offsetParameter : 0;
        Integer max = (maxParameter != null && maxParameter!=0) ? maxParameter : Integer.MAX_VALUE;
        return JsonObject.of("collection", finalContent, "offset", offset, "perPage", Math.min(max, page.getContent().size()), "size", page.getTotalElements(), "totalPages", (int)Math.ceil((double)page.getTotalElements()/(double)max));
    }

//    protected ResponseEntity<String> response(Map<String, Object> response, int code) {
//        return ResponseEntity.status(code).body(convertObjectToJSON(response));
//    }

    protected ResponseEntity<String> responseSuccess(Page page) {
        return responseSuccess(page, false);
    }

    protected ResponseEntity<String> responseSuccess(Page page, boolean isFilterRequired) {
        RequestParams requestParams = retrieveRequestParam();
        requestParams.putIfAbsent("offset", "0");
        requestParams.putIfAbsent("max", "0");
        if(isFilterRequired) {
            if (!page.getContent().isEmpty() && page.getContent().get(0) instanceof Map) {
                for (Object o : page.getContent()) {
                    filterOneElement((Map<String,Object>)o);
                }
            } else if(!page.getContent().isEmpty()) {
                throw new CytomineMethodNotYetImplementedException("Filter is not working with this class type " + page.getContent().get(0).getClass());
            }
        }
        return ResponseEntity.status(200).body(buildJsonList(page, requestParams).toJsonString());
    }


    protected ResponseEntity<String> responseSuccess(Page page, Integer offsetParameter, Integer maxParameter) {
        return ResponseEntity.status(200).body(buildJsonList(page, offsetParameter, maxParameter).toJsonString());
    }

    protected ResponseEntity<String> responseSuccess(Page page, Map<String,String> params) {
        return ResponseEntity.status(200).body(buildJsonList(page, params).toJsonString());
    }

    public ResponseEntity<String> responseSuccess(List list, Long offsetParameter, Long maxParameter) {
        return responseSuccess(list, offsetParameter.intValue(), maxParameter.intValue(), false);
    }

    public ResponseEntity<String> responseSuccess(List list, Integer offsetParameter, Integer maxParameter, boolean isFilterRequired) {
        List filtered = list;
        if (isFilterRequired) {
            filtered = new ArrayList();
            if (!list.isEmpty() && (list.get(0) instanceof CytomineDomain)) {
                for (Object o : list) {
                    JsonObject jsonObject = ((CytomineDomain) o).toJsonObject();
                    filterOneElement(jsonObject);
                    filtered.add(jsonObject);
                }
            } else if (!list.isEmpty() && (list.get(0) instanceof CytomineSocialDomain)) {
                for (Object o : list) {
                    JsonObject jsonObject = ((CytomineSocialDomain) o).toJsonObject();
                    filterOneElement(jsonObject);
                    filtered.add(jsonObject);
                }
            } else if (!list.isEmpty() && list.get(0) instanceof Map) {
                for (Object o : list) {
                    Map json = (Map)o;
                    filterOneElement(json);
                    filtered.add(json);
                }
            }
        }

        if (filtered.isEmpty() || filtered.get(0) instanceof CytomineDomain) {
            return responseSuccessDomainList((List<? extends CytomineDomain>) filtered, offsetParameter, maxParameter);
        } else if (filtered.isEmpty() || filtered.get(0) instanceof CytomineSocialDomain) {
            return responseSuccessSocialDomainList((List<? extends CytomineSocialDomain>) filtered, offsetParameter, maxParameter);
        } else {
            return responseSuccessGenericList(filtered, offsetParameter, maxParameter);
        }
    }

    public ResponseEntity<String> responseSuccess(List list) {
        return responseSuccess(list, false);
    }

    public ResponseEntity<String> responseSuccess(List list, boolean isFilterRequired) {
        RequestParams requestParams = retrievePageableParameters();
        return responseSuccess(list, requestParams.getOffset().intValue(), requestParams.getMax().intValue(), isFilterRequired);
    }

    private ResponseEntity<String> responseSuccessDomainList(List<? extends CytomineDomain> list, Integer offsetParameter, Integer maxParameter) {
        return ResponseEntity.status(200).body(buildJsonList(convertCytomineDomainListToJSON(list), offsetParameter, maxParameter).toJsonString()); //TODO: perf convert after buildJsonList will avoid converting unused items (out of page)
    }

    private ResponseEntity<String> responseSuccessSocialDomainList(List<? extends CytomineSocialDomain> list, Integer offsetParameter, Integer maxParameter) {
        return ResponseEntity.status(200).body(buildJsonList(convertCytomineSocialDomainListToJSON(list), offsetParameter, maxParameter).toJsonString()); //TODO: perf convert after buildJsonList will avoid converting unused items (out of page)
    }

    private ResponseEntity<String> responseSuccessGenericList(List list, Integer offsetParameter, Integer maxParameter) {
        return ResponseEntity.status(200).body(buildJsonList(list, offsetParameter, maxParameter).toJsonString()); //TODO: perf convert after buildJsonList will avoid converting unused items (out of page)
    }

    protected ResponseEntity<String> responseSuccess(CytomineDomain response, boolean isFilterRequired) {
        JsonObject json = response.toJsonObject();
        if (isFilterRequired) {
            filterOneElement(json);
        }
        return ResponseEntity.status(200).body(json.toJsonString());
    }



    protected ResponseEntity<String> responseSuccess(CytomineDomain response) {
        return ResponseEntity.status(200).body(response.toJSON());
    }

    protected ResponseEntity<String> responseSuccess(CytomineSocialDomain response) {
        return ResponseEntity.status(200).body(response.toJsonObject().toJsonString());
    }

    protected ResponseEntity<String> responseSuccess(String response) {
        return ResponseEntity.status(200).body(response);
    }

    protected ResponseEntity<String> responseSuccess(JsonObject response) {
        return responseSuccess(response, false);
    }

    protected ResponseEntity<String> responseSuccess(JsonObject response, boolean isFilterRequired) {
        if (isFilterRequired) {
            filterOneElement(response);
        }
        return ResponseEntity.status(200).body(response.toJsonString());
    }


    protected ResponseEntity<String> buildJson(CytomineDomain response, int code) {
        return ResponseEntity.status(code).body(response.toJSON());
    }

    protected ResponseEntity<String> buildJson(Map<String, Object> response, int code) {
        return ResponseEntity.status(code).body(convertObjectToJSON(response));
    }

    protected ResponseEntity<String> responseSuccess(CommandResponse commandResponse) {
        return ResponseEntity.status(commandResponse.getStatus()).body(
                convertObjectToJSON(commandResponse.getData()));
    }

    protected String convertObjectToJSON(Object o) {
        return JsonObject.toJsonString(o);
    }

    protected void filterOneElement(Map<String, Object> element) {

    }

    protected List<JsonObject> convertCytomineDomainListToJSON(List<? extends CytomineDomain> list) {
        List<JsonObject> results = new ArrayList<>();
        for (CytomineDomain cytomineDomain : list) {
            results.add(cytomineDomain.toJsonObject());
        }
        return results;
    }

    protected List<JsonObject> convertCytomineSocialDomainListToJSON(List<? extends CytomineSocialDomain> list) {
        List<JsonObject> results = new ArrayList<>();
        for (CytomineSocialDomain cytomineDomain : list) {
            results.add(cytomineDomain.toJsonObject());
        }
        return results;
    }

    protected List<JsonObject> convertCommandResponseToJSON(List<? extends CommandResponse> list) {
        List<JsonObject> results = new ArrayList<>();
        for (CommandResponse commandResponse : list) {
            results.add(commandResponse.toJsonObject());
        }
        return results;
    }

//    protected List<JsonObject> convertObjectToJSON(List list) {
//        List<JsonObject> results = new ArrayList<>();
//        for (Object o : list) {
//            results.add(convertObjectToJSON(o));
//        }
//        return results;
//    }

    protected String convertListToJSON(List o) {
        return JsonObject.toJsonString(o);
    }


    public ResponseEntity<String> add(ModelService service, JsonInput json) {
        return add(service, json, null);
    }

    public ResponseEntity<String> add(ModelService service, String json) {
        JsonInput data;
        try {
            data = new ObjectMapper().readValue(json, JsonMultipleObject.class);
            // If fails to parse as a single object, parse as a list
        } catch (Exception ex) {
            try {
                data = new ObjectMapper().readValue(json, JsonSingleObject.class);
            } catch (JsonProcessingException e) {
                throw new WrongArgumentException("Json not valid");
            }
        }
        return add(service, data);
    }



    public JsonObject addMultiple(ModelService service, List<JsonObject> json) {
        return service.addMultiple(json);
    }

    /**
     * Call add function for this service with the json
     * json parameter can be an array or a single item
     * If json is array => add multiple item
     * otherwise add single item
     * @param service Service for this domain
     * @param json JSON data
     * @return response
     */
    public ResponseEntity<String> add(ModelService service, JsonInput json, Task task) {
        try {
            if (json instanceof JsonMultipleObject) {
                return responseSuccess(addMultiple(service, ((JsonMultipleObject)json)));
            } else {
                JsonObject jsonObject = json instanceof JsonObject ? (JsonObject) json : ((JsonSingleObject)json);
                CommandResponse result = addOne(service, jsonObject, task);
                if (result != null) {
                    return responseSuccess(result);
                }
            }
        } catch (CytomineException e) {
            log.error("add error:" + e.msg);
            log.error(e.toString(), e);
            return buildJson(Map.of("success", false, "errors", e.msg), e.code);
        }
        return null;
    }

    public ResponseEntity<String> update(ModelService service, JsonObject json) {
        return update(service, json, null);
    }

    /**
     * Call update function for this service with the json
     * @param service Service for this domain
     * @param json JSON data
     * @return response
     */
    public ResponseEntity<String> update(ModelService service, JsonObject json, Task task) {
        try {
            CytomineDomain domain =  service.retrieve(json);
            CommandResponse result = service.update(domain, json, null, task);
            return responseSuccess(result);
        } catch (CytomineException e) {
            log.error(e.toString());
            return buildJson(Map.of("success", false, "errors", e.msg), e.code);
        }
    }

    /**
     * Call delete function for this service with the json
     * @param service Service for this domain
     * @param json JSON data
     * @return response
     */
    public ResponseEntity<String> delete(ModelService service, JsonObject json, Task task) {
        try {
            CytomineDomain domain =  service.retrieve(json);
            CommandResponse result = service.delete(domain, transactionService.start(), task,true);
            return responseSuccess(result);
        } catch (CytomineException e) {
            log.error(e.toString());
            return buildJson(Map.of("success", false, "errors", e.msg, "errorValues", e.values), e.code);
        }
    }

    /**
     * Call add function for this service with the json
     * @param service Service for this domain
     * @param json JSON data
     * @return response
     */
    public CommandResponse addOne(ModelService service, JsonObject json) {
        return service.add(json);
    }

    public CommandResponse addOne(ModelService service, JsonObject json, Task task) {
        if (task == null) {
            return service.add(json);
        } else {
            return service.add(json, task);
        }

    }

    /**
     * Build a response message for a domain not found
     * E.g. annotation 34 was not found
     * className = annotation, id = 34.
     * @param className Type of domain not found
     */
//    private static JsonObject buildJsonNotFound(String className, String id) {
//        return buildJsonNotFound(className, Map.of("id", id));
//    }
//
//    private static JsonObject buildJsonNotFound(String className, String filter, String id) {
//        return buildJsonNotFound(className, Map.of(filter, id));
//    }

    private static JsonObject buildJsonNotFound(String className, Map<String, Object> filters) {
        log.info("responseNotFound $className $id");
        log.error(className + " with filter " + filters + " does not exist");

        JsonObject jsonObject = new JsonObject();
        jsonObject.put("errors", Map.of(
                "message",  className + " not found with filters : " + filters,
                "errorCode", ErrorCode.NOT_FOUND_WITH_FILTERS.getValue(),
                "values", Map.of(
                        "id", filters.get("id"), "domain", className
                ))
        );
        return jsonObject;
    }

//    private static JsonObject buildJsonNotFound(String className, Long id) {
//        return buildJsonNotFound(className, String.valueOf(id));
//    }

    protected ResponseEntity<String> responseNotFound(String className, String id) {
        return responseNotFound(className, "id", id);
    }
    protected ResponseEntity<String> responseNotFound(String className, Long id) {
        return responseNotFound(className, "id", String.valueOf(id));
    }
    protected ResponseEntity<String> responseNotFound(String className, String filter, String id) {
        return responseNotFound(className, Map.of(filter, id));
    }
    protected ResponseEntity<String> responseNotFound(String className, String filter, Long id) {
        return responseNotFound(className, Map.of(filter, String.valueOf(id)));
    }
    protected ResponseEntity<String> responseNotFound(String className, Map<String, Object> filters) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildJsonNotFound(className, filters).toJsonString());
    }

    protected RequestParams retrieveRequestParam() {
        Map<String, String[]> parameterMap = request.getParameterMap();
        RequestParams flatMap = new RequestParams();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String value = null;
            if (entry.getValue()!=null && entry.getValue().length>1) {
                throw new CytomineMethodNotYetImplementedException("Multiple request params are not supported in this method");
            } else if (entry.getValue()!=null && entry.getValue().length==1) {
                value = entry.getValue()[0];
            }
            if (value!=null) {
                value = URLDecoder.decode(value, Charset.defaultCharset());
            }
            flatMap.put(URLDecoder.decode(entry.getKey(), Charset.defaultCharset()), value);
        }
        return flatMap;
    }


    protected void responseImageByteArray(String contentType, byte[] bytes) throws IOException {
        response.setContentLength(bytes.length);
        response.setStatus(200);
        response.setHeader("Connection", "Keep-Alive");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Type", contentType);
        try(OutputStream os = response.getOutputStream()) {
            os.write(bytes , 0, bytes.length);
            os.flush();
        }
    }

    protected void responseFile(String name, byte[] array) throws IOException {
        response.setStatus(200);
        response.setHeader("Content-Type", "application/octet-stream");
        response.setHeader("Content-disposition", "attachment; filename=" + name);
        try(OutputStream os = response.getOutputStream()) {
            os.write(array , 0, array.length);
            os.flush();
        }
    }

    protected void responseReportFile(String name, byte[] array, String format) throws IOException {
        response.setStatus(200);
        switch (format) {
            case "pdf":
                response.setHeader("Content-Type", "application/pdf");
                break;
            case "csv":
                response.setHeader("Content-Type", "text/csv");
                break;
            case "xls":
                response.setHeader("Content-Type", "application/octet-stream");
                break;
        }
        response.setHeader("Content-disposition", "attachment; filename=" + name);
        try(OutputStream os = response.getOutputStream()) {
            os.write(array , 0, array.length);
            os.flush();
        }
    }

    protected void responseString(String contentType, String string) throws IOException {
        response.setContentType(contentType);
        response.setStatus(200);
        response.getWriter().write(string);
        response.getWriter().flush();
    }

    /**
     * Response an image as a HTTP response
     * @param bytes Image
     */
    protected void responseByteArray(byte[] bytes, String expectedFormat)  {
        try {
            RequestParams params = retrieveRequestParam();
            String format = expectedFormat;
            if (params.isTrue("alphaMask") || params.isValue("type", "alphaMask")) {
                format = "png";
            }

            if (format.equals("jpg")) {
                if (request.getMethod().equals("HEAD")) {
                    responseString("image/jpeg", "");
                } else {
                    responseImageByteArray("image/jpeg", bytes);
                }
            } else if (format.equals("tiff") || format.equals("tif")) {
                if (request.getMethod().equals("HEAD")) {
                    responseString("image/tiff", "");
                } else {
                    responseImageByteArray("image/tiff", bytes);
                }
            } else if (format.equals("png")) {
                if (request.getMethod().equals("HEAD")) {
                    responseString("image/png", "");
                } else {
                    responseImageByteArray("image/png", bytes);
                }
            } else {
                throw new InvalidRequestException("Format " + format + " is not supported");
            }
        }
        catch (IOException e) {
            log.error("Cannot response bytes from controller", e);
        }
    }
}
