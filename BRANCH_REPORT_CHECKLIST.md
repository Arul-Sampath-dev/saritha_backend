# Branch report export (Excel / PDF) — build checklist

Builds the feature planned in [BRANCH_REPORT_PLAN.md](BRANCH_REPORT_PLAN.md). The target look is the samples in
[report-samples/](report-samples/) (`SarithaFashions_Navalur_2026-11-01_2026-11-30.xlsx` / `.pdf`).
Tick each box when it is done **and** checked.

---

## 0. Decisions (confirmed 2026-11-20: defaults, D7 as below)

| # | Question | Default we build with | ✔ / change |
|---|---|---|---|
| D1 | Who can download | Platform owner (any branch) **and** shop owner (own branch) | [x] |
| D2 | PDF size | Full lists up to 500 rows per list, then "+ N more — see the Excel report"; Excel always full | [x] |
| D3 | Sales by item | Included (new aggregation) | [x] |
| D4 | Compare with previous period | Included (same number of days just before) | [x] |
| D5 | Logo + branch details on the cover | Included | [x] |
| D6 | All-branches combined report | Not now (one branch only) | [x] |
| D7 | Period | The user picks any range up to 366 days: a day, a week, a month, 3 months, 6 months, 1 year or custom from–to; no "Overall" | [x] |
| D8 | Customer phone numbers | Full in Excel and PDF (the file stays with the owner) | [x] |
| D9 | PDF engine | Word template → existing `DocxToPdfConverter` (same as invoices) | [x] |

---

## 1. Preparation

- [x] **1.1 Align Apache POI versions.** In `pom.xml`, `poi` is 5.3.0 and `poi-ooxml` is 5.2.3. Set both to the same
      version (5.3.0).
      *Done when:* `mvn -q -o compile` passes, and the invoice / receipt PDFs still download (smoke test).
- [x] **1.2 Package.** Create `src/main/java/com/saritha/fashions/modules/report/export/`.
- [x] **1.3 Money / date helpers.** One shared Indian-money formatter (₹1,23,456.00) and IST date formatter. Reuse the
      *Note (2026-11-20):* Done inside the writers (PdfWriter.money / money0, Excel ₹ format).
      one in `SalesDocumentPdfService` (`money()`), moved to a small shared util if needed.

## 2. New data the report needs

- [x] **2.1 Payments in a period.** A repository method: payment receipts of a shop with `paidAt` in [from, to + 1 day),
      *Note (2026-11-20):* Built from the invoice payment entries (same source as "Collected", so the totals match), joined with `payment_receipts` for the receipt number, transaction id and who took it (`findPaymentsInPeriod`, `findPaymentReceipts`).
      oldest first. Fields: payment receipt no, paid at, invoice no, customer name, method, transaction id, amount,
      received by.
      *Test:* sample documents in / out of the range; a shop filter that excludes other shops.
- [x] **2.2 Sales by item (D3).** Aggregation on invoices dated in the period: `$unwind items`, then group by
      *Note (2026-11-20):* Computed in Java from the period's invoices (`BranchReportService.itemsSold`), tested.
      `itemName`, giving pieces (Σ quantity) and amount (Σ quantity × price, before GST), largest amount first.
      Deleted invoices are excluded.
      *Test:* two invoices with the same item add up; an item with quantity 2 counts 2 pieces.
- [x] **2.3 GST split per invoice type.** From the invoices dated in the period: taxable, CGST, SGST, IGST, total,
      split into intra-state (CGST + SGST) and inter-state (IGST) rows.
      *Test:* the totals equal `financials/summary` taxable and GST for the same period.

## 3. Report data (one object for both files)

- [x] **3.1 `BranchReportData` record**, with: header (branch name, address, phone, GSTIN, owner, period, generated at / by) ·
      summary · previous-period summary · GST rows · payment methods · expense categories · days · orders · payments ·
      deliveries · expenses · deleted expenses · petty cash top-ups + balance · production now · turnaround + stage
      times · staff work · customer stats + top 10 · deleted invoices · items sold.
