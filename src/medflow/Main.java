package medflow;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.io.FileHandler;
import medflow.io.SampleData;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.PriorityCategory;
import medflow.model.Role;
import medflow.model.Service;
import medflow.model.Sex;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitType;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.service.VisitService;
import medflow.util.ConsoleInput;
import medflow.util.Validator;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * MedFlow: A Java-Based Clinic Queuing and Patient Records System
 * LBYCPDS E27, Group 7
 *
 * Milestone 1 (Nov 5): class structure, data models, role-based login
 * Milestone 2 (Nov 9): patient registration with input validation, age rules, and duplicate check
 * Milestone 3 (Nov 12): visit creation, queue numbers, priority lane, calling patients
 */
public class Main {

    /** One menu option and the permission needed to use it. */
    private interface Action { void run() throws AccessDeniedException, InvalidInputException; }

    private static class MenuItem {
        final String label;
        final Permission permission;
        final Action action;
        MenuItem(String label, Permission permission, Action action) {
            this.label = label;
            this.permission = permission;
            this.action = action;
        }
    }

    private final AuditLog auditLog = new AuditLog();
    private final AuthService auth = new AuthService(auditLog);
    private final PatientService patients = new PatientService(auth, auditLog);
    private final VisitService visits = new VisitService(auth, patients, auditLog);
    private final FileHandler files;
    private final ConsoleInput in = new ConsoleInput(new Scanner(System.in));
    private User currentUser;

    public Main(String dataFolder) { this.files = new FileHandler(dataFolder); }

    public static void main(String[] args) {
        new Main(args.length > 0 ? args[0] : "data").start();
    }

    // ================================================================== start and login

