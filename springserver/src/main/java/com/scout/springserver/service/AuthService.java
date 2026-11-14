package com.scout.springserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.scout.springserver.dto.SignInRequestDTO;
import com.scout.springserver.dto.SignUpRequestDTO;
import com.scout.springserver.dto.UserResponseDTO;
import com.scout.springserver.exception.EmailAlreadyExistsException;
import com.scout.springserver.exception.InvalidCredentialsException;
import com.scout.springserver.exception.UserNotFoundException;
import com.scout.springserver.mapper.UserMapper;
import com.scout.springserver.model.User;
import com.scout.springserver.repository.UserRepository;

/**
 * backend/src/services/auth.service.ts. Passwords are bcrypt with cost 10, so
 * hashes created by the Express server verify here and vice versa.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponseDTO createUser(SignUpRequestDTO signUpRequestDTO) {
        if (userRepository.existsByEmail(signUpRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordEncoder.encode(signUpRequestDTO.getPassword());

        User newUser;
        try {
            newUser = userRepository.saveAndFlush(UserMapper.toModel(signUpRequestDTO, passwordHash));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent sign-up for the same email (unique constraint).
            throw new EmailAlreadyExistsException();
        }

        log.info("User {} created successfully", newUser.getEmail());
        return UserMapper.toDTO(newUser);
    }

    public UserResponseDTO authenticateUser(SignInRequestDTO signInRequestDTO) {
        User user = userRepository.findByEmail(signInRequestDTO.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!passwordEncoder.matches(signInRequestDTO.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        log.info("User {} authenticated successfully", user.getEmail());
        return UserMapper.toDTO(user);
    }

    public UserResponseDTO getUserById(Integer id) {
        User user = userRepository.findById(id)
            .orElseThrow(UserNotFoundException::new);
        return UserMapper.toDTO(user);
    }
}
