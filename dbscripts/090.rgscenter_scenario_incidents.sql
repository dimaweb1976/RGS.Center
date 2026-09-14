SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS public.scenario_incidents
(
    id UUID PRIMARY KEY,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(128) NOT NULL,
    code VARCHAR(128) NOT NULL,
    message TEXT NOT NULL,
    run_id UUID,
    object_number INTEGER,
    workflow_step VARCHAR(64),
    raised_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    cleared_by VARCHAR(255),
    cleared_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT scenario_incidents_severity_check
        CHECK (severity IN ('WARNING', 'ALERT', 'EMERGENCY')),
    CONSTRAINT scenario_incidents_status_check
        CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'CLEARED'))
);

CREATE INDEX IF NOT EXISTS idx_scenario_incidents_status
    ON public.scenario_incidents (status, raised_at);

COMMENT ON TABLE public.scenario_incidents IS
    'Workflow safety incidents and attributed operator recovery decisions';
