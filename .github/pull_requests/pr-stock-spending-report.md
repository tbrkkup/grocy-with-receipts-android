# Stock spending report — Android implementation

Closes #[stock-spending-report issue]

## What was added

A new "Spending report" screen reachable from the Stock overview toolbar that shows purchase spending broken down by product, product group, or store for a selected date range.

### New files

| File | Purpose |
|---|---|
| `model/ProductsPriceHistory.java` | Gson model for `products_price_history` rows + `getPriceHistory()` queue item |
| `viewmodel/StockReportSpendingsViewModel.java` | Aggregation logic and LiveData |
| `fragment/StockReportSpendingsFragment.java` | Screen with chip selectors and list |
| `adapter/SpendingItemAdapter.java` | RecyclerView adapter for spending rows |
| `res/layout/fragment_stock_report_spendings.xml` | Screen layout |
| `res/layout/row_spending_item.xml` | Single spending row |

### Changed files

| File | Change |
|---|---|
| `api/GrocyApi.java` | Added `ENTITY.PRODUCTS_PRICE_HISTORY`, `getProductsPriceHistory(startDate, endDate)` |
| `fragment/StockOverviewFragment.java` | Added menu item handler for the new action |
| `res/menu/menu_stock.xml` | Added "Spending report" menu item (`ic_round_cash_multiple`) |
| `res/navigation/navigation_main.xml` | Added `stockReportSpendingsFragment` + action from stock overview |
| `res/values/strings.xml` | Added date-range chip labels, screen title, "Group by" label |

## Data flow

1. On load, products, product groups, and stores are read from Room in a single `Single.zip()` call.
2. Price history is then fetched from `/api/objects/products_price_history` with URL-encoded `query[]` filters for `purchased_date` range and `transaction_type != self-production`.
3. Each row's spend is `amount × price`. Rows with `price = null` (e.g. inventory corrections) contribute zero.
4. Spend is aggregated into a `Map<String, Double>` keyed by the resolved name (product name / group name / store name), then sorted descending and rendered into the list.
5. A total across all entries is shown above the list.

## UX

Three date presets are shown as filter chips — "This month" (selected by default), "Last month", "This year" — with three group-by chips below. Changing any chip triggers an immediate re-aggregate and re-render without a network request (the price history response already covers the full range). The swipe-to-refresh gesture re-fetches from the network.

Offline behaviour follows the existing app pattern: the offline indicator is shown while the device has no connectivity; the last successful dataset is not cached to Room (price history is always fetched fresh), so the list stays empty when offline.
