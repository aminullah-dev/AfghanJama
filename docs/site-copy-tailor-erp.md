# Tailor ERP — page copy for linumic.com/what-we-do/tailor-erp/

> Draft copy to paste into the CMS. Every factual claim below is checked
> against the codebase (41 tables, 42 migrations, 38 screens, Android +
> Windows). Nothing here describes a feature that does not ship.
>
> **Not included: a web app.** The product today is an Android app and a
> Windows desktop app. If a web version is planned, say the word and it
> goes in as "coming", not as "available".

---

## Hero

**Tailor ERP**

Workshop management for tailoring businesses — built in Dari, right-to-left,
and built to work when the internet doesn't.

Every order, every cut, every tailor's assignment, every afghani in and out
of the drawer — in one book that balances itself.

`[ Download the Android demo ]`   `[ Get the Windows version ]`

---

## The problem it solves

Most tailoring workshops run on a paper notebook and memory. That works
until it doesn't:

- A customer's measurements are on a page nobody can find.
- A tailor finished six pieces last week — or was it seven?
- Fabric ran out mid-order, and nobody knew it was low.
- The cash drawer and the notebook disagree, and there's no way to tell
  which one is wrong.

Tailor ERP replaces the notebook without asking anyone to become an
accountant.

---

## What it does

**Orders, from measurement to delivery**
Take the order with the customer's measurements, cut the fabric, hand the
pieces to a tailor, send them to review, deliver. Each stage is a step the
app tracks — so at any moment you know exactly how many orders are on which
bench.

**Tailors and piece work**
Assign work, record what came back finished, and calculate pay per piece.
Partial deliveries are handled properly: a number moves forward once, never
twice.

**Material warehouse**
Fabric, buttons, thread, lining. Receive stock, issue it, run a count, set a
minimum level and get told before you run out. Every movement is recorded
with its reason, so a discrepancy has a history you can read.

**Books that balance**
Behind the simple screens is real double-entry accounting. Material consumed
by an order moves into work-in-progress, not into expenses. Opening balances
land in equity, not revenue. You never see a journal entry unless you want
to — but it's there, and it balances.

**Money and debts**
Cash in, cash out, customer credit, supplier accounts. Receivables are aged,
so a debt that has been sitting for a month tells you so instead of waiting
to be noticed.

**Printing**
Order slips, receipts, and reports laid out for the paper you actually use.

---

## Works offline

The workshop is the server. The app keeps its own database on the device —
no account, no subscription check, no internet required to open it or use
it. When several people need the same book, one machine shares it over the
local network.

Backups are a file you can copy to a USB stick, and restoring one is one
screen.

---

## Two ways to run it

**Android** — for the phone or tablet at the cutting table. Take
measurements, photograph the fabric, hand out work, mark deliveries.

**Windows desktop** — for the office machine. Same data, same book, a bigger
screen for reports and printing.

Both are built from one shared core, so a rule fixed in one is fixed in
both.

---

## Try it before you decide

The demo is the real app — not a slideshow, not a video.

Install it, open **Settings → Create Sample Workshop**, and it builds a
complete working shop for you in a few seconds: customers with measurements
and phone numbers, tailors and inspectors, stocked material with one item
deliberately below its minimum level, and six orders each sitting at a
different stage — one waiting to be cut, one out with a tailor, one in
review, one delivered.

Nothing is faked. The sample data is created through the same code paths
your real orders would use, so what you're clicking is what you'd get.

`[ Download the Android demo (APK) ]`

Installing an APK outside the Play Store: Android will ask you to allow
installation from this source once. That's expected — it's the same prompt
any direct download gets.

---

## Licensing

Tailor ERP is licensed per workshop, not per user or per device. One licence
covers the shop's phones and its office computer.

The demo runs without a licence so you can evaluate it properly. Licensing
applies when you start keeping real books.

`[ Request a licence ]`   `[ Talk to us about your workshop ]`

---

## Built for Dari, not translated into it

The interface is Persian/Dari throughout and laid out right-to-left from the
ground up — dates, numbers, forms, and printed paper. It was written for an
Afghan workshop, with the owner of that workshop telling us what was wrong
with it, week after week.

---

## Suggested meta description

> Workshop management for tailoring businesses: orders from measurement to
> delivery, piece-work pay, material stock, and books that balance. Dari,
> right-to-left, works offline. Android and Windows. Free demo.

---

## Notes for whoever pastes this in

1. Two download buttons need real URLs — the APK and the Windows installer.
   Both are built and tested in CI; they need to be published to the site.
2. The Windows build is not code-signed yet, so Windows SmartScreen /
   Smart App Control will warn on first run. Worth a one-line note next to
   the download so it doesn't look broken.
3. If you want this in Dari for an Afghan-facing version of the page, say
   so and I'll write it — not translated, written.
