SET client_encoding = 'UTF8';

-- Singleton profile shared by automatic and manual measurement modes.
CREATE TABLE IF NOT EXISTS public.measurement_parameters
(
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    measuring_time_minutes BIGINT NOT NULL
        CHECK (measuring_time_minutes BETWEEN 1 AND 1440),
    volume_ml DOUBLE PRECISION NOT NULL
        CHECK (volume_ml > 0 AND volume_ml <= 10000),
    geometry VARCHAR(32) NOT NULL,
    tare_mass_grams DOUBLE PRECISION NOT NULL
        CHECK (tare_mass_grams >= 0 AND tare_mass_grams <= 100000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO public.measurement_parameters
    (id, measuring_time_minutes, volume_ml, geometry,
     tare_mass_grams, version)
VALUES (1, 120, 300.0, 'BOTTLE', 0.0, 0)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE public.measurement_parameters IS
    'Current parameters shared by automatic and manual measurements';
