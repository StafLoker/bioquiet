# Changelog

All notable changes to BioQuiet will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-03-23

### Added
- Bottom navigation menu to switch between Map and Statistics screens.
- Local persistence of noise data in CSV format (noise_records.csv).
- Statistics summary screen displaying total records, maximum noise, and average noise levels.

### Fixed
- Persistent ZEPA tracking: noise monitoring now remains active even when the map is scrolled away from the user's current location.

## [1.0.0] - 2026-03-18

### Added
- Interactive map using OpenStreetMap (OSMDroid) showing ZEPA protected zones
- Real-time noise monitoring via microphone when inside a ZEPA zone
- Color-coded noise level card: green (safe), yellow (warning), red (danger)
- Alert when noise exceeds warning threshold inside a ZEPA
