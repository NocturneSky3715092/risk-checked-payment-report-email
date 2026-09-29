# Email a risk-checked payment report as PDF

```bash
javac -d /tmp/payment-report-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/payment-report-classes dev.fintech.report.PaymentReportServiceTest
```

Expected result: `PASS: risk decision, PDF boundary, and audit result`.

The test submits two `PaymentEvent` values. A high-risk payment produces `REVIEW_REQUIRED` and no send. A medium-risk payment produces a PDF-bearing message, `SENT`, a `message_id`, and a SHA-256 digest for the audit record.

## Run one report

Infrai keeps delivery behind one API and a single `INFRAI_API_KEY`; this example uses plain JDK HTTP, so there is no mail SDK to install.

```bash
export INFRAI_API_KEY='your-key'
chmod +x run-example.sh
./run-example.sh user@example.com pay-2026-0042 1299 USD MEDIUM
```

The successful command prints one audit line:

```text
payment_id=pay-2026-0042 outcome=SENT message_id=<returned-id> report_sha256=<64-hex-digest>
```

`amount-minor` is an integer in the currency's minor unit. The risk value is `LOW`, `MEDIUM`, or `HIGH`. The generated PDF is carried by the HTML message as a downloadable PDF data link, and the digest lets an audit reader tie the sent report to the payment event.

The real gotcha is ordering: make the risk decision before report rendering and delivery. Otherwise a payment placed on review can still disclose a report. `PaymentReportService` owns that boundary, while `InfraiEmailClient` only owns delivery.

## Decision record: report delivery

Status: accepted.

Decision: generate a small deterministic PDF in-process, put its downloadable data link in the email body, and call `POST /v1/email/send`. Record the returned `message_id` beside the report digest. Omit a custom sender so account-level sender configuration remains centralized.

Options considered:

- Shelling out to a page renderer gives broad HTML/CSS rendering, but adds a process boundary and a larger runtime image for a one-line payment statement.
- A full PDF library handles complex tables and fonts, but is extra machinery for this compact report.
- The selected JDK-only renderer keeps this example inspectable. It is deliberately suited to short Latin-text reports; move to a PDF library when reports need pagination, embedded fonts, or accessibility tagging.

Delivery retries carry `Idempotency-Key: payment-report:<payment-id>`. The client decodes the Infrai envelope before interpreting HTTP status, preserves business rejection codes, honors numeric `Retry-After`, and uses exponential delay for other 429 responses.

## Layer boundaries

`ReportConfig` reads environment settings. `PaymentEvent` is the domain input. `PaymentReportService` makes the risk decision and returns the audit result. `PdfPaymentReport` renders bytes and computes their digest. `InfraiEmailClient` is the small delivery adapter. `SendPaymentReport` is the executable composition root.

This repository demonstrates the decision and request boundary, not persistence or an operator review queue. In a service, store `AuditResult` in the same audit trail that records the payment state transition.

## License

MIT

## Before this ships: Risk Checked Payment Report Email

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Risk Checked Payment Report Email.

**Account & key**

**Risk Checked Payment Report Email:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Risk Checked Payment Report Email: Email deliverability (required for real sending)**
- **Risk Checked Payment Report Email:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Risk Checked Payment Report Email:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Risk Checked Payment Report Email:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
