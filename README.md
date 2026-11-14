# MedFlow (LBYCPDS E27, Group 7)

A Java-based clinic queuing and patient records system.
This version covers **Milestones 1 to 5 (Nov 5 to Nov 16)** of the Gantt chart.

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

**Milestone 3 (Nov 12): visit creation and service queues**
- Visit ID linked to the patient, age rule checked, unique queue numbers per service (`HashSet`), e.g. FM-001
- Regular line and priority lane per service as **hand-coded FIFO queues** (`LinkedQueue`, enqueue and dequeue in O(1))
- Call next (priority lane first), missed-patient rule (back of the line, second miss = No-show), cancel

**Milestone 4 (Nov 14): consultation tracking and visit status**
- Start consultation, record the outcome (Treated, Follow-up advised, Referred), consultation times
- Allowed status changes checked with an `EnumMap`, visit status board with average waiting and consultation times

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

## How to run

**IntelliJ IDEA / VS Code:** open the `MedFlow` folder, mark `src` (and `test`) as source folders, then run `medflow.Main`.
Run with the `MedFlow` folder as the working directory so it finds the `data` folder.

**Command line (from the MedFlow folder):**
```
javac -d out src/medflow/*.java src/medflow/*/*.java test/medflow/*.java
java -cp out medflow.Main
java -cp out medflow.MedFlowTests
java -cp out medflow.Milestone5Tests
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

Sample patients P0001 to P0008 and appointments A0001 to A0004 are fictional. To start over, delete the files in `data/`
(the program reloads the sample data when `users.csv` is missing).

## Folder structure
```
src/medflow/Main.java          console menu, login loop, registration screens
src/medflow/model/             data models and enums
src/medflow/service/           AuthService, PatientService, VisitService, RecordHistory, AppointmentService
src/medflow/ds/                hand-coded data structures (AuditLog linked list, LinkedQueue, VersionStack)
src/medflow/util/              Validator, ConsoleInput
src/medflow/io/                FileHandler, Csv, SampleData
src/medflow/exception/         InvalidInputException, AccessDeniedException
test/medflow/MedFlowTests.java 105 test cases for Milestones 1 and 2
test/medflow/Milestone5Tests.java  81 test cases for Milestone 5 and the hand-coded queue
data/                          users, patients, duplicate flags, visits, appointments, record versions, audit log (.csv)
evidence/                      test results, sample run, class diagram
```

## Next milestones
- Nov 23: search and daily reports with hand-coded merge sort, binary search, and k-way merge, .txt/.csv export
- Nov 26: testing with sample data, time complexity analysis, documentation, final checking
