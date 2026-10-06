SET client_encoding = 'UTF8';

-- A singleton row stores the exclusive-access lease shared by all clients.
CREATE TABLE IF NOT EXISTS public.onlyone_lock
(
    id INTEGER PRIMARY KEY CHECK (id = 1),
    token VARCHAR(256),
    client_ip VARCHAR(45),
    expires_at TIMESTAMP WITHOUT TIME ZONE
);

-- Re-running this script must preserve an existing active lease.
INSERT INTO public.onlyone_lock (id, token, client_ip, expires_at)
VALUES (1, NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE public.onlyone_lock IS
    'Singleton lease used to grant exclusive access to one client at a time';
COMMENT ON COLUMN public.onlyone_lock.token IS
    'Opaque lease token; NULL when exclusive access is free';
COMMENT ON COLUMN public.onlyone_lock.client_ip IS
    'Diagnostic IP address of the client that acquired the lease';
COMMENT ON COLUMN public.onlyone_lock.expires_at IS
    'Lease is active when expires_at >= LOCALTIMESTAMP and expired when expires_at < LOCALTIMESTAMP';
