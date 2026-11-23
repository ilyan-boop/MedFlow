# MedFlow (LBYCPDS E27, Group 7)

A Java-based clinic queuing and patient records system.
This version covers **Milestones 1 to 6 (Nov 5 to Nov 23)** of the Gantt chart.

## What is done

**Milestone 1: class structure, data models, role-based login**
- Data models: `User`, `Patient`, `Visit`, `Appointment`, `DuplicateFlag`
- Enums: `Role`, `Permission`, `Service`, `Sex`, `VisitType`, `VisitStatus`, `PriorityCategory`, `AppointmentStatus`
- Role-based login for the 5 staff roles (`AuthService`)
  - `HashMap<username, User>` for login
  - `HashMap<Role, Set<Permission>>` for permissions
  - The menu shows only the modules the role can use, and every service method checks the permission again
- Audit log as a **hand-coded singly linked list** (`AuditLog`), add in O(1)
- Data saved to and loaded from `.csv` files (`FileHandler`)

**Milestone 2: patient registration with validation, age rules, and duplicate check**
- Character-by-character input checks with no regex (`Validator`): names, birthdate, sex, address, contact number, menu choices
- Clear error messages, then the program asks again instead of crashing (`InvalidInputException`)
- Age rules: Pediatrics below 18, Internal Medicine 18 and above, Family Medicine all ages
- Unique patient IDs (P0001, P0002, ...)
- Duplicate check with `HashMap<name+birthdate key, list of IDs>` in O(1) average
  - "Dela Cruz", "DELA CRUZ", "De La Cruz", and "Dela-Cruz" all match
  - The receptionist can use the existing record, or create a new one that is **flagged** for records staff
- Records staff review flags side by side and either **merge** (the old record is kept and points to the new ID, nothing is deleted) or **keep both**
- Search by patient ID (HashMap, O(1)) or partial name (linear search, O(n))
- Clinical notes are hidden from roles without permission (Receptionist, Records Staff, Administrator)

**Milestone 3 (Nov 12): visit creation, queue numbers, and priority lane**
- Visit creation with age check, visit ID (V0001, ...), and queue number per service (FM-001, IM-001, PED-001)
- Queue numbers are checked in a `HashSet`, so two active visits never share a number
- Regular line and priority lane per service as **hand-coded FIFO queues** (`LinkedQueue`, enqueue and dequeue in O(1)).
  The priority lane is served first
- Call next, patient missing (back of the line, **No-show** on the second miss), and cancel

**Milestone 4 (Nov 14): calling patients, consultation tracking, visit status, and outcome recording**
- **Calling patients:** the call time is saved, and the queue screen also lists who is in consultation
- **Consultation tracking:** "Start consultation" saves the start time and adds the visit to the consultations in progress
  (`LinkedHashMap` per service, add and remove in O(1)). Waiting time is computed from arrival to start
- **Outcome recording (Clinician only, menu "Consultations: record the outcome"):**
  - Shows the patient, reason, allergies, history, and time so far
  - Outcome: Treated, Follow-up advised, or Referred, with a short note (3 to 200 characters, checked character by character)
  - Saves the end time, the note, and who recorded it. The visit becomes **COMPLETED** (Treated, Follow-up) or **REFERRED**,
    and the queue number is freed
- **Visit status rules:** every status change goes through one table (`EnumMap<VisitStatus, EnumSet<VisitStatus>>`).
  Completed, Referred, Cancelled, and No-show are final, so a visit can never jump back
- **Visit status board (menu "Visit status board and tracking"):** counts per service and status, average waiting and
  consultation minutes, and tracking by Visit ID or by Patient ID (full timeline). The outcome note is hidden from roles
  that cannot see clinical notes
- Permission `VIEW_VISIT_STATUS` (Receptionist, Nurse, Clinician, Administrator). Recording outcomes needs
  `RECORD_CONSULTATION` (Clinician only)
- `visits.csv` has 3 more columns (`calledTime`, `outcomeType`, `consultedBy`). Older files with 15 columns still load
- Audit log entries: `CALL_PATIENT`, `MISSED_CALL`, `NO_SHOW`, `START_CONSULT`, `RECORD_OUTCOME`

**Milestone 5 (Nov 16): version history, audit log, and appointments**
- Record updates: Records Staff correct personal details, Clinicians update allergies and history
- **Version history as a hand-coded stack** (`VersionStack`): before every change the old version is pushed (O(1)),
  so nothing is overwritten. Any change can be undone, and the undo is saved as a new version
- Name or birthdate changes move the record in the duplicate index and flag new possible duplicates
- Audit log filters by user, by record ID, and by action (one pass over the linked list, O(n))
- Appointments in a `TreeMap<date and time, appointments>`: book, reschedule, cancel, upcoming (`tailMap`),
  missed (`headMap`, 30-minute grace period), today's list (`subMap`), and check-in, which creates a visit and queue number
- Booking rules: no past times or Sundays, 08:00 to 16:30 every 30 minutes, age rule per service, up to 3 patients
  per service per slot, no double booking of the same patient
