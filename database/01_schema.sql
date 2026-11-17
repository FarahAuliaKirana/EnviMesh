-- =====================================================================
-- EnviMesh database schema (Supabase / PostgreSQL)
-- Run ONCE in Supabase: SQL Editor > New query > paste everything > Run.
-- Safe to run again: it uses "if not exists" / "or replace".
--
-- Tables
--   nodes       one row per sensor node (location, interval, battery, last seen)
--   thresholds  water-level limits per node (warning 40 cm, danger 50 cm by default)
--   readings    every measurement sent by a node (via gateway or simulator)
--   alerts      created automatically by the database when a reading crosses a limit
-- View
--   node_latest latest reading + status (good / warning / danger / sensor_error / offline)
--               per node, for the dashboard map (UC-03)
--
-- water_level_cm = depth of the water table BELOW the peat surface.
-- Bigger number = drier peat = higher fire risk.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Tables
-- ---------------------------------------------------------------------

create table if not exists public.nodes (
  id                text primary key,                      -- e.g. 'Node-01'
  name              text,
  latitude          double precision not null,
  longitude         double precision not null,
  wake_interval_min integer not null default 30
                    check (wake_interval_min between 1 and 1440),
  battery_pct       integer check (battery_pct between 0 and 100),
  last_seen_at      timestamptz,
  is_active         boolean not null default true,
  created_at        timestamptz not null default now()
);

create table if not exists public.thresholds (
  node_id     text primary key references public.nodes(id) on delete cascade,
  warning_cm  integer not null default 40 check (warning_cm > 0),
  danger_cm   integer not null default 50 check (danger_cm > 0),
  updated_at  timestamptz not null default now(),
  check (danger_cm >= warning_cm)
);

create table if not exists public.readings (
  id                bigint generated always as identity primary key,
  node_id           text not null references public.nodes(id) on delete cascade,
  measured_at       timestamptz not null default now(),   -- time at the node
  received_at       timestamptz not null default now(),   -- time it reached the server
  water_level_cm    numeric(6,1),
  soil_moisture_pct numeric(5,1),
  air_temp_c        numeric(4,1),
  air_humidity_pct  numeric(5,1),
  soil_temp_c       numeric(4,1),
  battery_pct       integer check (battery_pct between 0 and 100),
  latitude          double precision,                     -- optional, from the node's GPS
  longitude         double precision,
  error_code        text,                                 -- set when a sensor fails (UC-01)
  possible_fire     boolean not null default false,       -- possible-fire flag (UC-01)
  source            text not null default 'gateway'
                    check (source in ('gateway', 'simulator', 'manual'))
);

create index if not exists readings_node_time_idx on public.readings (node_id, measured_at desc);
create index if not exists readings_time_idx      on public.readings (measured_at desc);

create table if not exists public.alerts (
  id              bigint generated always as identity primary key,
  node_id         text not null references public.nodes(id) on delete cascade,
  reading_id      bigint references public.readings(id) on delete set null,
  level           text not null
                  check (level in ('warning', 'danger', 'possible_fire', 'sensor_error')),
  water_level_cm  numeric(6,1),
  latitude        double precision,
  longitude       double precision,
  message         text,
  status          text not null default 'active'
                  check (status in ('active', 'acknowledged', 'resolved')),
  action_note     text,                                   -- what the operator did (UC-04)
  handled_by      text,
  handled_at      timestamptz,
  created_at      timestamptz not null default now()
);

create index if not exists alerts_status_idx on public.alerts (status, created_at desc);
create index if not exists alerts_node_idx   on public.alerts (node_id, created_at desc);


-- ---------------------------------------------------------------------
-- 2. Automation
-- ---------------------------------------------------------------------

-- Every new node gets default thresholds (40 / 50 cm).
create or replace function public.create_default_threshold()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into thresholds (node_id) values (new.id) on conflict (node_id) do nothing;
  return new;
end;
$$;

drop trigger if exists nodes_default_threshold on public.nodes;
create trigger nodes_default_threshold
  after insert on public.nodes
  for each row execute function public.create_default_threshold();


