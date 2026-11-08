package com.routemate.common;

import com.routemate.auth.application.AnonymousSessions.Unauthorized;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(Unauthorized.class)
    ResponseEntity<ProblemDetail> unauthorized() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid bearer session is required.");
        problem.setProperty("code", "UNAUTHORIZED");
        return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .cacheControl(CacheControl.noStore()).body(problem);
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidRequest(Exception ignored) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the destination name and coordinates.");
        problem.setProperty("code", "INVALID_REQUEST");
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(problem);
    }
}
