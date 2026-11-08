package com.routemate.voice.infrastructure;
import com.routemate.voice.application.RoomVoice;
import org.slf4j.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
@Component
@EnableScheduling
class RoomMaintenance {
    private static final Logger LOG=LoggerFactory.getLogger(RoomMaintenance.class);
    private final RoomVoice voice;
    RoomMaintenance(RoomVoice voice){this.voice=voice;}
    @Scheduled(fixedDelayString="${routemate.maintenance-delay-ms:5000}")
    void run(){try{voice.maintain();}catch(Exception ignored){LOG.warn("Room maintenance delayed; retrying on next schedule");}}
}
