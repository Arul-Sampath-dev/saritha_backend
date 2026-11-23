"""Made-up but consistent sample data for one branch, November 2026 (for the report samples only)."""
import random
from datetime import date, datetime, timedelta

random.seed(19)

BRANCH = {
    "name": "Saritha Fashions - Navalur",
    "address": "12, OMR Main Road, Navalur, Chennai 600130",
    "phone": "98410 12345",
    "gstin": "33ABCDE1234F1Z5",
    "owner": "Priya R",
}
FROM, TO = date(2026, 11, 1), date(2026, 11, 30)
PREV_FROM, PREV_TO = date(2026, 10, 2), date(2026, 10, 31)
GENERATED_AT = datetime(2026, 11, 30, 18, 42)
GENERATED_BY = "Platform owner"
TODAY = date(2026, 11, 30)

CATALOG = [("Blouse", 1400), ("Designer blouse", 2400), ("Churidar", 1600), ("Kurti", 950), ("Saree fall & pico", 250),
           ("Lehenga", 6500), ("Salwar", 1300), ("Alteration", 200), ("Kids frock", 850), ("Gown", 3800)]
FIRST = ["Anitha", "Kavya", "Meena", "Divya", "Lakshmi", "Priya", "Revathi", "Sangeetha", "Deepa", "Nithya", "Swathi",
         "Gayathri", "Harini", "Janani", "Keerthana", "Malathi", "Pavithra", "Ramya", "Sowmya", "Vidhya", "Yamuna", "Bhavani"]
LAST = ["S", "R", "K", "M", "P", "V", "N", "G", "T", "A"]
METHODS = ["Cash", "UPI", "Card"]
STAFF = [
    {"name": "Priya R", "role": "Shop owner"},
    {"name": "Ravi K", "role": "Tailor master"},
    {"name": "Meena S", "role": "Tailor"},
    {"name": "Lakshmi P", "role": "Tailor"},
    {"name": "Selvi M", "role": "Tailor"},
    {"name": "Divya N", "role": "Shop assistant"},
    {"name": "Kala V", "role": "Shop assistant"},
]
STAGES = ["Item accepted", "Cutting", "Tailor assigned", "Stitching", "Shop assistant assigned", "Hemming", "Ironing", "Item completed"]


def r2(x):
    return round(x + 1e-9, 2)


customers = []
for i in range(34):
    customers.append({"id": f"CUS{1000 + i}", "name": f"{random.choice(FIRST)} {random.choice(LAST)}",
                      "phone": f"9{random.randint(100000000, 999999999)}", "new": i < 13})

