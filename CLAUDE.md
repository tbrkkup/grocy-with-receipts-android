# Grocy with Receipts — Android Development Progress

## Branch
Active development branch: `receipts`

## Completed Tasks

### Task 1 — Receipts List (MasterReceiptsFragment)
- Lists all receipts from `/objects/receipts`
- Supports swipe-to-delete
- Navigation to edit/create form

### Task 2 — Receipt Edit/Create Form (MasterReceiptFragment)
- Fields: store, date, total amount, notes
- Creates new receipt via POST `/objects/receipts`
- Updates existing receipt via PUT `/objects/receipts/<id>`
- Validation and error handling

### Task 3 — Receipt File Attachments
Commit: `d583444` on branch `receipts`

- File picker via `ActivityResultContracts.GetContent`
- Uploads file bytes to `/api/files/receipts/<filename>` (PUT)
- Links metadata row in `receipt_files` entity (POST `/objects/receipt_files`)
- Lists attached files in edit mode only (files require a receipt ID)
- Delete: removes metadata row first (source of truth), then best-effort deletes stored file
- UI: `linear_files_section` in `fragment_master_receipt.xml`, rows in `row_receipt_file.xml`

## Pending Tasks

### Task 4 — Link-Receipt Bulk Action
Allow selecting one or more stock log entries / purchase transactions and linking them to an existing receipt. Entry point could be a bulk action in the stock log or a picker in the receipt form that shows unlinked transactions.

### General
- CI verification of commit `d583444` was pending when development was paused
- Verify that the `receipts` branch is up-to-date with any upstream changes before resuming
