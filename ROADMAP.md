# Roadmap

## Open feature gaps vs. the web UI

### Equipment management
Grocy's web UI has an "Equipment" section (manage equipment items, attach manuals/files).
This feature is currently **not implemented in the Android app** — it's only reachable
through the web interface. Bringing it to Android would require:

- A new Fragment + ViewModel for the Equipment list/detail screens (following the existing
  MVVM pattern: Fragment → ViewModel → Repository → Room cache)
- API client methods in `GrocyApi.java` for the `equipment` and related file-upload endpoints
- A navigation entry point (e.g. in the drawer/bottom navigation) to reach the new screens

No work has started on this yet.
