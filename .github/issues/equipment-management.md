# Feature: Equipment management

## Summary

The grocy web frontend ships an Equipment section that lets users track household or kitchen equipment (appliances, tools, etc.) together with their instruction manuals (PDF). The Android app has no equivalent screen.

## Grocy API surface used

| Operation | Endpoint |
|---|---|
| List all equipment | `GET /api/objects/equipment` |
| Create equipment | `POST /api/objects/equipment` |
| Update equipment | `PUT /api/objects/equipment/{id}` |
| Delete equipment | `DELETE /api/objects/equipment/{id}` |
| Upload instruction manual | `PUT /api/files/equipmentmanuals/{base64filename}` |
| Download / view manual | `GET /api/files/equipmentmanuals/{base64filename}` |
| Delete manual | `DELETE /api/files/equipmentmanuals/{base64filename}` |

## Data model (`equipment` entity)

```
id                          INTEGER  PK AUTOINCREMENT
name                        TEXT     NOT NULL UNIQUE
description                 TEXT
instruction_manual_file_name TEXT
row_created_timestamp       DATETIME
```

## Proposed Android implementation

### Screens

1. **Equipment list** (`MasterEquipmentListFragment`)
   - RecyclerView listing all equipment items, sorted by name
   - Search/filter bar
   - Swipe-to-delete with undo snackbar
   - FAB → navigate to create form

2. **Equipment edit form** (`MasterEquipmentFragment`)
   - Fields: Name (required), Description (optional HTML, reuse existing HTML editor)
   - Instruction manual section: shows current manual filename if set; button to pick a PDF from the device; button to delete the current manual
   - Save: upload manual first (if changed), then PUT/POST equipment object

### Navigation

- Entry point: drawer menu, below Chores
- `masterEquipmentListFragment` → `masterEquipmentFragment` (create / edit)

### Files to add or change

- `model/Equipment.java` — Room entity + Gson mapping
- `fragment/MasterEquipmentListFragment.java`
- `fragment/MasterEquipmentFragment.java`
- `adapter/EquipmentItemAdapter.java`
- `viewmodel/MasterEquipmentViewModel.java`
- `repository/MasterEquipmentRepository.java`
- `database/AppDatabase.java` — add `equipment_table`
- `api/GrocyApi.java` — add `getEquipmentFile()` helper
- Layouts: `fragment_master_equipment_list.xml`, `fragment_master_equipment.xml`, `row_equipment_item.xml`
- Navigation graph entries + deep link
- Strings

## Acceptance criteria

- User can view, create, edit and delete equipment items
- User can attach, view and delete an instruction manual PDF
- All operations reflect immediately (optimistic UI with undo on delete)
- Offline state is handled the same way as other master-data screens