    private void start() {
        try {
            if (files.hasData()) {
                files.loadAll(auth, patients, visits, auditLog);
            } else {
                SampleData.load(auth, patients);
                System.out.println("(No saved data found. Loaded the fictional sample data.)");
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not read the data files: " + e.getMessage());
            return;
        }

        line('=');
        System.out.println("  MedFlow: Clinic Queuing and Patient Records System");
        System.out.println("  Family Medicine | Internal Medicine | Pediatrics");
        line('=');

        try {
            while (true) {
                System.out.println();
                String username = in.readLine("Username (or EXIT to close the program): ").trim();
                if (username.equalsIgnoreCase("EXIT")) break;
                if (username.isEmpty()) continue;
                String password = in.readLine("Password: ");
                currentUser = auth.login(username, password);
                if (currentUser == null) {
                    System.out.println("   ERROR: Wrong username or password.");
                    continue;
                }
                System.out.println("\nWelcome, " + currentUser.getFullName() + " (" + currentUser.getRole().getLabel() + ").");
                runMenu();
                auth.logout(currentUser);
                System.out.println("Logged out.");
                currentUser = null;
            }
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("\n(Input ended.)");
        }

        try {
            files.saveAll(auth, patients, visits, auditLog);
            System.out.println("Data saved. Goodbye.");
        } catch (IOException e) {
            System.out.println("Could not save the data: " + e.getMessage());
        }
    }

    // ================================================================== role menu

    private ArrayList<MenuItem> allModules() {
        ArrayList<MenuItem> m = new ArrayList<>();
        m.add(new MenuItem("Register a patient", Permission.REGISTER_PATIENT, this::registerPatient));
        m.add(new MenuItem("Create a visit and queue number", Permission.CREATE_VISIT, this::createVisitMenu));
        m.add(new MenuItem("Call patients / manage the queue", Permission.MANAGE_QUEUE, this::manageQueue));
        m.add(new MenuItem("Search patients", Permission.SEARCH_PATIENT, this::searchPatients));
        m.add(new MenuItem("View a patient record", Permission.VIEW_PATIENT_RECORD, this::viewPatientRecord));
        m.add(new MenuItem("Review possible duplicate records", Permission.RESOLVE_DUPLICATES, this::reviewDuplicates));
        m.add(new MenuItem("View the audit log", Permission.VIEW_AUDIT_LOG, this::viewAuditLog));
        m.add(new MenuItem("Manage user accounts", Permission.MANAGE_USERS, this::manageUsers));
        return m;
    }

    /** Shows only the modules that the role of the user can use. */
    private void runMenu() {
        ArrayList<MenuItem> menu = new ArrayList<>();
        for (MenuItem item : allModules()) {
            if (auth.can(currentUser, item.permission)) menu.add(item);
        }
        while (true) {
            System.out.println();
            line('-');
            System.out.println("  MAIN MENU  |  " + currentUser.getUsername() + " (" + currentUser.getRole().getLabel() + ")");
            line('-');
            for (int i = 0; i < menu.size(); i++) System.out.println("  " + (i + 1) + ". " + menu.get(i).label);
            System.out.println("  0. Log out");
            int choice = in.askChoice("Choose: ", 0, menu.size());
            if (choice == 0) return;
            try {
                menu.get(choice - 1).action.run();
            } catch (AccessDeniedException | InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    // ================================================================== registration (Figure 2)

    private void registerPatient() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.REGISTER_PATIENT);
        LocalDate today = LocalDate.now();
        header("PATIENT REGISTRATION");

        String last = in.ask("Last name                 : ", s -> Validator.validateName(s, "Last name", true));
        String first = in.ask("First name                : ", s -> Validator.validateName(s, "First name", true));
        String middle = in.ask("Middle name (Enter = none): ", s -> Validator.validateName(s, "Middle name", false));
        LocalDate birthdate = in.ask("Birthdate (YYYY-MM-DD)    : ", s -> Validator.validateBirthdate(s, today));
        Sex sex = in.ask("Sex (M/F)                 : ", Validator::validateSex);
        String address = in.ask("Address                   : ", Validator::validateAddress);
        String contact = in.ask("Contact number (09XXXXXXXXX): ", Validator::validateContactNumber);

        System.out.println("\nPlease check the details:");
        System.out.println("  Name     : " + last + ", " + first + (middle.isEmpty() ? "" : " " + middle));
        System.out.println("  Birthdate: " + birthdate + " (age " + java.time.Period.between(birthdate, today).getYears() + ")");
        System.out.println("  Sex      : " + sex.getLabel());
        System.out.println("  Address  : " + address);
        System.out.println("  Contact  : " + contact);
        if (!in.askYesNo("Save this patient? (Y/N): ")) {
            System.out.println("Registration cancelled. Nothing was saved.");
            return;
        }

        // Duplicate check: same last name, first name, and birthdate (HashMap lookup)
        ArrayList<Patient> matches = patients.findPossibleDuplicates(last, first, birthdate);
        Patient result;
        if (matches.isEmpty()) {
            result = patients.registerNew(currentUser, last, first, middle, birthdate, sex, address, contact);
            System.out.println("\nNo duplicate found. New record created.");
        } else {
            System.out.println("\nPOSSIBLE DUPLICATE: these records have the same name and birthdate:");
            for (Patient p : matches) {
                System.out.printf("  %s  %-30s  %s  %s  %s%n", p.getPatientId(), p.getFullName(),
                        p.getBirthdate(), p.getContactNumber(), p.getAddress());
            }
            String answer = in.ask("Is the patient one of these? Type the patient ID to use it, or N for a new record: ",
                    s -> {
                        String t = s.trim().toUpperCase();
                        if (t.equals("N") || t.equals("NO")) return "N";
                        String id = Validator.validatePatientId(t);
                        for (Patient p : matches) if (p.getPatientId().equals(id)) return id;
                        throw new InvalidInputException(id + " is not in the list above.");
                    });
            if (answer.equals("N")) {
                result = patients.registerNew(currentUser, last, first, middle, birthdate, sex, address, contact);
                System.out.println("\nNew record created and FLAGGED for records staff to review and merge.");
            } else {
                result = patients.useExisting(currentUser, answer);
                System.out.println("\nUsing the existing record. No new record was created.");
            }
        }
        int age = result.getAge(today);
        System.out.println("  Patient ID        : " + result.getPatientId());
        System.out.println("  Name              : " + result.getFullName());
        System.out.println("  Age               : " + age);
        System.out.println("  Eligible services : " + Validator.eligibleServices(age));

        // Milestone 3: go straight to visit creation (Figure 3)
        if (auth.can(currentUser, Permission.CREATE_VISIT)) {
            if (in.askYesNo("\nCreate a visit and queue number now? (Y/N): ")) createVisitFor(result);
            else System.out.println("  Next step         : use \"Create a visit\" from the main menu when the patient is ready.");
        }
    }

    // ================================================================== visit creation (Figure 3)

    /** Menu entry: find the patient by ID, then create the visit. */
    private void createVisitMenu() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.CREATE_VISIT);
        header("CREATE A VISIT");
        String id = in.ask("Patient ID: ", Validator::validatePatientId);
        Patient p = patients.findById(id);
        if (p == null) throw new InvalidInputException("No patient has the ID " + id + ".");
        if (p.isMerged()) {
            System.out.println("  " + id + " was merged into " + p.getMergedInto() + ". Using " + p.getMergedInto() + ".");
            p = patients.findById(p.getMergedInto());
        }
        createVisitFor(p);
    }

