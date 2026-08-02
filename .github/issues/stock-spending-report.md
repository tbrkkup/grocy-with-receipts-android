# Feature: Stock spending report

## Summary

The grocy web frontend has a "Stock reports → Spendings" page that visualises how much money was spent on purchases over a selectable date range, broken down by product, product group, or store. The Android app has a stock journal and stock overview but no spending report.

## Grocy API surface used

The web backend runs aggregated SQL against `products_price_history`. The same data is accessible on Android via the generic objects API:

| Operation | Endpoint |
|---|---|
| Fetch raw price history rows | `GET /api/objects/products_price_history` (with `query[]` filters) |
| Products (for name join) | already cached in Room |
| Product groups | already cached in Room |
| Stores | already cached in Room |

Filtering by date and excluding `self-production` transactions is done client-side after fetching.

## Data model (`products_price_history` rows, relevant fields)

```
product_id              INTEGER
amount                  REAL
price                   REAL
transaction_type        TEXT   (exclude "self-production")
purchased_date          DATE
shopping_location_id    INTEGER
```

## Proposed Android implementation

### Screen

**`StockReportSpendingsFragment`**

- Date range picker at the top (default: current month); presets "This month", "Last month", "This year", custom
- Group-by selector: Product / Product group / Store
- Horizontal bar chart (MPAndroidChart, already a dependency) showing spend per group item
- Sortable list below the chart, each row: name + formatted currency amount
- Total spend displayed as a summary chip above the chart
- Optional product-group filter when grouped by product

### Data flow

1. On date/group change, fetch `products_price_history` rows for the range (or all rows cached in Room, then filter in-memory)
2. Join with cached products / product groups / stores
3. Aggregate `amount × price` per group key
4. Sort descending by total spend
5. Render chart and list

### Files to add or change

- `model/ProductsPriceHistory.java` — Gson mapping (no Room persistence needed)
- `fragment/StockReportSpendingsFragment.java`
- `viewmodel/StockReportSpendingsViewModel.java`
- `api/GrocyApi.java` — add `getProductsPriceHistory(startDate, endDate)` helper
- Layouts: `fragment_stock_report_spendings.xml`, `row_spending_item.xml`
- Navigation graph entry + drawer menu entry (under Stock)
- Strings

## Acceptance criteria

- Spendings are shown for the current month by default
- Switching date range or group-by rerenders chart and list without full reload
- Currency is formatted according to the server's configured currency symbol
- Empty state shown when no purchase data exists for the selected range
- Offline: uses last-cached price history if available, shows offline indicator
