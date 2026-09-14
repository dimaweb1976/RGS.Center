\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';

-- Run as a PostgreSQL administrator connected to the maintenance database.

\if :{?dbname}
\else
  \warn 'ERROR: dbname variable is not set'
  \quit 3
\endif

\if :{?dedicated_user_name}
\else
  \warn 'ERROR: dedicated_user_name variable is not set'
  \quit 3
\endif

\if :{?dedicated_user_password}
\else
  \warn 'ERROR: dedicated_user_password variable is not set'
  \quit 3
\endif

\if :{?dedicated_app_user_name}
\else
  \warn 'ERROR: dedicated_app_user_name variable is not set'
  \quit 3
\endif

\if :{?dedicated_app_user_password}
\else
  \warn 'ERROR: dedicated_app_user_password variable is not set'
  \quit 3
\endif

-- Create cluster roles once and synchronize their login passwords on every run.
SELECT format('CREATE ROLE %I LOGIN', :'dedicated_user_name')
WHERE NOT EXISTS (
    SELECT FROM pg_roles WHERE rolname = :'dedicated_user_name'
)
\gexec

ALTER ROLE :"dedicated_user_name"
    WITH LOGIN PASSWORD :'dedicated_user_password';

SELECT format('CREATE ROLE %I LOGIN', :'dedicated_app_user_name')
WHERE NOT EXISTS (
    SELECT FROM pg_roles WHERE rolname = :'dedicated_app_user_name'
)
\gexec

ALTER ROLE :"dedicated_app_user_name"
    WITH LOGIN PASSWORD :'dedicated_app_user_password';

-- Development deployment follows the BSI template: the selected database is
-- recreated on every run. Never point this script at a database to be retained.
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = :'dbname'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS :"dbname";

CREATE DATABASE :"dbname"
WITH
    TEMPLATE = template0
    OWNER = :"dedicated_user_name"
    ENCODING = 'UTF8'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = false;

GRANT CONNECT, TEMPORARY ON DATABASE :"dbname" TO :"dedicated_app_user_name";

\connect :"dbname"
SET client_encoding = 'UTF8';

ALTER SCHEMA public OWNER TO :"dedicated_user_name";
GRANT USAGE ON SCHEMA public TO :"dedicated_app_user_name";

SET ROLE :"dedicated_user_name";

\ir 010.rgscenter_roles.sql
\ir 020.rgscenter_users.sql
\ir 030.rgscenter_auth_sessions.sql
\ir 035.rgscenter_device_settings.sql
\ir 037.rgscenter_device_settings_audit.sql
\ir 040.rgscenter_default_users.sql
\ir 050.rgscenter_onlyone_lock.sql
\ir 060.rgscenter_measurement_parameters.sql
\ir 070.rgscenter_manual_measurements.sql
\ir 080.rgscenter_automatic_measurements.sql
\ir 090.rgscenter_scenario_incidents.sql

-- The owner creates all objects; the application role only accesses data.
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public TO :"dedicated_app_user_name";
GRANT USAGE, SELECT
    ON ALL SEQUENCES IN SCHEMA public TO :"dedicated_app_user_name";

ALTER DEFAULT PRIVILEGES FOR ROLE :"dedicated_user_name" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES
    TO :"dedicated_app_user_name";

ALTER DEFAULT PRIVILEGES FOR ROLE :"dedicated_user_name" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES
    TO :"dedicated_app_user_name";

RESET ROLE;
