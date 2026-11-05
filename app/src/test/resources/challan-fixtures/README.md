# Challan reference samples

Master copy of the order challan rules, shared by the dashboard (web) and the Android app.
Each `NN-*.json` is one sample order with the expected result:

- `rows` — grouped table rows (`processOrderItemsForPrint`)
- `swatches` — photos shown in the swatch grid, in order
- `pages` — which row numbers and how many swatches land on each page (`paginateChallan`)

Both test suites must pass against these files:

- Dashboard: `src/__tests__/challanFixtures.test.ts`
- Android: copies this folder into `app/src/test/resources/challan-fixtures/` with its sync script

When you change a rule: update the samples here, bump `VERSION`, make the dashboard
tests pass, then sync and fix Android.

Page sizes and heights are in PDF points (A4 = 595 x 842); see `src/components/orders/challanPagination.ts`.
