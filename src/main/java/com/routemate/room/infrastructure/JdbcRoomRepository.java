package com.routemate.room.infrastructure;

import com.routemate.room.domain.*;
import com.routemate.trip.domain.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoomRepository implements RoomRepository {
    private final JdbcTemplate jdbc;
    public JdbcRoomRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    // A transaction-scoped lock shared by instances keeps small-MVP lifecycle mutations ordered.
    @Override public void lock(){jdbc.query("select pg_advisory_xact_lock(20261109)",rs->{});}
    @Override public void expire(Instant now){
        var time=Timestamp.from(now);
        jdbc.update("update trip_sessions set ended_at=greatest(started_at,?) where ended_at is null and updated_at<=?",time,Timestamp.from(now.minus(Duration.ofHours(24))));
        jdbc.update("""
            update room_memberships m set left_at=? where left_at is null and
            (heartbeat_at<=? or exists(select 1 from rooms r where r.id=m.room_id and (r.closed_at is not null or r.expires_at<=?))
            or not exists(select 1 from trip_sessions t where t.user_id=m.user_id and t.ended_at is null))
            """,time,Timestamp.from(now.minusSeconds(90)),time);
        closeEmpty(now);
    }
    private void closeEmpty(Instant now){jdbc.update("""
        update rooms r set closed_at=? where closed_at is null and
        (expires_at<=? or not exists(select 1 from room_memberships m where m.room_id=r.id and m.left_at is null))
        """,Timestamp.from(now),Timestamp.from(now));}
    @Override public Optional<Trip> trip(UUID user){return jdbc.query("""
        select *, ST_Y(destination::geometry) latitude, ST_X(destination::geometry) longitude
        from trip_sessions where user_id=? and ended_at is null
        """,(rs,row)->new Trip(rs.getObject("id",UUID.class),user,rs.getObject("vehicle_id",UUID.class),
        new Destination(rs.getString("destination_name"),rs.getDouble("latitude"),rs.getDouble("longitude")),
        rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("updated_at").toInstant()),user).stream().findFirst();}
    @Override public void endTrip(UUID user,Instant now){
        leave(user,now);
        jdbc.update("update trip_sessions set ended_at=greatest(started_at,?) where user_id=? and ended_at is null",Timestamp.from(now),user);
    }
    private static final String ROOM_SELECT="""
        select r.id,r.name,r.is_public,r.capacity,r.expires_at,
        (select count(*) from room_memberships m where m.room_id=r.id and m.left_at is null) participants
        from rooms r
        """;
    private Room map(ResultSet rs)throws SQLException{return new Room(rs.getObject("id",UUID.class),rs.getString("name"),
        rs.getBoolean("is_public"),rs.getInt("capacity"),rs.getInt("participants"),rs.getTimestamp("expires_at").toInstant());}
    @Override public Optional<Room> room(UUID id){return jdbc.query(ROOM_SELECT+" where r.id=? and r.closed_at is null",(rs,row)->map(rs),id).stream().findFirst();}
    @Override public Optional<Membership> membership(UUID user){return jdbc.query("select id,room_id from room_memberships where user_id=? and left_at is null",
        (rs,row)->new Membership(rs.getObject("id",UUID.class),rs.getObject("room_id",UUID.class)),user).stream().findFirst();}
    @Override public Room create(UUID user,Trip trip,String name,boolean isPublic,int capacity,Instant now){
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into rooms(id,creator_id,name,is_public,capacity,center,created_at,expires_at)
            values(?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326)::geography,?,?)
            """,id,user,name.trim(),isPublic,capacity,trip.destination().longitude(),trip.destination().latitude(),Timestamp.from(now),Timestamp.from(now.plus(Duration.ofHours(2))));
        join(user,id,now);return room(id).orElseThrow();
    }
    @Override public void join(UUID user,UUID room,Instant now){jdbc.update("insert into room_memberships(id,room_id,user_id,joined_at,heartbeat_at) values(?,?,?,?,?)",
        UUID.randomUUID(),room,user,Timestamp.from(now),Timestamp.from(now));}
    @Override public void leave(UUID user,Instant now){
        jdbc.update("update room_memberships set left_at=? where user_id=? and left_at is null",Timestamp.from(now),user);closeEmpty(now);
    }
    @Override public void heartbeat(UUID user,Instant now){
        jdbc.update("update room_memberships set heartbeat_at=? where user_id=? and left_at is null",Timestamp.from(now),user);
        jdbc.update("update trip_sessions set updated_at=greatest(updated_at,?) where user_id=? and ended_at is null",Timestamp.from(now),user);
    }
    @Override public List<Nearby> nearby(UUID user,int radiusKm){return jdbc.query("""
        select r.id,r.name,r.is_public,r.capacity,r.expires_at,count(m.id) participants,
        round(ST_Distance(r.center,t.destination)) distance_meters
        from rooms r join trip_sessions t on t.user_id=? and t.ended_at is null
        left join room_memberships m on m.room_id=r.id and m.left_at is null
        where r.closed_at is null and r.is_public and ST_DWithin(r.center,t.destination,?)
        group by r.id,t.destination having count(m.id)<r.capacity order by distance_meters,r.id limit 50
        """,(rs,row)->new Nearby(map(rs),rs.getLong("distance_meters")),user,radiusKm*1000);}
}
