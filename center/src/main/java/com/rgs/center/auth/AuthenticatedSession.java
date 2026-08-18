package com.rgs.center.auth;

import java.util.UUID;

/** Validated database session paired with the resolved application principal. */
record AuthenticatedSession(UUID sessionId, AuthPrincipal principal) {
}
