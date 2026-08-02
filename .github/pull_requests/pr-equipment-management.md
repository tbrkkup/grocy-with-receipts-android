# Equipment management — Android implementation

Closes #[equipment-management issue]

## What was added

Full CRUD for the grocy `equipment` entity, including PDF instruction manual upload/delete, integrated into the existing master data screens.

### New files

| File | Purpose |
|---|---|
| `model/Equipment.java` | Room entity + `Parcelable` + `updateEquipment()` queue item |
| `dao/EquipmentDao.java` | Room DAO (get / insert / delete) |
| `fragment/MasterEquipmentFragment.java` | Create/edit form (name, description, manual) |
| `res/layout/fragment_master_equipment.xml` | Form layout |

### Changed files

| File | Change |
|---|---|
| `database/AppDatabase.java` | Added `Equipment` entity, bumped DB version 54 → 55, added `equipmentDao()` |
| `api/GrocyApi.java` | Added `ENTITY.EQUIPMENT`, `getEquipmentManual(filename)` for the `/files/equipmentmanuals/` endpoint |
| `helper/DownloadHelper.java` | Added `Equipment.class` dispatch in `updateData()` |
| `util/PrefsUtil.java` | Added `DB_LAST_TIME_EQUIPMENT` to cache-clearing |
| `Constants.java` | Added `PREF.DB_LAST_TIME_EQUIPMENT` |
| `util/ObjectUtil.java` | Added `Equipment` cases in all 4 object-property switch blocks |
| `model/InfoFullscreen.java` | Added `INFO_EMPTY_EQUIPMENT = 38` |
| `view/InfoFullscreenView.java` | Renders the empty-equipment illustration |
| `repository/MasterObjectListRepository.java` | Added equipment to 8-way `Single.zip()` |
| `viewmodel/MasterObjectListViewModel.java` | Added equipment case in `loadFromDatabase` + `downloadData` |
| `fragment/MasterObjectListFragment.java` | Added equipment title, empty-state, FAB navigation, row click |
| `repository/MasterDataOverviewRepository.java` | Added equipment to 7-way `Single.zip()` |
| `viewmodel/MasterDataOverviewViewModel.java` | Added `equipmentLive`, download, getter |
| `fragment/MasterDataOverviewFragment.java` | Added click handler and count observer |
| `res/layout/fragment_master_data_overview.xml` | Added equipment list item |
| `res/navigation/navigation_main.xml` | Added `masterEquipmentFragment` and action |
| `res/values/strings.xml` | Added equipment, instruction manual, empty-state strings |

## Manual upload flow

The PDF picker uses `ActivityResultContracts.GetContent`. Bytes are read on a background `ExecutorService`. On save, the file is PUT to `/api/files/equipmentmanuals/<base64-filename>` before the equipment row is persisted. Existing manuals are deleted best-effort via a DELETE request when the user removes them or replaces them.

## Database migration

`fallbackToDestructiveMigration()` is already configured for this project, so only the version bump is required.