orders, payments, deliveries = [], [], []
pay_no = 1
rec_no = 1
for n in range(1, 49):
    d = FROM + timedelta(days=random.randint(0, 29))
    cust = random.choice(customers)
    items = []
    for _ in range(random.choice([1, 1, 2, 2, 3])):
        name, price = random.choice(CATALOG)
        qty = random.choice([1, 1, 1, 2])
        items.append({"name": name, "qty": qty, "price": price})
    gross = sum(i["qty"] * i["price"] for i in items)
    discount = random.choice([0, 0, 0, 0, 50, 100]) if gross > 1000 else 0
    taxable = gross - discount
    inter = n in (17, 33)  # two customers from another state
    cgst = sgst = 0.0 if inter else r2(taxable * 0.025)
    igst = r2(taxable * 0.05) if inter else 0.0
    total = r2(taxable + cgst + sgst + igst)
    due = d + timedelta(days=random.randint(5, 14))
    advance = r2(min(total, round(total * random.choice([0.3, 0.5, 0.5, 1.0]) / 10) * 10))
    method = random.choice(METHODS)
    received_by = random.choice(["Priya R", "Divya N"])
    paid = 0.0
    if advance > 0:
        payments.append({"no": f"SARI-PR-{d:%d-%m-%Y}-{pay_no:05d}", "at": datetime.combine(d, datetime.min.time()) + timedelta(hours=random.randint(10, 19), minutes=random.randint(0, 59)),
                         "invoice": None, "customer": cust["name"], "method": method, "txn": f"UPI-{random.randint(1000000, 9999999)}" if method == "UPI" else "",
                         "amount": advance, "by": received_by})
        pay_no += 1
        paid = advance
    delivered_at = None
    if due <= TODAY and random.random() < 0.72:
        delivered_at = datetime.combine(min(due + timedelta(days=random.randint(-1, 2)), TODAY), datetime.min.time()) + timedelta(hours=random.randint(11, 19), minutes=random.randint(0, 59))
        if delivered_at.date() < d:
            delivered_at = datetime.combine(d, datetime.min.time()) + timedelta(hours=18)
        bal = r2(total - paid)
        if bal > 0:
            m2 = random.choice(METHODS)
            payments.append({"no": f"SARI-PR-{delivered_at:%d-%m-%Y}-{pay_no:05d}", "at": delivered_at - timedelta(minutes=5), "invoice": None,
                             "customer": cust["name"], "method": m2, "txn": f"UPI-{random.randint(1000000, 9999999)}" if m2 == "UPI" else "",
                             "amount": bal, "by": random.choice(["Priya R", "Divya N"])})
            pay_no += 1
            paid = total
    inv_no = f"SARI-{d:%d-%m-%Y}-{n:05d}"
    for p in payments:
        if p["invoice"] is None:
            p["invoice"] = inv_no
    done = len(items) if delivered_at else random.randint(0, len(items))
    stage = "Delivered" if delivered_at else ("Ready" if done == len(items) else "In production")
    status = "Paid" if paid >= total else ("Partial" if paid > 0 else "Unpaid")
    o = {"no": inv_no, "date": d, "customer": cust["name"], "phone": cust["phone"], "items": items, "done": done,
         "gross": gross, "discount": discount, "taxable": taxable, "cgst": cgst, "sgst": sgst, "igst": igst, "gst": r2(cgst + sgst + igst),
         "total": total, "paid": r2(paid), "balance": r2(total - paid), "status": status, "stage": stage, "due": due,
         "delivered": delivered_at, "inter": inter}
    orders.append(o)
    if delivered_at:
        deliveries.append({"no": f"SARI-{delivered_at:%d-%m-%Y}-{rec_no:05d}", "at": delivered_at, "invoice": inv_no,
                           "customer": cust["name"], "total": total, "method": method})
        rec_no += 1

orders.sort(key=lambda o: (o["date"], o["no"]))
payments.sort(key=lambda p: p["at"])
deliveries.sort(key=lambda x: x["at"])

expenses = []
for d, cat, who, method, amt, note in [
    (1, "Rent", "Shop", "Bank transfer", 18000, "November rent"),
    (3, "Materials", "Shop", "Cash", 2450, "Lining cloth, threads"),
    (5, "Electricity Bill", "Shop", "UPI", 3120, "EB bill Oct–Nov"),
    (6, "Refreshment", "Shop", "Cash", 380, "Tea and snacks"),
    (8, "Advance", "Meena S", "Cash", 2000, "Salary advance"),
    (10, "Materials", "Shop", "UPI", 1875, "Hooks, zips, lace"),
    (12, "Travel", "Divya N", "Cash", 240, "Auto to customer"),
    (14, "Refreshment", "Shop", "Cash", 310, "Tea and snacks"),
    (16, "Materials", "Shop", "Cash", 960, "Buttons and piping"),
    (18, "Other", "Shop", "Cash", 650, "Machine oil and service"),
    (20, "Advance", "Selvi M", "Cash", 1500, "Salary advance"),
    (22, "Refreshment", "Shop", "Cash", 420, "Tea and snacks"),
    (24, "Travel", "Ravi K", "Cash", 180, "Market visit"),
    (26, "Materials", "Shop", "UPI", 2210, "Embroidery thread"),
    (28, "Salary", "Divya N", "Bank transfer", 12000, "November salary"),
    (28, "Salary", "Kala V", "Bank transfer", 11000, "November salary"),
    (29, "Refreshment", "Shop", "Cash", 290, "Tea and snacks"),
]:
    expenses.append({"date": date(2026, 11, d), "category": cat, "for": who, "method": method, "amount": float(amt), "notes": note})
