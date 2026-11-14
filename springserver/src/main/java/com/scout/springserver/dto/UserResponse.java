package com.scout.springserver.dto;

/** The `user` object returned by sign-up, sign-in and /me. Never includes the password hash. */
public record UserResponse(int id, String analyticsId, String name, String email, String role) {}
