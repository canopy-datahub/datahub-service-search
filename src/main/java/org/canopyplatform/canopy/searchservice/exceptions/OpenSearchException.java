package org.canopyplatform.canopy.searchservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class OpenSearchException extends RuntimeException{

    public OpenSearchException(String message){
        super(message);
    }

}