deleted_expenses = [{"date": date(2026, 11, 9), "category": "Materials", "amount": 640.0, "deleted_at": datetime(2026, 11, 9, 17, 5),
                     "by": "Priya R", "reason": "Entered twice"}]
topups = [{"at": datetime(2026, 11, 2, 10, 15), "by": "Platform owner", "amount": 5000.0},
          {"at": datetime(2026, 11, 15, 11, 40), "by": "Platform owner", "amount": 5000.0},
          {"at": datetime(2026, 11, 25, 9, 55), "by": "Platform owner", "amount": 3000.0}]
petty_balance = 2340.0

deleted_invoices = [
    {"no": "SARI-07-11-2026-00049", "date": date(2026, 11, 7), "customer": "Harini T", "amount": 1470.0,
     "deleted_at": datetime(2026, 11, 7, 16, 20), "by": "Priya R", "reason": "Customer cancelled"},
    {"no": "SARI-21-11-2026-00050", "date": date(2026, 11, 21), "customer": "Deepa G", "amount": 2520.0,
     "deleted_at": datetime(2026, 11, 22, 10, 5), "by": "Priya R", "reason": "Wrong items, created again"},
]

# ---------------------------------------------------------------- derived
days = []
d = FROM
while d <= TO:
    o = [x for x in orders if x["date"] == d]
    c = sum(p["amount"] for p in payments if p["at"].date() == d)
    e = sum(x["amount"] for x in expenses if x["date"] == d)
    days.append({"date": d, "orders": len(o), "invoiced": r2(sum(x["total"] for x in o)), "collected": r2(c), "expenses": r2(e), "net": r2(c - e)})
    d += timedelta(days=1)

sales = r2(sum(o["total"] for o in orders))
taxable = r2(sum(o["taxable"] for o in orders))
discount = r2(sum(o["discount"] for o in orders))
cgst = r2(sum(o["cgst"] for o in orders)); sgst = r2(sum(o["sgst"] for o in orders)); igst = r2(sum(o["igst"] for o in orders))
gst = r2(cgst + sgst + igst)
collected = r2(sum(p["amount"] for p in payments))
exp_total = r2(sum(x["amount"] for x in expenses))
exp_shop = r2(sum(x["amount"] for x in expenses if x["for"] == "Shop"))
exp_emp = r2(exp_total - exp_shop)
open_orders = [o for o in orders if not o["delivered"]]
summary = {
    "orders": len(orders), "sales": sales, "taxable": taxable, "discount": discount, "gst": gst, "cgst": cgst, "sgst": sgst, "igst": igst,
    "collected": collected, "expenses": exp_total, "exp_shop": exp_shop, "exp_emp": exp_emp, "net": r2(collected - exp_total),
    "delivered": len(deliveries), "open_invoices": len(open_orders) + 9, "outstanding": r2(sum(o["balance"] for o in open_orders) + 6850),
    "petty_added": r2(sum(t["amount"] for t in topups)), "petty_balance": petty_balance, "deleted": len(deleted_invoices),
}
previous = {"sales": r2(sales * 0.88), "collected": r2(collected * 0.93), "expenses": r2(exp_total * 1.06), "orders": 43}
previous["net"] = r2(previous["collected"] - previous["expenses"])


def change(cur, prev):
    return None if not prev else (cur - prev) / abs(prev) * 100


by_method = []
for m in METHODS:
    ps = [p for p in payments if p["method"] == m]
    by_method.append({"name": m, "count": len(ps), "amount": r2(sum(p["amount"] for p in ps))})
