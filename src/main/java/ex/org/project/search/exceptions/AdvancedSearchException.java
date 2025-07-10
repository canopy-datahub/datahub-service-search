package ex.org.project.search.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AdvancedSearchException extends RuntimeException{

    public AdvancedSearchException(String message){
        super(message);
    }

}
