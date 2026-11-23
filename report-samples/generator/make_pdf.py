"""Sample branch report as printable HTML (layout of BRANCH_REPORT_PLAN.md section 5); printed to PDF with Edge."""
import base64
import html
import sys
from datetime import datetime
import data as D

OUT_HTML = sys.argv[1]
LOGO = r"c:\Users\ARUL S\Downloads\SarithaFashion_Backend\SarithaFashion_Backend\SarithaFashion_Frontend\src\assets\logo.png"


def inr(x, paise=True):
    neg = x < 0
    x = abs(x)
    whole = int(round(x * 100)) // 100
    frac = int(round(x * 100)) % 100
    s = str(whole)
    if len(s) > 3:
        head, tail = s[:-3], s[-3:]
        parts = []
        while len(head) > 2:
            parts.insert(0, head[-2:])
            head = head[:-2]
        if head:
            parts.insert(0, head)
        s = ",".join(parts) + "," + tail
    return ("-" if neg else "") + "₹" + s + (f".{frac:02d}" if paise else "")


def dt(v):
    if v is None:
        return "—"
    if isinstance(v, datetime):
        return v.strftime("%d %b %Y, %I:%M %p").lstrip("0").replace(" 0", " ").replace("AM", "am").replace("PM", "pm")
    return v.strftime("%d %b %Y").lstrip("0")


def e(s):
    return html.escape(str(s))


def chg(cur, prev, good="up"):
    c = D.change(cur, prev)
    if c is None:
        return ""
    up = c >= 0
    ok = up if good == "up" else not up
    return f'<span class="chg {"ok" if ok else "bad"}">{"▲" if up else "▼"} {abs(c):.1f}%</span>'


def table(headers, rows, nums=(), total=None, cls=""):
    th = "".join(f'<th class="{"n" if i in nums else ""}">{e(h)}</th>' for i, h in enumerate(headers))
    body = "".join("<tr>" + "".join(f'<td class="{"n" if i in nums else ""}">{c}</td>' for i, c in enumerate(r)) + "</tr>" for r in rows)
    if not rows:
        body = f'<tr><td colspan="{len(headers)}" class="none">None in this period</td></tr>'
    foot = ""
    if total:
        foot = "<tfoot><tr>" + "".join(f'<td class="{"n" if i in nums else ""}">{c}</td>' for i, c in enumerate(total)) + "</tr></tfoot>"
    return f'<table class="{cls}"><thead><tr>{th}</tr></thead><tbody>{body}</tbody>{foot}</table>'


S, P = D.summary, D.previous
period = f"{D.FROM:%d %b %Y} – {D.TO:%d %b %Y}".lstrip("0")
logo = base64.b64encode(open(LOGO, "rb").read()).decode()

kpis = [
    ("Sales (invoiced)", inr(S["sales"], False), f'{S["orders"]} orders', chg(S["sales"], P["sales"])),
    ("Collected", inr(S["collected"], False), f'{len(D.payments)} payments', chg(S["collected"], P["collected"])),
    ("Expenses", inr(S["expenses"], False), f'{len(D.expenses)} entries', chg(S["expenses"], P["expenses"], "down")),
    ("Net cash flow", inr(S["net"], False), "collected − expenses", chg(S["net"], P["net"])),
    ("Orders delivered", str(S["delivered"]), "receipts generated", ""),
    ("Outstanding now", inr(S["outstanding"], False), f'{S["open_invoices"]} open invoices', ""),
    ("Petty cash", inr(S["petty_balance"], False), f'balance · {inr(S["petty_added"], False)} added', ""),
    ("Deleted invoices", str(S["deleted"]), "in the period", ""),
]
kpi_html = "".join(f'<div class="kpi"><span>{e(a)}</span><b>{b}</b><small>{e(c)} {d}</small></div>' for a, b, c, d in kpis)

sales_detail = table(["", "Amount"], [["Gross (items)", inr(S["taxable"] + S["discount"])], ["Discount", "− " + inr(S["discount"])],
                                      ["Taxable value", inr(S["taxable"])], ["GST", inr(S["gst"])], ["<b>Sales incl. GST</b>", f'<b>{inr(S["sales"])}</b>']],
                     nums=(1,), cls="kv")
gst = table(["Type", "Invoices", "Taxable", "CGST", "SGST", "IGST", "Total"],
            [[e(g["type"]), g["invoices"], inr(g["taxable"]), inr(g["cgst"]), inr(g["sgst"]), inr(g["igst"]), inr(g["total"])] for g in D.gst_rows],
            nums=(1, 2, 3, 4, 5, 6),
            total=["Total", S["orders"], inr(S["taxable"]), inr(S["cgst"]), inr(S["sgst"]), inr(S["igst"]), inr(S["sales"])])
