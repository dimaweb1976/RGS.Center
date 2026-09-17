# Dobot feedback-only connection

The default is the existing Dashboard + Feedback mode. To receive telemetry
without port 29999, restart the updated RGSCenter and open
`/wizard/settings/RoboticManipulator` as administrator.

1. Set `device.manipulator.host` to `127.0.0.1` (same machine as emulator).
2. Set feedback port to `30006` and check **Feedback-only - telemetry only**.
3. Click **Save settings**, then **Apply to device**.
4. In the emulator disable Dashboard stub, enable feedback 30006 at 1000 ms,
   use valid packet markers and click Start.
5. Check `/manipulator`: Mode Feedback-only, Dashboard disabled, fresh telemetry.

`rgs.device.manipulator.feedback-only=false` supplies the property default.
The persisted BOOLEAN `device.manipulator.feedback-only` in device_settings
overrides that default. No DB schema migration is needed. Switching the mode
requires Apply/reconnect and does not alter an existing session mid-stream.

Feedback-only disables all Dashboard commands and AI polling even if analogue
polling is configured on. Ready reports robot telemetry, not command capability.
Coordinates, DI/DO, hatch limits, command ID and active-low safety remain available.
Keep telemetry-stale-millis greater than the feedback period with margin (3500 ms
for 1000 ms packets). The emulator cannot supply this mode's analogue values.

To return to the real robot, uncheck Feedback-only and Save/Apply. Set the actual
robot host before applying. This change does not automatically change stored
addresses or stop/restart the running production application.
