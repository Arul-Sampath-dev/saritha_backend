# Production Workflow Review

## Intended item flow

`ORDER_ACCEPTED → CUTTING → TAILOR_ASSIGNED → STITCHING → SHOP_ASSISTANT_ASSIGNED → HEMMING → IRONING → ORDER_COMPLETED`

| Transition/action | Intended actor |
|---|---|
| `ORDER_ACCEPTED → CUTTING` | Tailor master |
| Assign tailor (`CUTTING → TAILOR_ASSIGNED`) | Tailor master |
| `TAILOR_ASSIGNED → STITCHING` | The assigned tailor |
| Assign shop assistant (`STITCHING → SHOP_ASSISTANT_ASSIGNED`) | The item's assigned tailor |
| `SHOP_ASSISTANT_ASSIGNED → HEMMING → IRONING → ORDER_COMPLETED` | The assigned shop assistant |

## Code findings

1. **Tailor master can start stitching.** `ItemWorkflow.STATUS_RULES` configures `TAILOR_ASSIGNED → STITCHING` with `OWNER_AND_MASTER` as supervisors, in addition to the assigned tailor as the worker. This differs from the intended rule that only the assigned tailor starts stitching.
2. **Shop owner can perform all status transitions.** The status rules include `SHOP_OWNER` as a supervisor for each transition. The intended workflow above does not include the shop owner as an actor for these status changes.
3. **Shop assistant assignment is restricted as intended.** `ASSIGN_SHOP_ASSISTANT` allows the shop owner or the item's assigned tailor; it does not allow the tailor master.
4. **Queues align with the handoffs.** The tailor-master queue includes `ORDER_ACCEPTED`, `CUTTING`, `TAILOR_ASSIGNED`, and `STITCHING`; the tailor queue includes assigned `TAILOR_ASSIGNED` and `STITCHING` items; the shop-assistant queue includes assigned `SHOP_ASSISTANT_ASSIGNED`, `HEMMING`, and `IRONING` items.

## Relevant implementation

- `src/main/java/com/saritha/fashions/modules/production/model/ItemWorkflow.java` defines status, assignment, and unassignment permissions.
- `src/main/java/com/saritha/fashions/modules/production/service/ProductionWriteServiceImpl.java` validates and applies status changes and assignments.
- `src/main/java/com/saritha/fashions/modules/production/service/ProductionReadServiceImpl.java` defines role queues and computes `availableActions` using the same workflow rules.

No code changes have been made as part of this review.

## Resolution (2026-11-14) — option 1 implemented

Status changes are now made **only by the person responsible for the step** (`ItemWorkflow.STATUS_RULES`):

| Status change | Allowed actor |
|---|---|
| `ORDER_ACCEPTED → CUTTING` | Tailor master |
| `TAILOR_ASSIGNED → STITCHING` | The assigned tailor (findings 1 and 2 fixed: tailor master and shop owner removed) |
| `SHOP_ASSISTANT_ASSIGNED → HEMMING → IRONING → ORDER_COMPLETED` | The assigned shop assistant (finding 2 fixed: shop owner removed) |

Assignment rules are kept as previously requested by the product owner:

| Action | Allowed actor |
|---|---|
| Assign / re-assign tailor | Tailor master, shop owner |
| Assign / re-assign shop assistant | The item's tailor, shop owner (not the tailor master) |
| Unassign tailor / shop assistant | Shop owner only |

Effects:
- The shop owner still sees every item in the queue, but `availableActions` only offers assignment actions; when a worker is absent the owner re-assigns the item.
- Worker steps have no supervisors, so every worker status update is guarded: it only applies while the item is still assigned to that worker.
- Activity entries (and "my activity" reports) are always credited to the person who did the work.

Verified with a permission-matrix check (37 checks: every role on every status step, assignments and unassignments). Docs updated: `MODULES_API_DOCUMENTATION.md` (section 4), `FRONTEND_BUILD_GUIDE.md`, `FRONTEND_MIGRATION_GUIDE.md`, and the Swagger description of `PATCH .../status`.