methods = table(["Method", "Payments", "Amount"], [[m["name"], m["count"], inr(m["amount"])] for m in D.by_method], nums=(1, 2),
                total=["Total", len(D.payments), inr(S["collected"])])
cats = table(["Category", "Entries", "Amount"], [[e(c["name"]), c["count"], inr(c["amount"])] for c in D.by_category], nums=(1, 2),
             total=["Total", len(D.expenses), inr(S["expenses"])])
daily = table(["Date", "Orders", "Invoiced", "Collected", "Expenses", "Net"],
              [[dt(x["date"]), x["orders"] or '<span class="mute">0</span>', inr(x["invoiced"]), inr(x["collected"]), inr(x["expenses"]),
                f'<span class="{"neg" if x["net"] < 0 else ""}">{inr(x["net"])}</span>'] for x in D.days],
              nums=(1, 2, 3, 4, 5), total=["Total", S["orders"], inr(S["sales"]), inr(S["collected"]), inr(S["expenses"]), inr(S["net"])], cls="compact")
orders = table(["Invoice", "Date", "Customer", "Items", "Taxable", "GST", "Total", "Paid", "Balance", "Status"],
               [[f'<span class="mono">{o["no"]}</span>', dt(o["date"]), e(o["customer"]), f'{o["done"]}/{len(o["items"])}', inr(o["taxable"]), inr(o["gst"]),
                 inr(o["total"]), inr(o["paid"]), f'<span class="{"due" if o["balance"] > 0 else "mute"}">{inr(o["balance"])}</span>',
                 f'<span class="pill {o["status"].lower()}">{o["status"]}</span> <span class="mute">{o["stage"]}</span>'] for o in D.orders],
               nums=(3, 4, 5, 6, 7, 8),
               total=["Total", "", f'{len(D.orders)} orders', "", inr(S["taxable"]), inr(S["gst"]), inr(S["sales"]),
                      inr(sum(o["paid"] for o in D.orders)), inr(sum(o["balance"] for o in D.orders)), ""], cls="compact")
payments = table(["Date & time", "Payment receipt", "Invoice", "Customer", "Method", "Received by", "Amount"],
                 [[dt(p["at"]), f'<span class="mono">{p["no"]}</span>', f'<span class="mono">{p["invoice"]}</span>', e(p["customer"]), p["method"],
                   e(p["by"]), inr(p["amount"])] for p in D.payments],
                 nums=(6,), total=["Total", "", "", f'{len(D.payments)} payments', "", "", inr(S["collected"])], cls="compact")
deliveries = table(["Receipt", "Delivered on", "Invoice", "Customer", "Total"],
                   [[f'<span class="mono">{x["no"]}</span>', dt(x["at"]), f'<span class="mono">{x["invoice"]}</span>', e(x["customer"]), inr(x["total"])] for x in D.deliveries],
                   nums=(4,), total=["Total", "", "", f'{len(D.deliveries)} orders', inr(sum(x["total"] for x in D.deliveries))], cls="compact")
expenses = table(["Date", "Category", "For", "Method", "Notes", "Amount"],
                 [[dt(x["date"]), e(x["category"]), e(x["for"]), e(x["method"]), e(x["notes"]), inr(x["amount"])] for x in D.expenses],
                 nums=(5,), total=["Total", "", "", "", f'{len(D.expenses)} entries', inr(S["expenses"])], cls="compact")
deleted_exp = table(["Date", "Category", "Amount", "Deleted on", "By", "Reason"],
                    [[dt(x["date"]), e(x["category"]), inr(x["amount"]), dt(x["deleted_at"]), e(x["by"]), e(x["reason"])] for x in D.deleted_expenses], nums=(2,))
topups = table(["Added on", "Added by", "Amount"], [[dt(t["at"]), e(t["by"]), inr(t["amount"])] for t in D.topups], nums=(2,),
               total=["Added in the period", "", inr(S["petty_added"])])
stages = table(["Stage (right now)", "Items", "Late", "Avg days"],
               [[s, n or '<span class="mute">0</span>', f'<span class="due">{late}</span>' if late else '<span class="mute">0</span>',
                 "—" if d is None else f"{d:.1f} d"] for s, n, late, d in D.stages_now], nums=(1, 2, 3),
               total=["Total", sum(x[1] for x in D.stages_now), sum(x[2] for x in D.stages_now), ""])
mx = max(h for _, h in D.stage_times)
times = "".join(f'<div class="bar"><span>{e(s)}{" <em>Slowest</em>" if h == mx else ""}</span><i><u style="width:{h / mx * 100:.0f}%"></u></i>'
                f'<b>{h / 24:.1f} days</b></div>' if h >= 24 else
                f'<div class="bar"><span>{e(s)}</span><i><u style="width:{h / mx * 100:.0f}%"></u></i><b>{h} h</b></div>' for s, h in D.stage_times)
