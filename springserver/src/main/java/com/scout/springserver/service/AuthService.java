package com.scout.springserver.service;

import com.scout.springserver.exception.DomainExceptions.EmailAlreadyExistsException;
import com.scout.springserver.exception.DomainExceptions.InvalidCredentialsException;
import com.scout.springserver.exception.DomainExceptions.UserNotFoundException;
import com.scout.springserver.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * backend/src/services/auth.service.ts. Passwords are bcrypt with cost 10,
 * so hashes created by the Express server verify here and vice versa.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JdbcClient jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public AuthService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String hashPassword(String password) {
        return encoder.encode(password);
    }

    public boolean comparePassword(String password, String hashedPassword) {
        return encoder.matches(password, hashedPassword);
    }

    public User authenticateUser(String email, String password) {
        User user = jdbc.sql("SELECT * FROM users WHERE email = :email LIMIT 1")
                .param("email", email)
                .query(User.ROW_MAPPER)
                .optional()
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!comparePassword(password, user.password())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        log.info("User {} authenticated successfully", user.email());
        return user;
    }

    /** id, name, email and role only (created_at/updated_at/password left null). */
    public User getUserById(int id) {
        return jdbc.sql("SELECT id, name, email, role FROM users WHERE id = :id LIMIT 1")
                .param("id", id)
                .query((rs, i) -> new User(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        null,
                        rs.getString("role"),
                        null,
                        null))
                .optional()
                .orElseThrow(UserNotFoundException::new);
    }

    public User createUser(String name, String email, String password, String role) {
        boolean exists = jdbc.sql("SELECT 1 FROM users WHERE email = :email LIMIT 1")
                .param("email", email)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (exists) throw new EmailAlreadyExistsException();

        try {
            User user = jdbc.sql("""
                            INSERT INTO users (name, email, password, role)
                            VALUES (:name, :email, :password, :role)
                            RETURNING *
                            """)
                    .param("name", name)
                    .param("email", email)
                    .param("password", hashPassword(password))
                    .param("role", role)
                    .query(User.ROW_MAPPER)
                    .single();

            log.info("User {} created successfully", user.email());
            return user;
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent sign-up for the same email.
            throw new EmailAlreadyExistsException();
        }
    }

    public User createUser(String name, String email, String password) {
        return createUser(name, email, password, "user");
    }
}