by_category = {}
for x in expenses:
    c = by_category.setdefault(x["category"], {"name": x["category"], "count": 0, "amount": 0.0})
    c["count"] += 1; c["amount"] = r2(c["amount"] + x["amount"])
by_category = sorted(by_category.values(), key=lambda c: -c["amount"])

gst_rows = [
    {"type": "Intra-state (CGST 2.5% + SGST 2.5%)", "invoices": sum(1 for o in orders if not o["inter"]),
     "taxable": r2(sum(o["taxable"] for o in orders if not o["inter"])), "cgst": cgst, "sgst": sgst, "igst": 0.0},
    {"type": "Inter-state (IGST 5%)", "invoices": sum(1 for o in orders if o["inter"]),
     "taxable": r2(sum(o["taxable"] for o in orders if o["inter"])), "cgst": 0.0, "sgst": 0.0, "igst": igst},
]
for g in gst_rows:
    g["total"] = r2(g["taxable"] + g["cgst"] + g["sgst"] + g["igst"])

items_sold = {}
for o in orders:
    for i in o["items"]:
        it = items_sold.setdefault(i["name"], {"name": i["name"], "pieces": 0, "amount": 0.0})
        it["pieces"] += i["qty"]; it["amount"] = r2(it["amount"] + i["qty"] * i["price"])
items_sold = sorted(items_sold.values(), key=lambda i: -i["amount"])

stages_now = [("Item accepted", 6, 1, 1.2), ("Cutting", 4, 0, 0.8), ("Tailor assigned", 3, 0, 0.5), ("Stitching", 12, 3, 3.4),
              ("Shop assistant assigned", 0, 0, None), ("Hemming", 2, 0, 0.6), ("Ironing", 1, 0, 0.2), ("Item completed", 8, 2, 1.9)]
stage_times = [("Item accepted → cutting", 20), ("Cutting → tailor", 31), ("Tailor assigned → stitching", 9), ("Stitching", 86),
               ("Hemming", 14), ("Ironing", 5), ("Ready → delivered", 44)]
turnaround = {"delivered": len(deliveries), "avg_days": 9.4, "on_time": 81.0, "with_due": len(deliveries)}

staff_work = [
    {"name": "Priya R", "role": "Shop owner", "work": f"{len(orders)} invoices created · {len(deliveries)} deliveries", "last": datetime(2026, 11, 30, 18, 2)},
    {"name": "Ravi K", "role": "Tailor master", "work": "71 items cut · 68 assigned to tailors", "last": datetime(2026, 11, 30, 17, 40)},
    {"name": "Meena S", "role": "Tailor", "work": "29 assigned · 27 stitched · 26 completed", "last": datetime(2026, 11, 30, 16, 55)},
    {"name": "Lakshmi P", "role": "Tailor", "work": "24 assigned · 22 stitched · 21 completed", "last": datetime(2026, 11, 30, 17, 10)},
    {"name": "Selvi M", "role": "Tailor", "work": "18 assigned · 15 stitched · 14 completed", "last": datetime(2026, 11, 29, 18, 30)},
    {"name": "Divya N", "role": "Shop assistant", "work": "34 hemming · 33 ironing · 33 completed", "last": datetime(2026, 11, 30, 17, 58)},
    {"name": "Kala V", "role": "Shop assistant", "work": "27 hemming · 26 ironing · 25 completed", "last": datetime(2026, 11, 30, 16, 45)},
]

spent = {}
for o in orders:
    s = spent.setdefault(o["customer"], {"name": o["customer"], "phone": o["phone"], "invoices": 0, "spent": 0.0, "last": o["date"]})
    s["invoices"] += 1; s["spent"] = r2(s["spent"] + o["total"]); s["last"] = max(s["last"], o["date"])
top_customers = sorted(spent.values(), key=lambda c: -c["spent"])[:10]
active = len(spent)
customer_stats = {"active": active, "new": 13, "returning": active - 13, "repeat_rate": round((active - 13) / active * 100, 1),
                  "avg_spend": r2(sales / active)}
