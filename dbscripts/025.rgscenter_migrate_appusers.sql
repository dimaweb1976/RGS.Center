SET client_encoding = 'UTF8';

SELECT to_regclass('public.appusers') IS NOT NULL AS legacy_appusers_exists
\gset

\if :legacy_appusers_exists
BEGIN;

LOCK TABLE public.appusers IN SHARE ROW EXCLUSIVE MODE;

-- Полей active в новой модели нет. Поэтому переносим только активные записи,
-- сохраняя их BCrypt-хэши. Неизвестные старые роли получают роль operator.
INSERT INTO public.users (login, hash, role)
SELECT a.login,
       a.scrthsh,
       CASE lower(a.login)
           WHEN 'service' THEN 1
           WHEN 'admin' THEN 2
           WHEN 'operator' THEN 3
           ELSE 3
       END
FROM public.appusers a
WHERE a.active
ON CONFLICT (login) DO NOTHING;

SELECT to_regclass('public.auth_sessions') IS NOT NULL AS legacy_sessions_exists
\gset

\if :legacy_sessions_exists
LOCK TABLE public.auth_sessions IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE migrated_user_ids ON COMMIT DROP AS
SELECT a.id AS old_id, u.id AS new_id
FROM public.appusers a
JOIN public.users u ON u.login = a.login
WHERE a.active;

DO $$
DECLARE
    constraint_name name;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'public.auth_sessions'::regclass
          AND c.contype = 'f'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.auth_sessions DROP CONSTRAINT %I',
            constraint_name
        );
    END LOOP;
END
$$;

DELETE FROM public.auth_sessions s
WHERE NOT EXISTS (
    SELECT 1
    FROM migrated_user_ids m
    WHERE m.old_id = s.user_id
);

UPDATE public.auth_sessions s
SET user_id = m.new_id
FROM migrated_user_ids m
WHERE s.user_id = m.old_id;

ALTER TABLE public.auth_sessions
    ALTER COLUMN user_id TYPE INTEGER USING user_id::INTEGER;

ALTER TABLE public.auth_sessions
    ADD CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
\endif

-- DROP без CASCADE намеренно останавливает миграцию при неизвестных связях.
DROP TABLE public.appusers;

COMMIT;
\endif