    private void createVisitFor(Patient p) throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.CREATE_VISIT);
        int age = p.getAge(LocalDate.now());
        header("CREATE A VISIT");
        System.out.println("  Patient : " + p.getPatientId() + "  " + p.getFullName() + " (age " + age + ")");

        // 1. Reason and visit type
        String reason = in.ask("Reason for visit: ", Validator::validateReason);
        VisitType[] types = VisitType.values();
        for (int i = 0; i < types.length; i++) System.out.println("  " + (i + 1) + ". " + pretty(types[i]));
        VisitType type = types[in.askChoice("Visit type: ", 1, types.length) - 1];

        // 2. Service, repeated until the age fits ("Show age rule error" loops back)
        Service[] services = Service.values();
        Service service;
        while (true) {
            for (int i = 0; i < services.length; i++) {
                System.out.println("  " + (i + 1) + ". " + services[i].getPrefix() + "  " + services[i].getLabel()
                        + " (" + services[i].getAgeRule() + ")");
            }
            service = services[in.askChoice("Service: ", 1, services.length) - 1];
            try {
                Validator.checkServiceEligibility(age, service);
                break;
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }

        // 3. Priority category (recorded by staff; the system never judges urgency)
        PriorityCategory[] cats = PriorityCategory.values();
        System.out.println("  Priority category recorded by staff:");
        for (int i = 0; i < cats.length; i++) {
            System.out.println("  " + (i + 1) + ". " + (cats[i].isPriority() ? pretty(cats[i]) : "Regular"));
        }
        PriorityCategory priority = cats[in.askChoice("Priority category: ", 1, cats.length) - 1];

        // 4. Create the visit, queue number, and place in line
        Visit v = visits.createVisit(currentUser, p.getPatientId(), service, reason, type, priority);
        System.out.println("\nVisit created.");
        System.out.println("  Visit ID     : " + v.getVisitId());
        System.out.println("  QUEUE NUMBER : " + v.getQueueNumber());
        System.out.println("  Service      : " + service.getLabel());
        System.out.println("  Line         : " + (priority.isPriority() ? "Priority lane (" + pretty(priority) + ")" : "Regular line"));
        System.out.println("  Position     : " + visits.positionOf(v));
        System.out.println("  Status       : " + pretty(v.getStatus()));
    }

    // ================================================================== calling patients (Figure 4)

    private void manageQueue() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.MANAGE_QUEUE);
        header("MANAGE QUEUE");
        Service[] services = Service.values();
        for (int i = 0; i < services.length; i++) {
            System.out.println("  " + (i + 1) + ". " + services[i].getLabel() + " (" + services[i].getPrefix() + ")  "
                    + visits.waitingCount(services[i]) + " waiting");
        }
        int pick = in.askChoice("Open the queue of (0 = back): ", 0, services.length);
        if (pick == 0) return;
        Service service = services[pick - 1];

        while (true) {
            ArrayList<Visit> view = showQueue(service);
            System.out.println("  1. Call next");
            System.out.println("  2. Patient missing");
            System.out.println("  3. Start consultation");
            System.out.println("  4. Cancel a visit");
            System.out.println("  0. Back");
            int action = in.askChoice("Choose: ", 0, 4);
            if (action == 0) return;
            try {
                if (action == 1) {
                    Visit v = visits.callNext(currentUser, service);
                    if (v == null) System.out.println("No patients waiting.");
                    else System.out.println("\n>>> NOW CALLING: " + v.getQueueNumber() + "  " + nameOf(v) + " <<<");
                } else if (action == 2) {
                    Visit v = visits.markMissed(currentUser, service);
                    if (v.getMissedCount() >= VisitService.MAX_MISSED) {
                        System.out.println(v.getQueueNumber() + " missed " + v.getMissedCount() + " calls. Status set to No-show.");
                    } else {
                        System.out.println(v.getQueueNumber() + " marked as missed (" + v.getMissedCount()
                                + "). Moved to the back of the same line.");
                    }
                } else if (action == 3) {
                    Visit v = visits.markArrived(currentUser, service);
                    System.out.println(v.getQueueNumber() + " is now in consultation.");
                } else {
                    cancelFromQueue(view);
                }
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    /** Shows the patient being called and everyone waiting. Returns the list so a number can be picked. */
    private ArrayList<Visit> showQueue(Service service) {
        ArrayList<Visit> view = visits.getQueueView(service);
        Visit calling = visits.getNowCalling(service);
        System.out.println();
        line('-');
        System.out.println("  " + service.getLabel() + " queue");
        line('-');
        if (view.isEmpty()) {
            System.out.println("  No patients waiting.");
        } else {
            System.out.printf("  %-3s %-8s %-28s %-18s %s%n", "#", "Number", "Name", "Line", "Status");
            for (int i = 0; i < view.size(); i++) {
                Visit v = view.get(i);
                String lineName = v.getPriorityCategory().isPriority() ? "Priority" : "Regular";
                String status = v == calling ? "NOW CALLING" : "Waiting";
                if (v.getMissedCount() > 0) status += " (missed " + v.getMissedCount() + ")";
                System.out.printf("  %-3d %-8s %-28s %-18s %s%n", i + 1, v.getQueueNumber(), cut(nameOf(v), 27),
                        lineName, status);
            }
        }
        return view;
    }

    private void cancelFromQueue(ArrayList<Visit> view) throws AccessDeniedException, InvalidInputException {
        if (view.isEmpty()) throw new InvalidInputException("There is no visit to cancel.");
        int pick = in.askChoice("Number in the list to cancel (0 = back): ", 0, view.size());
        if (pick == 0) return;
        Visit v = view.get(pick - 1);
        if (!in.askYesNo("Cancel " + v.getQueueNumber() + " (" + nameOf(v) + ")? (Y/N): ")) return;
        visits.cancel(currentUser, v);
        System.out.println(v.getQueueNumber() + " cancelled. The queue number is free again.");
    }

    private String nameOf(Visit v) {
        Patient p = patients.findById(v.getPatientId());
        return p == null ? v.getPatientId() : p.getFullName();
    }

    /** SENIOR_CITIZEN becomes "Senior citizen". */
    private static String pretty(Enum<?> e) {
        String s = e.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ================================================================== search and view

    private void searchPatients() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.SEARCH_PATIENT);
        header("SEARCH PATIENTS");
        System.out.println("  1. By patient ID");
        System.out.println("  2. By name (partial name is OK)");
        int choice = in.askChoice("Choose: ", 1, 2);
        if (choice == 1) {
            String id = in.ask("Patient ID: ", Validator::validatePatientId);
            Patient p = patients.findById(id);
            if (p == null) System.out.println("No patient has the ID " + id + ".");
            else printPatientRows(java.util.List.of(p));
        } else {
            String q = in.ask("Name to search: ", s -> Validator.validateName(s, "Search text", true));
            ArrayList<Patient> found = patients.searchByName(currentUser, q);
            if (found.isEmpty()) System.out.println("No patient matched \"" + q + "\".");
            else printPatientRows(found);
        }
    }

    private void printPatientRows(java.util.List<Patient> list) {
        LocalDate today = LocalDate.now();
        System.out.printf("%n  %-6s %-32s %-11s %-4s %-12s%n", "ID", "Name", "Birthdate", "Age", "Contact");
        for (Patient p : list) {
            System.out.printf("  %-6s %-32s %-11s %-4d %-12s%s%n", p.getPatientId(), p.getFullName(), p.getBirthdate(),
                    p.getAge(today), p.getContactNumber(), p.isMerged() ? "  (merged into " + p.getMergedInto() + ")" : "");
        }
        System.out.println("  " + list.size() + " record(s) found.");
    }

    private void viewPatientRecord() throws AccessDeniedException, InvalidInputException {
        header("VIEW PATIENT RECORD");
        String id = in.ask("Patient ID: ", Validator::validatePatientId);
        System.out.println();
        System.out.print(patients.viewRecord(currentUser, id));
    }

    // ================================================================== duplicate review (records staff)

    private void reviewDuplicates() throws AccessDeniedException, InvalidInputException {
        header("REVIEW POSSIBLE DUPLICATE RECORDS");
        ArrayList<DuplicateFlag> pending = patients.getPendingFlags(currentUser);
        if (pending.isEmpty()) {
            System.out.println("No possible duplicates to review.");
            return;
        }
        for (int i = 0; i < pending.size(); i++) {
            DuplicateFlag f = pending.get(i);
            System.out.printf("  %d. New %s  vs  existing %s   (flagged by %s)%n", i + 1,
                    f.getNewPatientId(), f.getExistingPatientId(), f.getFlaggedBy());
        }
        int pick = in.askChoice("Choose a flag to review (0 = back): ", 0, pending.size());
        if (pick == 0) return;
        DuplicateFlag flag = pending.get(pick - 1);
        Patient a = patients.findById(flag.getExistingPatientId());
        Patient b = patients.findById(flag.getNewPatientId());

        System.out.printf("%n  %-15s %-32s %-32s%n", "", "EXISTING " + a.getPatientId(), "NEW " + b.getPatientId());
        compare("Name", a.getFullName(), b.getFullName());
        compare("Birthdate", a.getBirthdate().toString(), b.getBirthdate().toString());
        compare("Sex", a.getSex().getLabel(), b.getSex().getLabel());
        compare("Contact", a.getContactNumber(), b.getContactNumber());
        compare("Address", a.getAddress(), b.getAddress());
        compare("Registered by", a.getCreatedBy(), b.getCreatedBy());

        System.out.println("\n  1. Same patient: merge " + b.getPatientId() + " into " + a.getPatientId());
        System.out.println("  2. Different patients: keep both records");
        System.out.println("  0. Decide later");
        int action = in.askChoice("Choose: ", 0, 2);
        if (action == 1) {
            patients.merge(currentUser, flag);
            System.out.println("Merged. " + b.getPatientId() + " is kept for history and now points to " + a.getPatientId() + ".");
        } else if (action == 2) {
            patients.keepBoth(currentUser, flag);
            System.out.println("Both records kept.");
        }
    }

    private void compare(String label, String a, String b) {
        String mark = a.equalsIgnoreCase(b) ? "" : "   <- different";
        System.out.printf("  %-15s %-32s %-32s%s%n", label, cut(a, 31), cut(b, 31), mark);
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 3) + "..."; }

    // ================================================================== administrator

    private void viewAuditLog() throws AccessDeniedException {
        auth.require(currentUser, Permission.VIEW_AUDIT_LOG);
        header("AUDIT LOG (latest 25 entries)");
        for (AuditEntry e : auditLog.last(25)) System.out.println("  " + e);
        System.out.println("  Total entries: " + auditLog.size());
    }

    private void manageUsers() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.MANAGE_USERS);
        header("USER ACCOUNTS");
        ArrayList<User> users = auth.getAllUsers();
        users.sort((x, y) -> x.getRole().compareTo(y.getRole()));   // display only
        for (User u : users) System.out.printf("  %-12s %-22s %s%n", u.getUsername(), u.getFullName(), u.getRole().getLabel());
        if (!in.askYesNo("\nAdd a new account? (Y/N): ")) return;

        String username = in.ask("Username: ", Validator::validateUsername);
        String password = in.ask("Password: ", Validator::validatePassword);
        String fullName = in.ask("Full name: ", s -> Validator.validateName(s, "Full name", true));
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i++) System.out.println("  " + (i + 1) + ". " + roles[i].getLabel());
        Role role = roles[in.askChoice("Role: ", 1, roles.length) - 1];
        User u = auth.addUser(currentUser, username, password, fullName, role);
        System.out.println("Account created: " + u);
    }

    // ================================================================== helpers

    private static void line(char c) { System.out.println(String.valueOf(c).repeat(64)); }

    private static void header(String title) {
        System.out.println();
        System.out.println("== " + title + " " + "=".repeat(Math.max(3, 60 - title.length())));
    }
}
