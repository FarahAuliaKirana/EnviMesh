-- =====================================================================
-- Quick test for the EnviMesh schema. Run AFTER 01_schema.sql.
-- It creates a test node, sends three readings, shows the result,
-- then deletes everything it created.
-- =====================================================================

insert into public.nodes (id, name, latitude, longitude)
values ('TEST-01', 'Test node', -0.0263, 109.3425);

-- 1) normal reading: no alert
insert into public.readings (node_id, water_level_cm, soil_moisture_pct, air_temp_c, air_humidity_pct, battery_pct, source)
values ('TEST-01', 25, 55, 29.5, 82, 90, 'manual');

-- 2) 45 cm below surface: warning alert
insert into public.readings (node_id, water_level_cm, battery_pct, source)
values ('TEST-01', 45, 89, 'manual');

-- 3) 55 cm below surface + broken sensor: danger + sensor_error alerts
insert into public.readings (node_id, water_level_cm, battery_pct, error_code, source)
values ('TEST-01', 55, 88, 'SOIL_SENSOR_TIMEOUT', 'manual');

-- Expect 3 alerts: warning, danger, sensor_error
select level, water_level_cm, message, status from public.alerts where node_id = 'TEST-01' order by id;

-- Expect status 'sensor_error' (latest reading has an error code)
select id, water_level_cm, status, battery_pct from public.node_latest where id = 'TEST-01';

-- Clean up (deletes the node, its thresholds, readings and alerts)
delete from public.nodes where id = 'TEST-01';
