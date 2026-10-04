package com.claimsure.common.error;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String what, String id) {
        super(what + " '" + id + "' was not found");
    }
}