staff = table(["Name", "Role", "Work in the period", "Last activity"], [[e(s["name"]), e(s["role"]), e(s["work"]), dt(s["last"])] for s in D.staff_work])
C = D.customer_stats
top = table(["#", "Customer", "Invoices", "Spent", "Last visit"],
            [[i + 1, e(c["name"]), c["invoices"], inr(c["spent"]), dt(c["last"])] for i, c in enumerate(D.top_customers)], nums=(2, 3))
deleted = table(["Invoice", "Customer", "Amount", "Deleted on", "By", "Reason"],
                [[f'<span class="mono">{x["no"]}</span>', e(x["customer"]), inr(x["amount"]), dt(x["deleted_at"]), e(x["by"]), e(x["reason"])] for x in D.deleted_invoices], nums=(2,))
items = table(["Item", "Pieces", "Amount (before GST)"], [[e(i["name"]), i["pieces"], inr(i["amount"])] for i in D.items_sold], nums=(1, 2),
              total=["Total", sum(i["pieces"] for i in D.items_sold), inr(sum(i["amount"] for i in D.items_sold))])

css = """
@page { size: A4; margin: 14mm 12mm 16mm; }
* { box-sizing: border-box; }
body { margin: 0; font-family: 'Inter', 'Segoe UI', Arial, sans-serif; color: #212121; font-size: 10.5px; line-height: 1.45; }
h1, h2, h3, .kpi b { font-family: 'Space Grotesk', 'Segoe UI', Arial, sans-serif; }
.page { page-break-after: always; }
.page:last-child { page-break-after: auto; }
.top { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 1px solid #e5e7eb; padding-bottom: 12px; margin-bottom: 14px; }
.top img { height: 40px; }
.top .meta { text-align: right; color: #75758a; font-size: 10px; }
.eyebrow { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 9px; letter-spacing: .1em; text-transform: uppercase; color: #93939f; }
.band { background: #003c33; color: #fff; border-radius: 14px; padding: 18px 22px; margin-bottom: 16px; }
.band h1 { margin: 2px 0 6px; font-size: 24px; font-weight: 500; letter-spacing: -.3px; }
.band .eyebrow { color: rgba(255,255,255,.65); }
.band .row { display: flex; flex-wrap: wrap; gap: 4px 22px; font-size: 10.5px; color: rgba(255,255,255,.85); }
.band .row b { color: #fff; font-weight: 600; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid #e5e7eb; border-radius: 12px; margin-bottom: 16px; }
.kpi { padding: 12px 14px; border-left: 1px solid #f2f2f2; border-top: 1px solid #f2f2f2; display: flex; flex-direction: column; gap: 2px; }
.kpi:nth-child(4n+1) { border-left: 0; } .kpi:nth-child(-n+4) { border-top: 0; }
.kpi span { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 8.5px; letter-spacing: .08em; text-transform: uppercase; color: #93939f; }
.kpi b { font-size: 18px; font-weight: 600; color: #17171c; }
.kpi small { color: #75758a; font-size: 9.5px; }
.chg { font-weight: 600; margin-left: 4px; } .chg.ok { color: #1a7f4b; } .chg.bad { color: #b30000; }
h2 { font-size: 14px; font-weight: 600; margin: 18px 0 8px; color: #17171c; }
h2:first-child { margin-top: 0; }
h2 small { font-family: 'Inter', 'Segoe UI', Arial, sans-serif; font-weight: 400; color: #75758a; font-size: 10px; margin-left: 6px; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
table { width: 100%; border-collapse: collapse; }
thead { display: table-header-group; }
tr { page-break-inside: avoid; }
th { text-align: left; font-weight: 600; color: #616161; background: #f7f6f3; padding: 6px 8px; font-size: 9.5px; border-bottom: 1px solid #e5e7eb; }
td { padding: 5px 8px; border-bottom: 1px solid #f2f2f2; vertical-align: top; }
.n { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
tfoot td { font-weight: 700; background: #edfce9; color: #003c33; border-bottom: 0; }
table.compact td { padding: 3.5px 7px; font-size: 9.5px; } table.compact th { font-size: 9px; }
table.kv th { display: none; }
.mono { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 9px; }
.mute { color: #93939f; } .due { color: #b30000; font-weight: 600; } .neg { color: #b30000; }
.none { text-align: center; color: #93939f; font-style: italic; padding: 10px; }
.pill { display: inline-block; padding: 0 6px; border-radius: 99px; font-size: 8.5px; font-weight: 600; }
.pill.paid { background: #edfce9; color: #1a7f4b; } .pill.partial { background: #fff6e5; color: #9a5b00; } .pill.unpaid { background: #fdeeee; color: #b30000; }
.note { color: #75758a; font-size: 9.5px; margin: 6px 0 0; }
.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-bottom: 10px; }
.stats div { background: #f7f6f3; border-radius: 10px; padding: 8px 10px; }
.stats span { display: block; font-size: 8.5px; color: #93939f; text-transform: uppercase; letter-spacing: .06em; font-family: 'JetBrains Mono', Consolas, monospace; }
.stats b { font-size: 14px; font-family: 'Space Grotesk', 'Segoe UI', Arial, sans-serif; }
.bar { display: grid; grid-template-columns: 170px 1fr 64px; gap: 10px; align-items: center; margin: 5px 0; }
.bar i { height: 6px; background: #f4f4f6; border-radius: 3px; overflow: hidden; display: block; }
.bar u { display: block; height: 100%; background: #003c33; border-radius: 3px; }
.bar b { text-align: right; } .bar em { font-style: normal; font-size: 8.5px; font-weight: 600; color: #c94f33; background: #fff0eb; padding: 0 6px; border-radius: 99px; margin-left: 4px; }
.foot { margin-top: 14px; padding-top: 8px; border-top: 1px solid #e5e7eb; color: #93939f; font-size: 9px; display: flex; justify-content: space-between; }
"""

