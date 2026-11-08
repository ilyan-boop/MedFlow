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
src/medflow/service/           AuthService, PatientService
src/medflow/ds/                hand-coded data structures (AuditLog linked list)
src/medflow/util/              Validator, ConsoleInput
src/medflow/io/                FileHandler, Csv, SampleData
src/medflow/exception/         InvalidInputException, AccessDeniedException
test/medflow/MedFlowTests.java 105 test cases for Milestones 1 and 2
data/                          users.csv, patients.csv, duplicate_flags.csv (audit_log.csv is created on first run)
evidence/                      test results, sample run, class diagram
```

## Next milestones
- Nov 12: visit creation, queue numbers (HashSet), FIFO queue and priority lane (hand-coded queue and heap)
- Nov 14 to 16: calling patients, consultation, version history (stack), appointments (TreeMap)
- Nov 23: merge sort, binary search, k-way merge, reports, testing