- Follow-ups: consultations that end with "Follow-up visit advised" appear in a to-book list, and the booked
  appointment is linked to that visit

**Milestone 6 (Nov 23): search and daily reports**
- **Hand-coded merge sort** (`SortUtil`): stable, O(n log n) in all cases. Every list in the program is now sorted with it,
  so `Collections.sort`, `Arrays.sort`, and `List.sort` are not used anywhere
- **Hand-coded binary search and linear search** (`SearchUtil`): binary search in O(log n), and every match of a repeated
  key is listed by checking the items next to the match
- **Search menu (`SearchService`)**: by patient ID or visit ID (HashMap, O(1)), by name (list sorted by name with merge sort,
  then binary search for names that start with the text, O(log n + m)), and by queue number (sorted list and binary search,
  since numbers like FM-001 are used again on other days). If no name starts with the text, linear search finds it
  anywhere in the name. The sorted lists are sorted again only when patients or visits change
- **Hand-coded min-heap** (`BinaryHeap`): insert and removeMin in O(log n), peek in O(1)
- **Hand-coded k-way merge** (`MergeUtil`): joins the sorted FM, IM, and PED lists with the min-heap in O(N log k)
- **Daily report (Administrator, menu "Daily report and export", `ReportService`)**: for a chosen date, each service list
  is sorted by arrival time with merge sort, the three lists are joined by k-way merge, then the report shows patient
  counts by service and status, patients seen (consultation volume), average waiting and consultation time, all visits
  in order of arrival, and the visits with one status
- **Export** (`FileHandler`): `reports/daily_report_YYYY-MM-DD.txt` or `.csv`, next to the `data` folder.
  Outcome notes are not included. Audit log entries: `VIEW_REPORT`, `EXPORT_REPORT`
- Sample visits for Tuesday, November 17 and Wednesday, November 18, 2026 are in `data/visits.csv`, so a report can be
  made right away. The expected numbers are checked by hand in `Milestone6Tests`
- **Find a patient by ID or by name** (`Main.askPatientId`): every screen that needs a patient (create a visit, book an
  appointment, appointments of a patient, correct patient details, update clinical notes, version history, view a record)
  lets the user type the patient ID or search by name with `SearchService` and pick from the results

## How to run

**IntelliJ IDEA / VS Code:** open the `MedFlow` folder, mark `src` (and `test`) as source folders, then run `medflow.Main`.
Run with the `MedFlow` folder as the working directory so it finds the `data` folder.

**Command line (from the MedFlow folder):**
```
javac -d out src/medflow/*.java src/medflow/*/*.java test/medflow/*.java
java -cp out medflow.Main
java -cp out medflow.MedFlowTests
java -cp out medflow.Milestone5Tests
java -cp out medflow.Milestone6Tests
java -cp out medflow.ComplexityRuns
```
Requires JDK 17 or newer (built and tested on JDK 21).

**Terminal inside IntelliJ (the screen clears after each step here):** press Alt + F12 (or Shift + F12, depending on the
keymap), then type `cd src/medflow` and `java Main.java`. The program finds the `data` folder of the project by itself,
so both ways of running use the same saved patients, visits, and appointments. This way of running needs JDK 22 or newer.

## Sample accounts (fictional)

| Username  | Password | Role          |
|-----------|----------|---------------|
| reception | rec123   | Receptionist  |
| nurse     | nur123   | Nurse         |
| doctor    | doc123   | Clinician     |
| records   | rec456   | Records Staff |
| admin     | adm123   | Administrator |

Sample patients P0001 to P0008, visits V0001 to V0015, and appointments A0001 to A0007 are fictional. To start over, delete the files in `data/`
(the program reloads the sample data when `users.csv` is missing).

## Folder structure
```
src/medflow/Main.java          console menu, login loop, registration screens
src/medflow/model/             data models and enums
src/medflow/service/           AuthService, PatientService, VisitService, RecordHistory, AppointmentService,
                               SearchService, ReportService
src/medflow/ds/                hand-coded data structures (AuditLog linked list, LinkedQueue, VersionStack, BinaryHeap)
src/medflow/util/              Validator, ConsoleInput, SortUtil (merge sort), SearchUtil (binary and linear search),
                               MergeUtil (k-way merge)
src/medflow/io/                FileHandler, Csv, SampleData
src/medflow/exception/         InvalidInputException, AccessDeniedException
test/medflow/MedFlowTests.java     199 test cases for Milestones 1 to 4
test/medflow/Milestone5Tests.java   81 test cases for Milestone 5 and the hand-coded queue
test/medflow/Milestone6Tests.java   87 test cases for Milestone 6 (sorting, searching, k-way merge, reports)
test/medflow/ComplexityRuns.java    time complexity test runs with growing sample data
data/                          users, patients, duplicate flags, visits, appointments, record versions, audit log (.csv)
evidence/                      screenshots, test results, time complexity results, sample runs, sample reports,
                               class diagram
reports/                       exported daily reports (made by the program)
```

## Next milestone
- Nov 26: final testing with sample data, time complexity analysis write-up, documentation, and final checking
