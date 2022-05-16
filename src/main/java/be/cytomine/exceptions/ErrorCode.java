package be.cytomine.exceptions;

public enum ErrorCode {
        NOT_FOUND(40400), // {{object}} not found
        NOT_FOUND_WITH_ID(40401), // {{object}} {{id}} not found
        NOT_FOUND_DELETED(40402), // The {{object}} has been deleted
        NOT_FOUND_NOT_EXIST(40403), // {{object}} {{id}} not exist!
        NOT_FOUND_USER_CANT_SET_KEYS(40404), // {{username}} user does not exist, cannot set its keys
        NOT_FOUND_INSTANCE(40405), // {{instance}} with id {{id}}
        NOT_FOUND_DOMAIN(40406), // {{domain}} {{json}} not found
        NOT_FOUND_BASE_ANNOTATION(40407), // You need to provide a 'baseAnnotation' parameter (annotation id/location = {{baseAnnotation}})!
        NOT_FOUND_DESCRIPTION(40408), // Description not found for domain {{domainClass}} {{id}}
        NOT_FOUND_USER(40409), // "{{user}} user does not exist, cannot set its keys"
        NOT_FOUND_WITH_FILTERS(40410), // {{className}} not found with filters : "id" {{id}}
        NOT_FOUND_NOT_ACL(40411), // User {{username}} or Object {{objectId}} are not in ACL
        NOT_FOUND_ACL_ERROR(40412), // ACL error {{className}} with id {{id}} was not found! Unable to process auth checking
        NOT_FOUND_ACL_ERROR_NULL_DOMAIN(40413), // ACL error: domain is null! Unable to process project auth checking
        NOT_FOUND_ACL_ERROR_EDITING_MODE(4015), // ACL error: project editing mode is unknown! Unable to process project auth checking
        NOT_FOUND_USER_NOT_SUPPORTED(40416), // User Cannot read current username. Object {{user}} is not supported
        NOT_FOUND_INSTANCE_CREATION(40417), // Cannot create instance of object: {{json}} Exception {{exceptionMessage}}
        NOT_FOUND_COMMAND(40418), // Command not supported
        NOT_FOUND_UNDO_OPERATION(40419), // You cannot delete your last operation!
        NOT_FOUND_ASK_PROPERTIES(40420), // You must ask at least one properties group for request.
        NOT_FOUND_NOT_VALID(40421), //  Request not valid : domainClassName = {{domainClassName}}, domainIdent = {{domainIdent}} and user = {{user}}.
        NOT_FOUND_IMAGE_SERVER(40422); // No image server defined in configuration
        private int value;

        private ErrorCode(int value){
            this.value = value;
        }

        public int getValue() {
            return value;
        }
}