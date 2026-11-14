# MedFlow (LBYCPDS E27, Group 7)

A Java-based clinic queuing and patient records system.
This version covers **Milestone 1 (Nov 5)** and **Milestone 2 (Nov 9)** of the Gantt chart.

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

**Milestone 3: visit creation, queue numbers, and priority lane**
- Visit creation with age check, visit ID (V0001, ...), and queue number per service (FM-001, IM-001, PED-001)
- Queue numbers are checked in a `HashSet`, so two active visits never share a number
- Priority lane and regular line per service (`ArrayDeque`), the priority lane is served first
- Call next, patient missing (back of the line, **No-show** on the second miss), and cancel

**Milestone 4: calling patients, consultation tracking, visit status, and outcome recording** (new)
- **Calling patients:** the call time is now saved, and the queue screen also lists who is in consultation
- **Consultation tracking:** "Start consultation" saves the start time and adds the visit to the consultations in progress (`LinkedHashMap` per service, add and remove in O(1)). Waiting time is computed from arrival to start
- **Outcome recording (Clinician only, new menu "Consultations: record the outcome"):**
  - Shows the patient, reason, allergies, history, and time so far
  - Outcome: Treated, Follow-up advised, or Referred, with a short note (3 to 200 characters, checked character by character)
  - Saves the end time, the note, and who recorded it. The visit becomes **COMPLETED** (Treated, Follow-up) or **REFERRED**, and the queue number is freed
- **Visit status rules:** every status change goes through one table (`EnumMap<VisitStatus, EnumSet<VisitStatus>>`). Completed, Referred, Cancelled, and No-show are final, so a visit can never jump back
- **Visit status board (new menu "Visit status board and tracking"):** counts per service and status, average waiting and consultation minutes, and tracking by Visit ID or by Patient ID (full timeline). The outcome note is hidden from roles that cannot see clinical notes
- New permission `VIEW_VISIT_STATUS` (Receptionist, Nurse, Clinician, Administrator). Recording outcomes needs `RECORD_CONSULTATION` (Clinician only)
- `visits.csv` has 3 new columns (`calledTime`, `outcomeType`, `consultedBy`). Older files with 15 columns still load
- Audit log entries: `CALL_PATIENT`, `MISSED_CALL`, `NO_SHOW`, `START_CONSULT`, `RECORD_OUTCOME`

## How to run

**IntelliJ IDEA / VS Code:** open the `MedFlow` folder, mark `src` (and `test`) as source folders, then run `medflow.Main`.
Run with the `MedFlow` folder as the working directory so it finds the `data` folder.

**Command line (from the MedFlow folder):**
```
javac -d out src/medflow/*.java src/medflow/*/*.java test/medflow/*.java
java -cp out medflow.Main
java -cp out medflow.MedFlowTests
```
Requires JDK 17 or newer (built and tested on JDK 21).

## Sample accounts (fictional)

| Username  | Password | Role          |
|-----------|----------|---------------|
| reception | rec123   | Receptionist  |
| nurse     | nur123   | Nurse         |
| doctor    | doc123   | Clinician     |
| records   | rec456   | Records Staff |
| admin     | adm123   | Administrator |

Sample patients P0001 to P0008 are fictional. To start over, delete the files in `data/`
(the program reloads the sample data when `users.csv` is missing).

## Folder structure
```
src/medflow/Main.java          console menu, login loop, registration screens
src/medflow/model/             data models and enums
src/medflow/service/           AuthService, PatientService, VisitService
src/medflow/ds/                hand-coded data structures (AuditLog linked list)
src/medflow/util/              Validator, ConsoleInput
src/medflow/io/                FileHandler, Csv, SampleData
src/medflow/exception/         InvalidInputException, AccessDeniedException
test/medflow/MedFlowTests.java 199 test cases for Milestones 1 to 4
data/                          users.csv, patients.csv, visits.csv, duplicate_flags.csv (audit_log.csv is created on first run)
evidence/                      test results, sample runs, class diagram
```

## Menu by role (Milestones 3 and 4)

| Role          | Can use                                                                              |
|---------------|--------------------------------------------------------------------------------------|
| Receptionist  | Register, create a visit, visit status board (outcome note hidden), search, view     |
| Nurse         | Call patients and start consultations, visit status board, search, view              |
| Clinician     | Call patients, **record outcomes**, visit status board, search, view                 |
| Records Staff | Search, view, review duplicates                                                      |
| Administrator | Visit status board, search, view, audit log, user accounts                           |

## Next milestones
- Nov 16: version history (stack), audit log views, appointments and follow-ups (TreeMap)
- Nov 23: merge sort, binary search, k-way merge, reports with file export, testing
