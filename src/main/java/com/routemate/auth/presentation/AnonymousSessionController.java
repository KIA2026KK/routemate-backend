package com.routemate.auth.presentation;

import com.routemate.auth.application.AnonymousSessions;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AnonymousSessionController {
    private final AnonymousSessions sessions;
    AnonymousSessionController(AnonymousSessions sessions) { this.sessions = sessions; }
    @PostMapping({"/api/v1/auth/device-sessions", "/api/v1/auth/anonymous"})
    ResponseEntity<AnonymousSessions.IssuedSession> create() {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(sessions.create());
    }
}