head = (f'<div class="top"><img src="data:image/png;base64,{logo}" alt=""><div class="meta">{e(D.BRANCH["name"])}<br>{period}</div></div>')
foot = (f'<div class="foot"><span>Saritha&rsquo;s Designer Studio · Branch report · {e(D.BRANCH["name"])} · {period}</span>'
        f'<span>Generated {dt(D.GENERATED_AT)} by {e(D.GENERATED_BY)}</span></div>')

pages = [
    f'''<section class="page">{head}
<div class="band"><div class="eyebrow">Branch report · sample</div><h1>{e(D.BRANCH["name"])}</h1>
<div class="row"><span>Period <b>{period}</b></span><span>Shop owner <b>{e(D.BRANCH["owner"])}</b></span><span>GSTIN <b>{e(D.BRANCH["gstin"])}</b></span></div>
<div class="row" style="margin-top:4px"><span>{e(D.BRANCH["address"])} · {e(D.BRANCH["phone"])}</span></div></div>
<h2>Summary <small>▲ / ▼ compared with the 30 days before</small></h2>
<div class="kpis">{kpi_html}</div>
<div class="grid2"><div><h2>Sales</h2>{sales_detail}</div><div><h2>Collected by payment method</h2>{methods}</div></div>
<h2>GST summary <small>business summary — not a GST return</small></h2>{gst}
<h2>Expenses by category</h2>{cats}
{foot}</section>''',
    f'''<section class="page">{head}<h2>Day by day</h2>{daily}{foot}</section>''',
    f'''<section class="page">{head}<h2>Orders <small>{len(D.orders)} invoices dated in the period</small></h2>{orders}{foot}</section>''',
    f'''<section class="page">{head}<h2>Payments received <small>{len(D.payments)} payments</small></h2>{payments}{foot}</section>''',
    f'''<section class="page">{head}<h2>Deliveries <small>{len(D.deliveries)} orders handed over</small></h2>{deliveries}
<h2>Expenses</h2>{expenses}<h2>Deleted expenses</h2>{deleted_exp}<h2>Petty cash top-ups <small>balance now {inr(S["petty_balance"])}</small></h2>{topups}{foot}</section>''',
    f'''<section class="page">{head}
<div class="grid2"><div><h2>Production right now</h2>{stages}</div>
<div><h2>Where the time goes <small>delivered orders</small></h2>{times}
<p class="note">{D.turnaround["delivered"]} orders delivered · average {D.turnaround["avg_days"]} days from order to delivery · {D.turnaround["on_time"]:.0f}% on time</p></div></div>
<h2>Staff work</h2>{staff}
<h2>Customers</h2>
<div class="stats"><div><span>Active</span><b>{C["active"]}</b></div><div><span>New</span><b>{C["new"]}</b></div><div><span>Returning</span><b>{C["returning"]}</b></div>
<div><span>Repeat rate</span><b>{C["repeat_rate"]}%</b></div><div><span>Average spend</span><b>{inr(C["avg_spend"], False)}</b></div></div>
<div class="grid2"><div><h2>Top 10 customers</h2>{top}</div><div><h2>Sales by item</h2>{items}</div></div>
<h2>Deleted invoices</h2>{deleted}
{foot}</section>''',
]

doc = f'''<!doctype html><html><head><meta charset="utf-8"><title>Branch report</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=JetBrains+Mono&family=Space+Grotesk:wght@500;600&display=swap" rel="stylesheet">
<style>{css}</style></head><body>{"".join(pages)}</body></html>'''
open(OUT_HTML, "w", encoding="utf-8").write(doc)
print("html ok")
