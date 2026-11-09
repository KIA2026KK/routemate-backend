package com.routemate.common;

import com.routemate.auth.application.AnonymousSessions.Unauthorized;
import com.routemate.vehicle.domain.VehicleRepository;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(com.routemate.room.domain.RoomFailure.class)
    ResponseEntity<ProblemDetail> roomFailure(com.routemate.room.domain.RoomFailure failure) {
        var status=switch(failure.reason){
            case TRIP_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
            case ALREADY_IN_ROOM,ROOM_FULL,TRIP_CHANGED -> HttpStatus.CONFLICT;
            case ROOM_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case NOT_IN_ROOM -> HttpStatus.FORBIDDEN;
            case VOICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return vehicleProblem(status,failure.reason.name(),"Check the room and trip state.");
    }
    @ExceptionHandler(VehicleRepository.Conflict.class)
    ResponseEntity<ProblemDetail> vehicleConflict() {
        return vehicleProblem(HttpStatus.CONFLICT, "VEHICLE_CONFLICT", "Vehicle account access must be confirmed.");
    }
    @ExceptionHandler(VehicleRepository.Required.class)
    ResponseEntity<ProblemDetail> vehicleRequired() {
        return vehicleProblem(HttpStatus.PRECONDITION_REQUIRED, "VEHICLE_REQUIRED", "Link a vehicle before registering a trip.");
    }
    @ExceptionHandler(VehicleRepository.MockDisabled.class)
    ResponseEntity<ProblemDetail> mockDisabled() {
        return vehicleProblem(HttpStatus.FORBIDDEN, "MOCK_VEHICLE_DISABLED", "Mock vehicles are disabled on this server.");
    }
    private ResponseEntity<ProblemDetail> vehicleProblem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
    @ExceptionHandler(Unauthorized.class)
    ResponseEntity<ProblemDetail> unauthorized() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid bearer session is required.");
        problem.setProperty("code", "UNAUTHORIZED");
        return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .cacheControl(CacheControl.noStore()).body(problem);
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalidRequest(Exception ignored) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the request fields and values.");
        problem.setProperty("code", "INVALID_REQUEST");
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(problem);
    }
}
