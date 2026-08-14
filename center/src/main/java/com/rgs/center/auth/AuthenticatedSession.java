package com.rgs.center.auth;

import java.util.UUID;

record AuthenticatedSession(UUID sessionId, AuthPrincipal principal) {
}
