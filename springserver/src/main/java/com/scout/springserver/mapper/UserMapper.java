package com.scout.springserver.mapper;

import com.scout.springserver.dto.SignUpRequestDTO;
import com.scout.springserver.dto.UserResponseDTO;
import com.scout.springserver.model.User;

public class UserMapper {

    public static UserResponseDTO toDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        return dto;
    }

    /** passwordHash must already be bcrypt-hashed. */
    public static User toModel(SignUpRequestDTO request, String passwordHash) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordHash);
        user.setRole("user");
        return user;
    }
}
