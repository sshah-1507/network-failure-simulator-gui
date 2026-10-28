# Network Failure Simulator & Rerouting — GUI Edition

A Java Swing desktop application that simulates network router and link failures, dynamic failover to preconfigured backup routes, chronological event scheduling, and undo operations using standard data structures.

---

## 1. Requirements

- **Java JDK 17+** (OpenJDK / Eclipse Adoptium Temurin or Oracle JDK)
- Windows PowerShell or Command Prompt

---

## 2. Quick Start: How to Run

### Method 1: Double-Click (Easiest on Windows)
Simply double-click **`run.bat`** in the project root directory.

### Method 2: Windows PowerShell
Open PowerShell in this directory and run:

```powershell
# 1. Compile source files
Remove-Item -Recurse -Force out -ErrorAction SilentlyContinue
mkdir out
javac -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })

# 2. Launch GUI
java -cp out com.networksim.ui.NetworkGUI
```

Or run the pre-packaged executable JAR:
```powershell
java -jar network-failure-simulator-gui.jar
```

---

## 3. How to Demonstrate the Key Features

### 1. Initial State & Route Monitoring
1. When launched, the **A–F demo network** renders automatically on the canvas.
2. In the top status banner, observe:
   - `Simulation Clock: t=0`
   - `Active Route (A->D): R1 [A -> B -> C -> D]`
3. Click **Check Active Route** or **Show All Routes** to inspect configured paths and priority order.

### 2. Failing and Restoring ANY Router or Link Dynamically
1. **Dynamic Link Failure:**
   - In section **2. Dynamic Link Management**, choose any link from the dropdown (e.g. `B-C`).
   - Click **Fail Link**.
   - Notice the link turns **red dashed** on the canvas, its status changes to `FAILED`, and the simulator immediately reroutes traffic to `R2 [A -> E -> F -> D]` (Priority 2).
   - Click **Restore** to bring the link back up; the primary route `R1` (Priority 1) will be restored as active.
2. **Dynamic Router Failure:**
   - In section **1. Dynamic Router Management**, choose any router (e.g. `C`).
   - Click **Fail Router**. Notice Router C turns red and traffic switches to `R2`.
   - Try clicking **Fail Router** again: a clean error message informs you that the router is already failed.
   - Click **Restore** to bring Router C back online.

### 3. Chronological Event Scheduler (TreeMap + Queue)
1. In section **4. Event Scheduler**:
   - The timestamp input is numeric (e.g. `10`). Non-negative integer values are enforced.
   - Select event type `Fail Link` and target `B-C`.
   - Click **Schedule Event**. A confirmation log appears in the console and the top banner updates `Pending Events: 1`. (Note: Scheduling stores the event in the queue; it does not execute immediately).
   - Enter timestamp `15`, choose `Fail Router` with target `E`, and click **Schedule Event**.
   - Enter timestamp `10`, choose `Restore Link` with target `B-C`, and click **Schedule Event** (demonstrating multiple events at the exact same timestamp).
2. Click **Show Pending Queue** to see events organized chronologically.
3. Click **Run Next** to advance the simulation clock and execute the next event in order.
4. Click **Run All** to execute all remaining pending events.

### 4. Undo and Reset
1. Click **Undo Last Operation** at any time to reverse your previous failure or restoration using the LIFO undo stack.
2. Click **Reset Demo** to restore the entire topology, clocks, and route states back to the original demo configuration.

### 5. Statistics & Event History
- Click **Data Structure Stats** to view live metrics on all underlying data structures (Adjacency list size, HashSets, TreeMap entries, Cache hits).
- Click **Event History** to view the timestamped audit log of all operations.

---

## 4. DSA Scope & Explanation

This project focuses strictly on **Data Structures**:
- **Adjacency List / Map** (`LinkedHashMap`): Router network connectivity.
- **HashMap**: $O(1)$ lookup for links, routes, and candidate priority queues.
- **HashSet**: Fast membership checks for failed routers and links.
- **PriorityQueue**: Candidate routes ordered by priority (1 = highest).
- **Reverse Dependency Indexes**: Instant mapping from a failed component to its dependent routes.
- **TreeMap + Queue**: Chronological event scheduling with FIFO execution for events at the same tick.
- **ArrayDeque**: LIFO stack for undo operations.
- **LinkedHashMap**: Bounded LRU cache for route queries.

> **Scope Note:** No Dijkstra, BFS, DFS, or shortest-path algorithms are implemented. The simulator selects among preconfigured routes based on priority.

Detailed explanation for college presentations and vivas is available in [`docs/DSA_EXPLANATION.md`](docs/DSA_EXPLANATION.md).  
Automated test specifications and results are in [`docs/TESTING.md`](docs/TESTING.md).

---

## 5. Automated Tests

To run the 45-point test suite:
```powershell
java -cp out com.networksim.test.SimulatorTestSuite
```
