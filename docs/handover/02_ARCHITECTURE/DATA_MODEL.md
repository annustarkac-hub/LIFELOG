# Data Model Proposal

This is a logical model; the implementation agent may refine it and document migrations.

## app_identity
- package_name (PK)
- display_name
- version_name nullable
- first_seen_at nullable
- last_seen_at nullable
- is_currently_installed
- icon_cache_key nullable

## usage_record
- id (PK)
- package_name nullable (null for device-level)
- metric_type (APP_FOREGROUND, DEVICE_SCREEN_ON, API_AGGREGATE, etc.)
- start_at_utc
- end_at_utc nullable
- duration_ms nullable
- source_type
- source_api
- source_query_start_utc
- source_query_end_utc
- collected_at_utc
- is_historical
- is_inferred
- dedupe_key
- created_at_utc

## usage_daily
- local_date
- timezone_id
- package_name nullable
- metric_type
- duration_ms
- launch_count nullable
- coverage_status
- computed_at_utc
- PK: local_date + timezone_id + package_name + metric_type

## system_event
- id (PK)
- event_type
- event_time_utc
- package_name nullable
- value_text nullable
- value_number nullable
- source_api
- source_event_type
- dedupe_key
- collected_at_utc

## battery_snapshot
- id (PK)
- captured_at_utc
- battery_level_percent nullable
- charging_state nullable
- plugged_type nullable
- source_api

## data_coverage
- id (PK)
- metric_type
- interval_start_utc
- interval_end_utc
- status (AVAILABLE, PARTIAL, UNAVAILABLE, NOT_GRANTED, UNKNOWN)
- source
- checked_at_utc
- notes nullable

## device_profile
- id (PK; local singleton)
- manufacturer
- model
- android_release
- api_level
- first_lifelog_tracking_at_utc
- user_purchase_date nullable
- purchase_date_source nullable (USER_ENTERED)
- last_successful_collection_at_utc

## collection_checkpoint
- id (PK)
- collector_name
- last_successful_query_end_utc
- last_successful_run_at_utc
- status
- error_code nullable

## backup_metadata
- id (PK)
- created_at_utc
- backup_format_version
- app_version
- checksum
- record_counts_json
- encryption_mode

## Required indexes
- usage_record(package_name, start_at_utc)
- usage_record(metric_type, start_at_utc)
- usage_record(dedupe_key) unique where non-null
- system_event(event_time_utc)
- system_event(event_type, event_time_utc)
- usage_daily(local_date, metric_type)
- data_coverage(metric_type, interval_start_utc)

## Data model notes
- Use appropriate Room types/converters.
- Define nullable semantics carefully.
- Keep historical/recorded provenance.
- Avoid storing redundant aggregates without a recomputation strategy.
- Version all backup schemas.
