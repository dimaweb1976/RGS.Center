SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS public.automatic_measurements
(
    run_id UUID NOT NULL,
    object_number INTEGER NOT NULL CHECK (object_number > 0),
    sample_code VARCHAR(256) NOT NULL,
    coordinate_x DOUBLE PRECISION NOT NULL,
    coordinate_y DOUBLE PRECISION NOT NULL,
    gross_mass_grams NUMERIC(18, 6),
    net_mass_grams NUMERIC(18, 6),
    measuring_time_minutes BIGINT NOT NULL,
    volume_ml DOUBLE PRECISION NOT NULL,
    geometry VARCHAR(32) NOT NULL,
    tare_mass_grams DOUBLE PRECISION NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    spectrum_reference TEXT NOT NULL,
    error_message TEXT NOT NULL,
    PRIMARY KEY (run_id, object_number)
);

CREATE INDEX IF NOT EXISTS idx_automatic_measurements_sample
    ON public.automatic_measurements (sample_code, started_at);

CREATE INDEX IF NOT EXISTS idx_automatic_measurements_started
    ON public.automatic_measurements (started_at);

COMMENT ON TABLE public.automatic_measurements IS
    'Terminal object results produced by automatic measurement runs';
