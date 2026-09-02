package com.rgs.center.api.auth;

/** Credentials submitted by a separate frontend. */
public record LoginRequest(String login, String password) {
}
