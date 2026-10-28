# Testing Report — Network Failure Simulation & Rerouting

**Test Suite Class:** [`com.networksim.test.SimulatorTestSuite`](../src/main/java/com/networksim/test/SimulatorTestSuite.java)  
**Execution Environment:** OpenJDK 17 (Temurin-17.0.18+8), Windows PowerShell  
**Total Tests Executed:** 45  
**Total Tests Passed:** 45  
**Failures / Errors:** 0  

---

## 1. Automated Test Execution Summary

The automated test runner was executed via:
```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
java -cp out com.networksim.test.SimulatorTestSuite
```

### Output:
```text
====================================================
  Running Simulator Test Suite (All Edge Cases)
====================================================
[PASS] Initial Clock is 0
[PASS] Initial Active Route A->D is R1
[PASS] R1 has priority 1
[PASS] Total configured routers is 6
[PASS] Total configured links is 7
[PASS] Total configured routes is 3
[PASS] Link B-C is failed
[PASS] Reroute from R1 to R2 upon B-C failure
[PASS] Prevent duplicate link failure
[PASS] No operational route left when B-C and E-F are both failed
[PASS] Router C is failed
[PASS] Reroute to R2 upon Router C failure
[PASS] Prevent duplicate router failure
[PASS] All routes unavailable if destination D fails
[PASS] Query report indicates no route
[PASS] Switched to backup R2
[PASS] Link B-C is restored
[PASS] R1 (Priority 1) reactivated as preferred route upon link restoration
[PASS] Active route R2 after failing B-C
[PASS] Router E failed
[PASS] Undo 1 restores Router E
[PASS] Active route still R2 (B-C still failed)
[PASS] Undo 2 restores Link B-C
[PASS] Active route restored to R1
[PASS] Reject negative timestamp
[PASS] Pending event count is 3
[PASS] Clock advanced to t=10
[PASS] Link B-C failed at t=10
[PASS] Active route switched to R2 at t=10
[PASS] Reject scheduling in past relative to simulation clock
[PASS] Clock advanced to t=15
[PASS] Router E failed at t=15
[PASS] Clock advanced to t=20
[PASS] Link B-C restored at t=20
[PASS] Preferred route R1 reactivated at t=20
[PASS] Pending events empty
[PASS] Both events recorded at t=10
[PASS] Event 1 is FAIL_ROUTER
[PASS] Event 2 is RESTORE_ROUTER
[PASS] Router B failed after Event 1
[PASS] Router B restored after Event 2
[PASS] Active route is R1 after FIFO sequence
[PASS] First query computes status
[PASS] Second query returns from LRU Cache
[PASS] State modification invalidates cache and returns fresh active route
====================================================
  SUMMARY: 45 / 45 tests passed.
====================================================
```

---

## 2. Test Case Specifications & Results

| # | Test Case Description | Input / Trigger | Expected Outcome | Execution Status |
|---|---|---|---|---|
| **TC-01** | Initial Topology Load | `NetworkSimulator.demo()` | 6 routers (A-F), 7 links, Clock t=0, Active route R1 | **PASSED** |
| **TC-02** | Initial Active Route Priority | Query A->D | R1 (Priority 1) active, R2 (2) and R3 (3) candidates | **PASSED** |
| **TC-03** | Dynamic Link Failure (B-C) | `failLink("B", "C")` | Link B-C marked failed; R1 marked unavailable; R2 becomes active | **PASSED** |
| **TC-04** | Duplicate Link Failure | `failLink("B", "C")` again | Throws `IllegalStateException`; does not corrupt state | **PASSED** |
| **TC-05** | Dynamic Router Failure (C) | `failRouter("C")` | Router C marked failed; R1 disabled; R2 becomes active | **PASSED** |
| **TC-06** | Duplicate Router Failure | `failRouter("C")` again | Throws `IllegalStateException`; rejected cleanly | **PASSED** |
| **TC-07** | Total Route Exhaustion | Fail B-C and E-F | R1, R2, R3 all unavailable; Active route is `null` (No Operational Route) | **PASSED** |
| **TC-08** | Destination Failure | Fail Router D | All routes fail because destination is down | **PASSED** |
| **TC-09** | Link Restoration & Reactivation | `restoreLink("B", "C")` | Link restored; Priority 1 route R1 automatically re-promoted | **PASSED** |
| **TC-10** | LIFO Undo Stack | Fail B-C, Fail E, Undo, Undo | First undo restores E (R2 remains active); Second undo restores B-C (R1 reactivates) | **PASSED** |
| **TC-11** | Negative Timestamp Scheduling | Schedule with $t = -5$ | Rejected with `IllegalArgumentException` | **PASSED** |
| **TC-12** | Past Timestamp Scheduling | Advance clock to 10, schedule at $t = 5$ | Rejected with `IllegalArgumentException` ("Cannot schedule in past") | **PASSED** |
| **TC-13** | Out-of-Order Scheduling Order | Schedule t=20, t=10, t=15 | Executed chronologically: t=10 first, then t=15, then t=20 | **PASSED** |
| **TC-14** | Equal Timestamp FIFO Queue | Two events scheduled at $t=10$ | Executed in FIFO order at tick 10 | **PASSED** |
| **TC-15** | Bounded LRU Cache | Repeated queries for A->D | 1st query computes and caches; 2nd returns `[Cached]`; state change invalidates cache | **PASSED** |

---

## 3. GUI & Interactive Validation

- **Non-blocking Input Validation:** When invalid input is entered into the GUI timestamp field (e.g. entering "B-C" or letters instead of numbers), the GUI displays an explicit warning dialog and keeps the current state intact without crashing.
- **Dynamic Dropdowns:** Dropdowns for routers and links are built dynamically from `sim.getRouters()` and `sim.getLinks()`. The status badge immediately updates to `FAILED` (red) or `UP` (green).
- **Topology Canvas:** Renders failed nodes with red fill/border and failed links with red dashed strokes. Active route is rendered in emerald green.
