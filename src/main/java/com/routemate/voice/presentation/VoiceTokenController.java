package com.routemate.voice.presentation;
import com.routemate.voice.application.RoomVoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
class VoiceTokenController {
    private final RoomVoice voice;
    VoiceTokenController(RoomVoice voice){this.voice=voice;}
    @PostMapping("/api/v1/voice/token") Object token(@RequestHeader(value="Authorization",required=false)String auth,@Valid @RequestBody Request request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(voice.issue(auth,request.roomId()));
    }
    record Request(@NotNull UUID roomId){}
}
