SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS public.manual_measurements
(
    id UUID PRIMARY KEY,
    sample_code VARCHAR(256) NOT NULL,
    gross_mass_grams NUMERIC(18, 6) NOT NULL,
    net_mass_grams NUMERIC(18, 6) NOT NULL,
    measuring_time_minutes BIGINT NOT NULL,
    volume_ml DOUBLE PRECISION NOT NULL,
    geometry VARCHAR(32) NOT NULL,
    tare_mass_grams DOUBLE PRECISION NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    spectrum_reference TEXT NOT NULL,
    error_message TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_manual_measurements_sample
    ON public.manual_measurements (sample_code, started_at);

CREATE INDEX IF NOT EXISTS idx_manual_measurements_started
    ON public.manual_measurements (started_at);

COMMENT ON TABLE public.manual_measurements IS
    'Terminal results of operator-controlled manual measurements';
