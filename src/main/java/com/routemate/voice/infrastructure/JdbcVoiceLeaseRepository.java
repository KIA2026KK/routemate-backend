package com.routemate.voice.infrastructure;
import com.routemate.voice.domain.VoiceLeaseRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
class JdbcVoiceLeaseRepository implements VoiceLeaseRepository {
    private final JdbcTemplate jdbc;
    JdbcVoiceLeaseRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public UUID identity(UUID membership,String hash){return jdbc.queryForObject("""
        insert into voice_leases(identity,membership_id,token_hash) values(?,?,?)
        on conflict(membership_id,token_hash) do update set membership_id=excluded.membership_id returning identity
        """,UUID.class,UUID.randomUUID(),membership,hash);}
    public Map<String,Set<String>> allowed(Instant now){
        Map<String,Set<String>> allowed=new HashMap<>();
        jdbc.query("""
            select m.room_id,l.identity from voice_leases l join room_memberships m on m.id=l.membership_id
            join auth_sessions a on a.token_hash=l.token_hash join rooms r on r.id=m.room_id
            where m.left_at is null and r.closed_at is null and r.expires_at>? and a.revoked_at is null and a.expires_at>?
            """,rs->{allowed.computeIfAbsent("rmv-"+rs.getObject("room_id"),ignored->new HashSet<>()).add(rs.getString("identity"));},Timestamp.from(now),Timestamp.from(now));
        return allowed;
    }
}
