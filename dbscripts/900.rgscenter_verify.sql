SET client_encoding = 'UTF8';

DO $$
DECLARE
    expected_tables INTEGER;
    expected_roles INTEGER;
    expected_users INTEGER;
    onlyone_rows INTEGER;
    onlyone_singleton_rows INTEGER;
    legacy_table regclass;
    settings_audit_table regclass;
    manual_measurements_table regclass;
    automatic_measurements_table regclass;
    scenario_incidents_table regclass;
BEGIN
    SELECT count(*)
    INTO expected_tables
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name IN (
          'roles',
          'users',
          'auth_sessions',
          'onlyone_lock'
      );

    SELECT count(*)
    INTO expected_roles
    FROM public.roles
    WHERE (id, rolename) IN (
        (1, 'service'),
        (2, 'administrator'),
        (3, 'operator')
    );

    SELECT count(*)
    INTO expected_users
    FROM public.users u
    JOIN public.roles r ON r.id = u.role
    WHERE (u.login, r.rolename) IN (
        ('service', 'service'),
        ('admin', 'administrator'),
        ('operator', 'operator')
    );

    legacy_table := to_regclass('public.appusers');
    settings_audit_table := to_regclass('public.device_settings_audit');
    manual_measurements_table := to_regclass('public.manual_measurements');
    automatic_measurements_table := to_regclass('public.automatic_measurements');
    scenario_incidents_table := to_regclass('public.scenario_incidents');

    IF expected_tables <> 4 THEN
        RAISE EXCEPTION 'RGSCenter schema is incomplete: expected 4 core tables, found %',
            expected_tables;
    END IF;

    SELECT count(*), count(*) FILTER (WHERE id = 1)
    INTO onlyone_rows, onlyone_singleton_rows
    FROM public.onlyone_lock;

    IF onlyone_rows <> 1 OR onlyone_singleton_rows <> 1 THEN
        RAISE EXCEPTION
            'onlyone_lock must contain exactly one singleton row with id = 1; found % rows',
            onlyone_rows;
    END IF;

    IF expected_roles <> 3 THEN
        RAISE EXCEPTION 'RGSCenter roles are incomplete: expected 3, found %',
            expected_roles;
    END IF;
    IF expected_users <> 3 THEN
        RAISE EXCEPTION 'RGSCenter default users are incomplete: expected 3, found %',
            expected_users;
    END IF;
    IF legacy_table IS NOT NULL THEN
        RAISE EXCEPTION 'Legacy table appusers was not migrated';
    END IF;
    IF settings_audit_table IS NULL THEN
        RAISE EXCEPTION 'Table device_settings_audit is missing';
    END IF;
    IF manual_measurements_table IS NULL THEN
        RAISE EXCEPTION 'Table manual_measurements is missing';
    END IF;
    IF automatic_measurements_table IS NULL THEN
        RAISE EXCEPTION 'Table automatic_measurements is missing';
    END IF;
    IF scenario_incidents_table IS NULL THEN
        RAISE EXCEPTION 'Table scenario_incidents is missing';
    END IF;
END
$$;

SELECT current_database() AS database_name,
       current_user AS deployment_user,
       pg_encoding_to_char(encoding) AS database_encoding
FROM pg_database
WHERE datname = current_database();

SELECT r.id AS role_id, r.rolename, u.login
FROM public.roles r
LEFT JOIN public.users u ON u.role = r.id
ORDER BY r.id, u.login;

SELECT count(*) AS auth_sessions
FROM public.auth_sessions;

-- Display the current lease and evaluate it using the backend boundary rule.
SELECT id,
       token,
       client_ip,
       expires_at,
       token IS NOT NULL
           AND expires_at >= LOCALTIMESTAMP AS busy
FROM public.onlyone_lock
WHERE id = 1;