-- Opens an alert unless the same node already has an active alert of the same level.
create or replace function public.open_alert(
  p_node    text,
  p_reading bigint,
  p_level   text,
  p_water   numeric,
  p_lat     double precision,
  p_lon     double precision,
  p_message text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (
    select 1 from alerts
     where node_id = p_node and level = p_level and status = 'active'
  ) then
    insert into alerts (node_id, reading_id, level, water_level_cm, latitude, longitude, message)
    values (p_node, p_reading, p_level, p_water, p_lat, p_lon, p_message);
  end if;
end;
$$;


-- Runs after every new reading: updates the node and creates alerts (UC-02).
create or replace function public.handle_new_reading()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_lat     double precision;
  v_lon     double precision;
  v_warning integer;
  v_danger  integer;
begin
  update nodes
     set last_seen_at = greatest(coalesce(last_seen_at, new.measured_at), new.measured_at),
         battery_pct  = coalesce(new.battery_pct, battery_pct)
   where id = new.node_id
  returning latitude, longitude into v_lat, v_lon;

  v_lat := coalesce(new.latitude, v_lat);
  v_lon := coalesce(new.longitude, v_lon);

  select warning_cm, danger_cm into v_warning, v_danger
    from thresholds where node_id = new.node_id;
  v_warning := coalesce(v_warning, 40);
  v_danger  := coalesce(v_danger, 50);

  if new.error_code is not null then
    perform open_alert(new.node_id, new.id, 'sensor_error', new.water_level_cm, v_lat, v_lon,
                       'Sensor error: ' || new.error_code);
  end if;

  if new.possible_fire then
    perform open_alert(new.node_id, new.id, 'possible_fire', new.water_level_cm, v_lat, v_lon,
                       'Possible fire detected');
  end if;

  if new.water_level_cm is not null then
    if new.water_level_cm >= v_danger then
      perform open_alert(new.node_id, new.id, 'danger', new.water_level_cm, v_lat, v_lon,
                         format('Water table %s cm below surface (danger at %s cm)',
                                new.water_level_cm, v_danger));
    elsif new.water_level_cm >= v_warning then
      perform open_alert(new.node_id, new.id, 'warning', new.water_level_cm, v_lat, v_lon,
                         format('Water table %s cm below surface (warning at %s cm)',
                                new.water_level_cm, v_warning));
    end if;
  end if;

  return null;
end;
$$;

drop trigger if exists readings_after_insert on public.readings;
create trigger readings_after_insert
  after insert on public.readings
  for each row execute function public.handle_new_reading();

-- These functions are internal: do not expose them through the Data API.
revoke all on function public.open_alert(text, bigint, text, numeric, double precision, double precision, text)
  from public, anon, authenticated;
revoke all on function public.handle_new_reading()       from public, anon, authenticated;
revoke all on function public.create_default_threshold() from public, anon, authenticated;


-- ---------------------------------------------------------------------
-- 3. View for the dashboard map
-- ---------------------------------------------------------------------

create or replace view public.node_latest
with (security_invoker = true) as
select
  n.id,
  n.name,
  n.latitude,
  n.longitude,
  n.wake_interval_min,
  n.battery_pct,
  n.last_seen_at,
  r.measured_at,
  r.water_level_cm,
  r.soil_moisture_pct,
  r.air_temp_c,
  r.air_humidity_pct,
  r.soil_temp_c,
  coalesce(t.warning_cm, 40) as warning_cm,
  coalesce(t.danger_cm, 50)  as danger_cm,
  case
    -- offline when nothing arrived for two wake-up intervals (UC-03 alternative flow)
    when n.last_seen_at is null
      or n.last_seen_at < now() - make_interval(mins => n.wake_interval_min * 2) then 'offline'
    when r.error_code is not null                          then 'sensor_error'
    when r.water_level_cm >= coalesce(t.danger_cm, 50)      then 'danger'
    when r.water_level_cm >= coalesce(t.warning_cm, 40)     then 'warning'
    else 'good'
  end as status
from public.nodes n
left join public.thresholds t on t.node_id = n.id
left join lateral (
  select * from public.readings rr
   where rr.node_id = n.id
   order by rr.measured_at desc, rr.id desc   -- id breaks ties when timestamps are equal
   limit 1
) r on true
where n.is_active;


-- ---------------------------------------------------------------------
-- 4. Access rules (Row Level Security)
--   anon / authenticated (dashboard in the browser): read everything
--   authenticated (logged-in operator): update alert status and thresholds
--   service_role (simulator / gateway on a server, NEVER in an app): full access
-- ---------------------------------------------------------------------

alter table public.nodes      enable row level security;
alter table public.thresholds enable row level security;
alter table public.readings   enable row level security;
alter table public.alerts     enable row level security;

grant usage on schema public to anon, authenticated, service_role;

grant select on public.nodes, public.thresholds, public.readings, public.alerts, public.node_latest
  to anon, authenticated;
grant update (warning_cm, danger_cm, updated_at)              on public.thresholds to authenticated;
grant update (status, action_note, handled_by, handled_at)     on public.alerts     to authenticated;

grant all on public.nodes, public.thresholds, public.readings, public.alerts to service_role;
grant select on public.node_latest to service_role;
grant usage, select on all sequences in schema public to service_role;

drop policy if exists "read nodes"      on public.nodes;
drop policy if exists "read thresholds" on public.thresholds;
drop policy if exists "read readings"   on public.readings;
drop policy if exists "read alerts"     on public.alerts;
drop policy if exists "operators update thresholds" on public.thresholds;
drop policy if exists "operators update alerts"     on public.alerts;

create policy "read nodes"      on public.nodes      for select to anon, authenticated using (true);
create policy "read thresholds" on public.thresholds for select to anon, authenticated using (true);
create policy "read readings"   on public.readings   for select to anon, authenticated using (true);
create policy "read alerts"     on public.alerts     for select to anon, authenticated using (true);

create policy "operators update thresholds" on public.thresholds
  for update to authenticated using (true) with check (true);
create policy "operators update alerts" on public.alerts
  for update to authenticated using (true) with check (true);


-- ---------------------------------------------------------------------
-- 5. Realtime: the dashboard gets new readings and alerts live
-- ---------------------------------------------------------------------

do $$
begin
  begin
    alter publication supabase_realtime add table public.readings;
  exception when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.alerts;
  exception when duplicate_object then null;
  end;
end $$;