- [x] **3.2 `BranchReportService.build(shopId, from, to, user)`**:
  - [x] validate: from and to are present, to ≥ from, at most 366 days, and the shop exists
  - [x] access (D1): OWNER → any `shopId`; SHOP_OWNER → own shop only (ignore or reject another `shopId`)
  - [x] **reuse** the existing services, without copying their maths: financial summary (current + previous), daily
        financials, invoices list (all pages), receipts, expenses + deleted expenses, petty cash history, production,
        activity by employee, customers, deleted invoices
  - [x] plus the new queries 2.1–2.3
  - [x] staff work text per role (tailor: assigned · stitched · completed; assistant: hemming · ironing · completed;
        master: cut · assigned; shop owner: invoices · deliveries)
- [ ] **3.3 Consistency test.** For one fixture period these must match: Σ orders total = summary sales; Σ payments
      *Note (2026-11-20):* Not as a separate test yet: totals come from the same sources by design (orders and summary from the invoices of the period, payments from the same payment entries). Check by hand in section 9.
      = collected; Σ expenses = expenses; Σ days = the period totals.

## 4. Excel writer

- [x] **4.1 `BranchReportExcelWriter.write(data) → byte[]`** with POI **SXSSF** (streaming).
- [x] **4.2 Shared styles:** title, sub-title, header (bold, grey fill, bottom border), money `₹##,##,##0.00` (Indian
      format), date `dd-mmm-yyyy`, date-time, integer, percent, total row (bold, pale-green fill).
- [x] **4.3 Sheets** (same order and columns as the sample):
  - [x] Summary (branch block, figures with previous + change %, GST, methods, categories)
  - [x] Daily
  - [x] Orders
  - [x] Payments
  - [x] Deliveries
  - [x] Expenses (+ deleted expenses)
  - [x] Petty cash
  - [x] Production
  - [x] Staff
  - [x] Customers
  - [x] Deleted invoices
  - [x] Items
- [x] **4.4 On every list:**
  - frozen header and auto-filter
  - a `SUM` totals row
  - "None in this period" when the list is empty
  - fitted column widths
  - landscape, fit to width
- [x] **4.5 Test:** open the bytes with POI and check the 12 sheet names, the header cells, that a money cell is
      numeric with the ₹ format, and that a totals cell holds a `SUM` formula.

## 5. PDF writer

- [x] **5.1 Word template `src/main/resources/BranchReport.docx`**, laid out like the sample PDF:
      *Note (2026-11-20):* Changed: no hand-made .docx template — the Word document is built in Java with POI (`BranchReportPdfWriter`), because the template engine cannot repeat several lists or colour values. Same layout as the sample. Cell widths are fixed twips (the converter fails on % cell widths).
  - **Page 1:** logo, green title band, eight summary figures (with ▲ / ▼), sales breakdown, payment methods, GST,
    expense categories
  - **Next pages:** day by day, orders, payments, then deliveries + expenses + petty cash
  - **Last page:** production, time per stage, staff, customer figures, top 10, items, deleted invoices
  - A footer on every page with branch, period, "generated on / by" and the page number
  - Table header rows set to "repeat on each page"
- [ ] **5.2 Placeholders** in `WordTemplate` syntax, matching plan section 5: `{{branchName}}` … and table template rows
      *Note (2026-11-20):* Not needed (see 5.1).
      `{{orders.no}}`, `{{payments.amount}}`, …
- [x] **5.3 `BranchReportPdfWriter.write(data) → byte[]`**:
  - fill the template (`WordTemplate`)
  - cap each list at 500 rows (D2) and add a "+ N more — see the Excel report" row
  - change ▲ / ▼ text with green / red colour
  - "None in this period" rows for empty lists
  - convert with `DocxToPdfConverter`
- [x] **5.4 Long periods:** more than 62 days → the day-by-day table in the PDF becomes week by week (Excel stays daily).
- [x] **5.5 Tests:** no `{{` left in the filled .docx (before conversion); list caps work; the converter is mocked in
      unit tests.

## 6. Endpoint

