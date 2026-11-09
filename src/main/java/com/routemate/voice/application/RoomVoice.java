package com.routemate.voice.application;
import com.routemate.voice.domain.*;
import com.routemate.room.domain.RoomFailure;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class RoomVoice {
    private final VoiceAuthorizations authorizations;
    private final VoiceGateway gateway;
    private final Clock clock;
    public RoomVoice(VoiceAuthorizations authorizations,VoiceGateway gateway,Clock clock){
        this.authorizations=authorizations;this.gateway=gateway;this.clock=clock;
    }
    public VoiceGateway.Grant issue(String auth,UUID roomId){
        var plan=authorizations.prepare(auth,roomId);
        var grant=gateway.issue(plan.roomId(),plan.capacity(),plan.identity(),clock.instant());
        authorizations.confirm(auth,plan);
        return grant;
    }
    public void maintain(){
        authorizations.expire();
        try { gateway.reconcile(authorizations); }
        catch(RoomFailure ignored){org.slf4j.LoggerFactory.getLogger(RoomVoice.class).warn("Voice cleanup deferred; database expiry retained");}
    }
}
