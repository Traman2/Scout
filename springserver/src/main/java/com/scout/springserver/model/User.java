package com.scout.springserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.scout.springserver.util.Timestamps;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/** Table `users` (backend/src/models/user.model.ts). */
public record User(
        int id,
        String name,
        String email,
        @JsonIgnore String password,
        String role,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static final RowMapper<User> ROW_MAPPER = (rs, i) -> new User(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getString("role"),
            Timestamps.read(rs, "created_at"),
            Timestamps.read(rs, "updated_at"));
}