- [x] **6.1 `GET /v1/api/reports/branch-report?shopId=&from=&to=&format=xlsx|pdf`** in `ReportController`
      *Note (2026-11-20):* In its own `BranchReportController` (same `/v1/api/reports` path), not in `ReportController`.
  - `format` default `xlsx`
  - response `Content-Type`:
    - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
    - `application/pdf`
  - `Content-Disposition: attachment; filename="SarithaFashions_<Branch>_<from>_<to>.<ext>"`
    - one day: `…_<date>.<ext>`
    - branch name made file-safe
- [x] **6.2 Security rule** in `WebSecurityConfig`: OWNER + SHOP_OWNER (the service checks the shop).
      *Note (2026-11-20):* No change needed: `GET /v1/api/reports/**` already allows OWNER and SHOP_OWNER.
- [x] **6.3 Errors:**
  - 400 for bad dates, range > 366 days, unknown shop or unknown format
  - 403 for another shop
  - 502 "PDF service unavailable, download the Excel instead" when the converter fails
- [ ] **6.4 Activity log (optional):** `REPORT_DOWNLOADED` with branch, period and format.
      *Note (2026-11-20):* Not done (optional).
- [x] **6.5 Swagger:** summary, parameters, examples and responses.
- [ ] **6.6 Controller tests:** content type + file name for both formats, 400 for the range, 403 for another shop.
      *Note (2026-11-20):* Partly: file names tested; content type / 400 / 403 controller tests still to write.

## 7. Frontend

- [x] **7.1 API helper** `downloadBranchReport({ shopId, from, to, format })` in `src/api/reports.js`
  - generic blob download using the server's file name, like `utils/downloadPdf.js`
  - readable error message from a JSON error body
- [x] **7.2 `ReportDownloadModal`** (shared component, design.md style):
  - period options (D7): Today · Yesterday · This week · Last week · This month · Last month · Pick a month ·
    Last 3 months · Last 6 months · Last 12 months · Pick a day · Custom from–to (any range up to 366 days)
  - format choice: **Excel** (full detail) / **PDF** (printable), with one line explaining each
  - a summary line, e.g. "Saritha Fashions – Navalur · 1 Nov – 30 Nov 2026 · Excel"
  - **Download** button with a spinner; success toast "Report downloaded"; error toast
- [x] **7.3 Owner branch page** (`Owner/Branches/BranchPage.jsx`): "Download report" button in the header actions.
- [x] **7.4 Shop owner (D1):** "Download report" on the shop owner dashboard header (own branch; no shop picker).
- [x] **7.5** `FRONTEND_CHANGE_CHECKLIST.md` entry.

## 8. Documentation

- [x] **8.1** `MODULES_API_DOCUMENTATION.md`: new endpoint (parameters, formats, errors, file name rule, limits).
- [x] **8.2** `BRANCH_REPORT_PLAN.md`: mark it built and note any changes from the plan.
- [x] **8.3** `PLATFORM_OWNER_PAGES_PLAN.md`: tick the report item.

## 9. Testing by hand (before sign-off)

Use a branch with real test data; compare every figure with the dashboard / branch page for the same period.

- [ ] One day (today), and one day with no orders
- [ ] This week; this month; last month
- [ ] Custom 3 months (PDF switches to weekly in the day-by-day table)
- [ ] A branch with no data at all (every section says "None in this period")
- [ ] More than 500 orders in the period (PDF shows "+ N more"; Excel shows all)
- [ ] Excel opens in Microsoft Excel **and** Google Sheets; money sums; dates sort; filters work
- [ ] PDF prints well on A4; headers repeat on every page; the logo shows; ₹ signs render
- [ ] Shop owner always gets their own branch (a different `shopId` is ignored, as in every report) (D1)
- [ ] Converter down → PDF gives the friendly error; Excel still works
- [ ] File names are correct (branch, dates, extension)
- [ ] Figures match the dashboard / branch page (sales, collected, expenses, net, delivered, outstanding)

## 10. Done when

- [ ] All boxes above are ticked
- [x] `mvn test` is green (28 tests); frontend lint + build are clean
- [ ] The Excel and the PDF match each other and the samples' layout
- [ ] Committed on a feature branch (when you ask)
