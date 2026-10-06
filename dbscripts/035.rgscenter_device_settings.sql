SET client_encoding = 'UTF8';

-- Persistent connection settings edited by authenticated administrators.
CREATE TABLE IF NOT EXISTS public.device_settings
(
    string_id VARCHAR(128) PRIMARY KEY,
    device_code VARCHAR(64) NOT NULL,
    type SMALLINT NOT NULL CHECK (type BETWEEN 1 AND 4),
    boolean_value BOOLEAN,
    long_value BIGINT,
    double_value DOUBLE PRECISION,
    string_value TEXT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER REFERENCES public.users(id),
    CONSTRAINT ck_device_settings_typed_value CHECK
    (
        (type = 1 AND boolean_value IS NOT NULL
                  AND long_value IS NULL
                  AND double_value IS NULL
                  AND string_value IS NULL)
        OR
        (type = 2 AND boolean_value IS NULL
                  AND long_value IS NOT NULL
                  AND double_value IS NULL
                  AND string_value IS NULL)
        OR
        (type = 3 AND boolean_value IS NULL
                  AND long_value IS NULL
                  AND double_value IS NOT NULL
                  AND string_value IS NULL)
        OR
        (type = 4 AND boolean_value IS NULL
                  AND long_value IS NULL
                  AND double_value IS NULL
                  AND string_value IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_device_settings_device
    ON public.device_settings (device_code, string_id);

CREATE INDEX IF NOT EXISTS idx_device_settings_updated_by
    ON public.device_settings (updated_by);

COMMENT ON TABLE public.device_settings IS
    'Typed connection settings editable by authenticated RGSCenter administrators';
COMMENT ON COLUMN public.device_settings.version IS
    'Optimistic-lock version supplied by the browser during update';
COMMENT ON COLUMN public.device_settings.updated_by IS
    'Administrator who last changed the setting';

-- Deployment supplies defaults only for missing rows. Browser changes survive
-- repeated script execution because ON CONFLICT never overwrites existing data.
INSERT INTO public.device_settings
    (string_id, device_code, type,
     boolean_value, long_value, double_value, string_value)
VALUES
    ('device.camera.ip', 'camera', 4, NULL, NULL, NULL, '192.168.1.13'),
    ('device.lenovo-pc.ip', 'lenovo-pc', 4, NULL, NULL, NULL, '192.168.201.20'),

    ('device.nicole.ip', 'nicole', 4, NULL, NULL, NULL, '127.0.0.1'),
    ('device.nicole.port', 'nicole', 2, NULL, 5000, NULL, NULL),
    ('device.nicole.connect-timeout-millis', 'nicole', 2, NULL, 2000, NULL, NULL),
    ('device.nicole.read-timeout-millis', 'nicole', 2, NULL, 250, NULL, NULL),
    ('device.nicole.response-timeout-millis', 'nicole', 2, NULL, 3000, NULL, NULL),

    ('device.boson.ip', 'boson', 4, NULL, NULL, NULL, '192.168.1.14'),

    ('device.manipulator.host', 'RoboticManipulator', 4, NULL, NULL, NULL, '192.168.5.1'),
    ('device.manipulator.command-port', 'RoboticManipulator', 2, NULL, 29999, NULL, NULL),
    ('device.manipulator.feedback-port', 'RoboticManipulator', 2, NULL, 30006, NULL, NULL),
    ('device.manipulator.connect-timeout-millis', 'RoboticManipulator', 2, NULL, 2000, NULL, NULL),
    ('device.manipulator.read-timeout-millis', 'RoboticManipulator', 2, NULL, 250, NULL, NULL),
    ('device.manipulator.response-timeout-millis', 'RoboticManipulator', 2, NULL, 3000, NULL, NULL),

    ('device.barcode-scanner.port', 'barcode-scanner', 4, NULL, NULL, NULL, 'COM8'),
    ('device.barcode-scanner.baud-rate', 'barcode-scanner', 2, NULL, 9600, NULL, NULL),
    ('device.barcode-scanner.read-timeout-millis', 'barcode-scanner', 2, NULL, 250, NULL, NULL),
    ('device.barcode-scanner.response-timeout-millis', 'barcode-scanner', 2, NULL, 1000, NULL, NULL),

    ('device.gdrm.port', 'gdrm', 4, NULL, NULL, NULL, 'COM3'),
    ('device.gdrm.initial-baud-rate', 'gdrm', 2, NULL, 9600, NULL, NULL),
    ('device.gdrm.read-timeout-millis', 'gdrm', 2, NULL, 250, NULL, NULL),
    ('device.gdrm.response-timeout-millis', 'gdrm', 2, NULL, 1200, NULL, NULL),

    ('device.scales.port', 'scales', 4, NULL, NULL, NULL, 'COM7'),
    ('device.scales.baud-rate', 'scales', 2, NULL, 9600, NULL, NULL),
    ('device.scales.read-timeout-millis', 'scales', 2, NULL, 250, NULL, NULL),
    ('device.scales.response-timeout-millis', 'scales', 2, NULL, 700, NULL, NULL),

    ('device.shp.port', 'shp', 4, NULL, NULL, NULL, 'COM5'),
    ('device.shp.baud-rate', 'shp', 2, NULL, 9600, NULL, NULL),
    ('device.shp.read-timeout-millis', 'shp', 2, NULL, 250, NULL, NULL),
    ('device.shp.response-timeout-millis', 'shp', 2, NULL, 1200, NULL, NULL),

    ('device.gamma-pro.host', 'gamma-pro', 4, NULL, NULL, NULL, '127.0.0.1'),
    ('device.gamma-pro.port', 'gamma-pro', 2, NULL, 37569, NULL, NULL),
    ('device.gamma-pro.connect-timeout-millis', 'gamma-pro', 2, NULL, 2000, NULL, NULL),
    ('device.gamma-pro.read-timeout-millis', 'gamma-pro', 2, NULL, 250, NULL, NULL),
    ('device.gamma-pro.response-timeout-millis', 'gamma-pro', 2, NULL, 3000, NULL, NULL),
    ('device.gamma-pro.charset', 'gamma-pro', 4, NULL, NULL, NULL, 'windows-1251')
ON CONFLICT (string_id) DO NOTHING;
