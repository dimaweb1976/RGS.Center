SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS public.auth_sessions
(
    id UUID PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    remote_address VARCHAR(64),
    user_agent VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_active
    ON public.auth_sessions (revoked_at, expires_at, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user
    ON public.auth_sessions (user_id);

COMMENT ON TABLE public.auth_sessions IS
    'Серверные сессии токен-аутентификации RGSCenter; хранится SHA-256 токена';
