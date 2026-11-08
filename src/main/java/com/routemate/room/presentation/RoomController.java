package com.routemate.room.presentation;

import com.routemate.room.application.RoomSessions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class RoomController {
    private final RoomSessions rooms;
    public RoomController(RoomSessions rooms){this.rooms=rooms;}
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/api/v1/trips/me") Object trip(@RequestHeader(value="Authorization",required=false)String auth){return ok(rooms.currentTrip(auth).orElse(null));}
    @PostMapping("/api/v1/trips/me/end") Object end(@RequestHeader(value="Authorization",required=false)String auth){rooms.endTrip(auth);return ok(java.util.Map.of("ended",true));}
    @GetMapping("/api/v1/rooms/me") Object current(@RequestHeader(value="Authorization",required=false)String auth){return ok(rooms.current(auth).orElse(null));}
    @GetMapping("/api/v1/rooms/nearby") Object nearby(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(defaultValue="5")int radiusKm){return ok(rooms.nearby(auth,radiusKm));}
    @PostMapping("/api/v1/rooms") Object create(@RequestHeader(value="Authorization",required=false)String auth,@Valid @RequestBody Create request){return ok(rooms.create(auth,request.name(),request.isPublic(),request.capacity()));}
    @PostMapping("/api/v1/rooms/{id}/join") Object join(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable UUID id){return ok(rooms.join(auth,id));}
    @PostMapping("/api/v1/rooms/{id}/leave") Object leave(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable UUID id){rooms.leave(auth,id);return ok(java.util.Map.of("left",true));}
    @PostMapping("/api/v1/rooms/{id}/heartbeat") Object heartbeat(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable UUID id){return ok(rooms.heartbeat(auth,id));}
    record Create(@NotBlank @Size(max=60)String name,@NotNull Boolean isPublic,@NotNull @Min(2) @Max(8)Integer capacity){}
}
