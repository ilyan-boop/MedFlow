package medflow;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.io.FileHandler;
import medflow.io.SampleData;
import medflow.model.Appointment;
import medflow.model.AppointmentStatus;
import medflow.model.ConsultOutcome;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.PriorityCategory;
import medflow.model.RecordField;
import medflow.model.RecordVersion;
import medflow.model.Role;
import medflow.model.Service;
import medflow.model.Sex;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitStatus;
import medflow.model.VisitType;
import medflow.service.AppointmentService;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.service.RecordHistory;
import medflow.service.VisitService;
import medflow.util.ConsoleInput;
import medflow.util.Validator;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Scanner;

/**
 * MedFlow: A Java-Based Clinic Queuing and Patient Records System
 * LBYCPDS E27, Group 7
 *
 * Milestone 1 (Nov 5): class structure, data models, role-based login
 * Milestone 2 (Nov 9): patient registration with input validation, age rules, and duplicate check
 * Milestone 3 (Nov 12): visit creation, queue numbers, priority lane, calling patients
 * Milestone 4 (Nov 14): consultation tracking, visit status, and outcome recording
 * Milestone 5 (Nov 16): record updates with version history (stack), audit log filters, and
 *                       appointments and follow-ups (TreeMap)
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
    private final RecordHistory records = new RecordHistory(auth, patients, auditLog);
    private final AppointmentService appointments = new AppointmentService(auth, patients, visits, auditLog);
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
                files.loadAll(auth, patients, visits, records, appointments, auditLog);
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
            files.saveAll(auth, patients, visits, records, appointments, auditLog);
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
        m.add(new MenuItem("Consultations: record the outcome", Permission.RECORD_CONSULTATION, this::consultations));
        m.add(new MenuItem("Visit status board and tracking", Permission.VIEW_VISIT_STATUS, this::statusBoard));
        m.add(new MenuItem("Appointments and follow-ups", Permission.MANAGE_APPOINTMENTS, this::appointmentsMenu));
        m.add(new MenuItem("Search patients", Permission.SEARCH_PATIENT, this::searchPatients));
        m.add(new MenuItem("View a patient record", Permission.VIEW_PATIENT_RECORD, this::viewPatientRecord));
        m.add(new MenuItem("Correct patient details", Permission.CORRECT_RECORDS, () -> updateRecord(false)));
        m.add(new MenuItem("Update clinical notes (allergies, history)", Permission.EDIT_CLINICAL_NOTES, () -> updateRecord(true)));
        m.add(new MenuItem("Version history of a record", Permission.VIEW_VERSION_HISTORY, this::versionHistory));
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
            System.out.println("  3. Start consultation (patient came)");
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
                    System.out.println(v.getQueueNumber() + " is now in consultation (waited "
                            + v.getWaitingMinutes() + " min). The clinician records the outcome when done.");
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
        showInConsultation(service);
        return view;
    }

    /** Patients of this service who are with the clinician now (Milestone 4). */
    private void showInConsultation(Service service) {
        ArrayList<Visit> consulting = visits.getInConsultation(service);
        if (consulting.isEmpty()) return;
        System.out.println("  In consultation:");
        for (Visit v : consulting) {
            System.out.printf("    %-8s %-28s started %s (%d min so far)%n", v.getQueueNumber(), cut(nameOf(v), 27),
                    hm(v.getConsultStart()), minutesSince(v.getConsultStart()));
        }
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

    // ================================================================== consultation and outcome (Milestone 4)

    private void consultations() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.RECORD_CONSULTATION);
        header("CONSULTATIONS IN PROGRESS");
        while (true) {
            ArrayList<Visit> list = visits.getAllInConsultation();
            if (list.isEmpty()) {
                System.out.println("  No patient is in consultation.");
                return;
            }
            System.out.printf("  %-3s %-6s %-8s %-28s %-18s %-6s %s%n", "#", "Visit", "Number", "Name", "Service", "Start", "Minutes");
            for (int i = 0; i < list.size(); i++) {
                Visit v = list.get(i);
                System.out.printf("  %-3d %-6s %-8s %-28s %-18s %-6s %d%n", i + 1, v.getVisitId(), v.getQueueNumber(),
                        cut(nameOf(v), 27), v.getService().getLabel(), hm(v.getConsultStart()), minutesSince(v.getConsultStart()));
            }
            int pick = in.askChoice("Choose a consultation to finish (0 = back): ", 0, list.size());
            if (pick == 0) return;
            try {
                finishConsultation(list.get(pick - 1));
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    private void finishConsultation(Visit v) throws AccessDeniedException, InvalidInputException {
        Patient p = patients.findById(v.getPatientId());
        header("RECORD OUTCOME  " + v.getVisitId() + " (" + v.getQueueNumber() + ")");
        System.out.println("  Patient   : " + nameOf(v) + " (" + v.getPatientId() + ")");
        System.out.println("  Reason    : " + v.getReasonForVisit());
        if (p != null) {
            System.out.println("  Allergies : " + (p.getAllergies().isEmpty() ? "none recorded" : p.getAllergies()));
            System.out.println("  History   : " + (p.getRelevantHistory().isEmpty() ? "none recorded" : p.getRelevantHistory()));
        }
        System.out.println("  Started   : " + hm(v.getConsultStart()) + " (" + minutesSince(v.getConsultStart()) + " min so far)");

        ConsultOutcome[] outcomes = ConsultOutcome.values();
        System.out.println();
        for (int i = 0; i < outcomes.length; i++) {
            System.out.println("  " + (i + 1) + ". " + outcomes[i].getLabel() + "  -> status " + pretty(outcomes[i].getFinalStatus()));
        }
        System.out.println("  0. Back (the patient stays in consultation)");
        int pick = in.askChoice("Outcome: ", 0, outcomes.length);
        if (pick == 0) return;
        ConsultOutcome type = outcomes[pick - 1];
        String prompt = type == ConsultOutcome.REFERRED ? "Referred to and reason: "
                : type == ConsultOutcome.FOLLOW_UP ? "Outcome note and when to come back: " : "Outcome note: ";
        String note = in.ask(prompt, Validator::validateOutcomeNote);

        System.out.println("\n  Outcome : " + type.getLabel());
        System.out.println("  Note    : " + note);
        if (!in.askYesNo("Save this outcome? (Y/N): ")) {
            System.out.println("Nothing was saved. The patient stays in consultation.");
            return;
        }
        Visit done = visits.recordOutcome(currentUser, v.getVisitId(), type, note);
        System.out.println("\nOutcome recorded.");
        System.out.println("  Visit        : " + done.getVisitId() + " (" + done.getQueueNumber() + ")");
        System.out.println("  Status       : " + pretty(done.getStatus()));
        System.out.println("  Consultation : " + hm(done.getConsultStart()) + " to " + hm(done.getConsultEnd())
                + " (" + done.getConsultMinutes() + " min)");
        System.out.println("  Queue number " + done.getQueueNumber() + " is free again.");
        if (type == ConsultOutcome.FOLLOW_UP) {
            System.out.println("  Follow-up    : the receptionist books it under Appointments and follow-ups >");
            System.out.println("                 \"Book a follow-up from a consultation\".");
        }
    }

    // ================================================================== visit status board (Milestone 4)

    private void statusBoard() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.VIEW_VISIT_STATUS);
        while (true) {
            header("VISIT STATUS BOARD");
            System.out.printf("  %-18s %5s %5s %5s %5s %5s %5s %5s  %8s %8s%n", "Service", "Wait", "Call", "Cons",
                    "Done", "Refer", "Canc", "NoShw", "AvgWait", "AvgCons");
            for (Service s : Service.values()) {
                EnumMap<VisitStatus, Integer> c = visits.statusCounts(currentUser, s);
                int calling = visits.getNowCalling(s) == null ? 0 : 1;
                System.out.printf("  %-18s %5d %5d %5d %5d %5d %5d %5d  %8s %8s%n", s.getLabel(),
                        c.get(VisitStatus.WAITING) - calling + c.get(VisitStatus.REGISTERED), calling,
                        c.get(VisitStatus.IN_CONSULTATION), c.get(VisitStatus.COMPLETED), c.get(VisitStatus.REFERRED),
                        c.get(VisitStatus.CANCELLED), c.get(VisitStatus.NO_SHOW),
                        minutesText(visits.averageWaitingMinutes(s)), minutesText(visits.averageConsultMinutes(s)));
            }
            System.out.println("  Wait = waiting, Call = being called, Cons = in consultation, Refer = referred,");
            System.out.println("  Canc = cancelled, NoShw = no-show. Averages are in minutes.");
            System.out.println();
            System.out.println("  1. Track a visit (Visit ID) or all visits of a patient (Patient ID)");
            System.out.println("  0. Back");
            if (in.askChoice("Choose: ", 0, 1) == 0) return;

            String code = in.ask("Visit ID (V0001) or Patient ID (P0001): ", s -> {
                String t = s == null ? "" : s.trim().toUpperCase();
                return t.startsWith("V") ? Validator.validateVisitId(t) : Validator.validatePatientId(t);
            });
            try {
                if (code.startsWith("V")) {
                    printVisitCard(visits.trackVisit(currentUser, code));
                } else {
                    ArrayList<Visit> list = visits.trackPatientVisits(currentUser, code);
                    if (list.isEmpty()) System.out.println("  " + code + " has no visits yet.");
                    for (Visit v : list) printVisitCard(v);
                }
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    /** The timeline of one visit. The outcome note is shown only to roles that may see clinical notes. */
    private void printVisitCard(Visit v) {
        System.out.println();
        line('-');
        System.out.println("  Visit ID      : " + v.getVisitId() + "   Queue number: " + v.getQueueNumber());
        System.out.println("  Patient       : " + v.getPatientId() + "  " + nameOf(v));
        System.out.println("  Service       : " + v.getService().getLabel() + "   (" + pretty(v.getVisitType()) + ", "
                + (v.getPriorityCategory().isPriority() ? "priority lane" : "regular line") + ")");
        System.out.println("  Reason        : " + v.getReasonForVisit());
        System.out.println("  STATUS        : " + pretty(v.getStatus()).toUpperCase());
        System.out.println("  Arrived       : " + v.getArrivalTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        System.out.println("  Last called   : " + (v.getCalledTime() == null ? "not yet" : hm(v.getCalledTime()))
                + (v.getMissedCount() > 0 ? "   (missed calls: " + v.getMissedCount() + ")" : ""));
        if (v.getConsultStart() == null) {
            System.out.println("  Consultation  : not started");
        } else {
            System.out.println("  Consult start : " + hm(v.getConsultStart()) + "   (waited " + v.getWaitingMinutes() + " min)");
            if (v.getConsultEnd() == null) {
                System.out.println("  Consult end   : still in consultation (" + minutesSince(v.getConsultStart()) + " min so far)");
            } else {
                System.out.println("  Consult end   : " + hm(v.getConsultEnd()) + "   (consultation " + v.getConsultMinutes() + " min)");
            }
        }
        if (v.getOutcomeType() != null) {
            if (auth.can(currentUser, Permission.VIEW_CLINICAL_NOTES)) {
                System.out.println("  Outcome       : " + v.getOutcomeType().getLabel());
                System.out.println("  Outcome note  : " + v.getOutcome() + "   (by " + v.getConsultedBy() + ")");
            } else {
                System.out.println("  Outcome       : hidden for the " + currentUser.getRole().getLabel() + " role");
            }
        }
    }

    private static String hm(LocalDateTime t) { return t == null ? "-" : t.format(DateTimeFormatter.ofPattern("HH:mm")); }

    private static long minutesSince(LocalDateTime t) { return Duration.between(t, LocalDateTime.now()).toMinutes(); }

    private static String minutesText(double minutes) { return minutes < 0 ? "-" : String.format("%.1f", minutes); }

    private String nameOf(Visit v) {
        Patient p = patients.findById(v.getPatientId());
        return p == null ? v.getPatientId() : p.getFullName();
    }

    /** SENIOR_CITIZEN becomes "Senior citizen". */
    private static String pretty(Enum<?> e) {
        String s = e.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ================================================================== Milestone 5: record updates (Figure 6)

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd");

    /** Shows a clinical value only to roles that may see clinical notes. */
    private String shown(RecordField field, String value) {
        if (field.isClinical() && !auth.can(currentUser, Permission.VIEW_CLINICAL_NOTES)) return "(clinical, hidden)";
        return value.isEmpty() ? "(none)" : value;
    }

    /**
     * Records Staff correct personal details. Clinicians update allergies and relevant history.
     * Every change pushes the old version on the version stack first (RecordHistory.updateField).
     */
    private void updateRecord(boolean clinical) throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, clinical ? Permission.EDIT_CLINICAL_NOTES : Permission.CORRECT_RECORDS);
        header(clinical ? "UPDATE CLINICAL NOTES" : "CORRECT PATIENT DETAILS");
        String id = in.ask("Patient ID: ", Validator::validatePatientId);
        Patient p = patients.findById(id);
        if (p == null) throw new InvalidInputException("No patient has the ID " + id + ".");
        if (p.isMerged()) throw new InvalidInputException(id + " was merged into " + p.getMergedInto() + ". Update that record instead.");

        ArrayList<RecordField> fields = new ArrayList<>();
        for (RecordField f : RecordField.values()) if (f.isClinical() == clinical) fields.add(f);

        while (true) {
            System.out.println();
            System.out.println("  " + p.getPatientId() + "  " + p.getFullName() + "   (version " + records.currentVersion(p.getPatientId()) + ")");
            for (int i = 0; i < fields.size(); i++) {
                RecordField f = fields.get(i);
                System.out.printf("  %d. %-17s %s%n", i + 1, f.getLabel(), shown(f, RecordHistory.valueOf(p, f)));
            }
            System.out.println("  0. Done");
            int pick = in.askChoice("Field to change: ", 0, fields.size());
            if (pick == 0) return;
            RecordField field = fields.get(pick - 1);

            String hint = field == RecordField.BIRTHDATE ? " (YYYY-MM-DD)"
                    : field == RecordField.CONTACT_NUMBER ? " (09XXXXXXXXX)"
                    : field == RecordField.SEX ? " (M/F)"
                    : field.isClinical() || field == RecordField.MIDDLE_NAME ? " (Enter or \"none\" = clear)" : "";
            String newValue = in.ask("New " + field.getLabel().toLowerCase() + hint + ": ", t -> RecordHistory.clean(field, t));
            String oldValue = RecordHistory.valueOf(p, field);
            System.out.println("  " + field.getLabel() + ": " + shown(field, oldValue) + "  ->  " + shown(field, newValue));
            if (!in.askYesNo("Save this change? (Y/N): ")) {
                System.out.println("  Nothing was changed.");
                continue;
            }
            try {
                RecordVersion saved = records.updateField(currentUser, p.getPatientId(), field, newValue);
                System.out.println("  Saved. Version " + saved.getVersionNumber() + " (the old record) was pushed on the version stack.");
                System.out.println("  The record is now version " + records.currentVersion(p.getPatientId()) + ".");
                if (field == RecordField.BIRTHDATE) {
                    System.out.println("  Age is now " + p.getAge(LocalDate.now()) + ". Eligible services: "
                            + Validator.eligibleServices(p.getAge(LocalDate.now())));
                }
                if (field.isIdentity()
                        && patients.findPossibleDuplicates(p.getLastName(), p.getFirstName(), p.getBirthdate()).size() > 1) {
                    System.out.println("  NOTE: another record now has the same name and birthdate. It was flagged for review.");
                }
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    /** Version history, newest first (top of the stack). A change can be undone, which saves a new version. */
    private void versionHistory() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.VIEW_VERSION_HISTORY);
        header("VERSION HISTORY");
        String id = in.ask("Patient ID: ", Validator::validatePatientId);
        while (true) {
            ArrayList<RecordVersion> list = records.getHistory(currentUser, id);
            Patient p = patients.findById(id);
            System.out.println();
            System.out.println("  " + p.getPatientId() + "  " + p.getFullName() + "   current version: "
                    + records.currentVersion(p.getPatientId()));
            if (list.isEmpty()) {
                System.out.println("  No changes since the record was registered.");
                return;
            }
            System.out.println("  Newest first (top of the stack). Each line is a saved version and the change that replaced it:");
            System.out.printf("  %-4s %-16s %-10s %-17s %s%n", "Ver", "Changed on", "By", "Field", "Change");
            for (RecordVersion v : list) {
                RecordField f = v.getChangedField();
                String change = shown(f, v.getOldValue()) + "  ->  " + shown(f, v.getNewValue())
                        + (v.getAction() == RecordVersion.Action.RESTORE ? "   (undo)" : "");
                System.out.printf("  v%-3d %-16s %-10s %-17s %s%n", v.getVersionNumber(), v.getChangedAt().format(STAMP),
                        v.getChangedBy(), f.getLabel(), change);
            }
            System.out.println();
            System.out.println("  1. View a saved version in full");
            System.out.println("  2. Undo the latest change");
            System.out.println("  3. Undo the change made to a saved version");
            System.out.println("  0. Back");
            int pick = in.askChoice("Choose: ", 0, 3);
            if (pick == 0) return;
            try {
                if (pick == 1) {
                    int n = in.askChoice("Version number: ", 1, records.currentVersion(id) - 1);
                    printVersion(records.findVersion(id, n));
                } else {
                    RecordVersion target = pick == 2 ? list.get(0)
                            : records.findVersion(id, in.askChoice("Version number: ", 1, records.currentVersion(id) - 1));
                    RecordField f = target.getChangedField();
                    if (!auth.can(currentUser, f.getEditPermission())) {
                        System.out.println("   ERROR: Only a " + (f.isClinical() ? "Clinician" : "Records Staff member")
                                + " can undo a change to the " + f.getLabel().toLowerCase() + ".");
                        continue;
                    }
                    System.out.println("  This puts " + f.getLabel().toLowerCase() + " back to: " + shown(f, target.getOldValue()));
                    if (!in.askYesNo("Undo this change? (Y/N): ")) continue;
                    RecordVersion saved = records.restore(currentUser, id, target.getVersionNumber());
                    System.out.println("  Undone. The record before the undo was saved as version " + saved.getVersionNumber()
                            + ", so nothing was lost.");
                }
            } catch (AccessDeniedException e) {
                System.out.println("   ERROR: " + e.getMessage());
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    private void printVersion(RecordVersion v) {
        System.out.println();
        line('-');
        System.out.println("  Version " + v.getVersionNumber() + " of " + v.getPatientId()
                + "  (replaced on " + v.getChangedAt().format(STAMP) + " by " + v.getChangedBy() + ")");
        System.out.println("  Name           : " + v.getFullName());
        System.out.println("  Birthdate      : " + v.getBirthdate());
        System.out.println("  Sex            : " + v.getSex().getLabel());
        System.out.println("  Address        : " + v.getAddress());
        System.out.println("  Contact number : " + v.getContactNumber());
        System.out.println("  Allergies      : " + shown(RecordField.ALLERGIES, v.getAllergies()));
        System.out.println("  History        : " + shown(RecordField.RELEVANT_HISTORY, v.getRelevantHistory()));
    }

    // ================================================================== Milestone 5: appointments (Figure 7)

    private void appointmentsMenu() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.MANAGE_APPOINTMENTS);
        int marked = appointments.markMissed(LocalDateTime.now());
        if (marked > 0) System.out.println("\n  " + marked + " appointment(s) passed without a check-in and were marked Missed.");
        while (true) {
            header("APPOINTMENTS AND FOLLOW-UPS");
            System.out.println("  1. Book an appointment");
            System.out.println("  2. Book a follow-up from a consultation  (" + appointments.followUpsToBook(currentUser).size() + " to book)");
            System.out.println("  3. Today's appointments and check-in");
            System.out.println("  4. Upcoming appointments");
            System.out.println("  5. Missed appointments");
            System.out.println("  6. Reschedule an appointment");
            System.out.println("  7. Cancel an appointment");
            System.out.println("  8. Appointments of a patient");
            System.out.println("  0. Back");
            int pick = in.askChoice("Choose: ", 0, 8);
            if (pick == 0) return;
            try {
                switch (pick) {
                    case 1: bookAppointment(null); break;
                    case 2: bookFollowUp(); break;
                    case 3: todayAndCheckIn(); break;
                    case 4: printAppointments("UPCOMING APPOINTMENTS", appointments.upcoming(currentUser, LocalDateTime.now())); break;
                    case 5: printAppointments("MISSED APPOINTMENTS (newest first)", appointments.missed(currentUser, LocalDateTime.now())); break;
                    case 6: rescheduleAppointment(); break;
                    case 7: cancelAppointment(); break;
                    default: {
                        String id = in.ask("Patient ID: ", Validator::validatePatientId);
                        printAppointments("APPOINTMENTS OF " + id, appointments.ofPatient(currentUser, id, LocalDateTime.now()));
                    }
                }
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    /** Asks for a date and time until the slot can be booked. */
    private LocalDateTime askDateTime(String label) {
        LocalDate date = in.ask(label + " date (YYYY-MM-DD): ", t -> Validator.validateAppointmentDate(t, LocalDate.now()));
        LocalTime time = in.ask(label + " time (HH:MM, 08:00 to 16:30): ", Validator::validateAppointmentTime);
        return LocalDateTime.of(date, time);
    }

    /** Books a new appointment. For a follow-up, "from" is the consultation that advised it. */
    private void bookAppointment(Visit from) throws AccessDeniedException, InvalidInputException {
        header(from == null ? "BOOK AN APPOINTMENT" : "BOOK A FOLLOW-UP");
        Patient p;
        Service service;
        String purpose;
        if (from == null) {
            String id = in.ask("Patient ID: ", Validator::validatePatientId);
            p = patients.findById(id);
            if (p == null) throw new InvalidInputException("No patient has the ID " + id + ".");
            if (p.isMerged()) {
                System.out.println("  " + id + " was merged into " + p.getMergedInto() + ". Using " + p.getMergedInto() + ".");
                p = patients.findById(p.getMergedInto());
            }
            System.out.println("  Patient : " + p.getPatientId() + "  " + p.getFullName() + " (age " + p.getAge(LocalDate.now()) + ")");
            Service[] services = Service.values();
            for (int i = 0; i < services.length; i++) {
                System.out.println("  " + (i + 1) + ". " + services[i].getPrefix() + "  " + services[i].getLabel()
                        + " (" + services[i].getAgeRule() + ")");
            }
            service = services[in.askChoice("Service: ", 1, services.length) - 1];
            purpose = in.ask("Purpose of the appointment: ", Validator::validateReason);
        } else {
            p = patients.findById(from.getPatientId());
            service = from.getService();
            purpose = "Follow-up of " + from.getVisitId();
            System.out.println("  Patient : " + p.getPatientId() + "  " + p.getFullName());
            System.out.println("  Service : " + service.getLabel() + " (same service as " + from.getVisitId() + ")");
            System.out.println("  Purpose : " + purpose);
        }

        while (true) {
            LocalDateTime when = askDateTime("Appointment");
            System.out.println("  " + appointments.openPlaces(service, when) + " of " + AppointmentService.SLOT_CAPACITY
                    + " places open in " + service.getPrefix() + " at " + when.toLocalTime() + " on " + when.format(DAY));
            if (!in.askYesNo("Book " + p.getPatientId() + " on " + when.format(DAY) + " at " + when.toLocalTime() + "? (Y/N): ")) {
                System.out.println("  Nothing was booked.");
                return;
            }
            try {
                Appointment a = appointments.book(currentUser, p.getPatientId(), service, when, purpose,
                        from == null ? "" : from.getVisitId(), LocalDateTime.now());
                System.out.println("\n  Booked.");
                printAppointmentCard(a);
                return;
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage() + " Please choose another date or time.");
            }
        }
    }

    private void bookFollowUp() throws AccessDeniedException, InvalidInputException {
        ArrayList<Visit> list = appointments.followUpsToBook(currentUser);
        header("FOLLOW-UPS TO BOOK");
        if (list.isEmpty()) {
            System.out.println("  No consultation is waiting for a follow-up booking.");
            return;
        }
        System.out.printf("  %-3s %-6s %-28s %-18s %-16s %s%n", "#", "Visit", "Patient", "Service", "Consult ended", "Clinician");
        for (int i = 0; i < list.size(); i++) {
            Visit v = list.get(i);
            System.out.printf("  %-3d %-6s %-28s %-18s %-16s %s%n", i + 1, v.getVisitId(), cut(nameOf(v), 27),
                    v.getService().getLabel(), v.getConsultEnd().format(STAMP), v.getConsultedBy());
        }
        int pick = in.askChoice("Choose a consultation (0 = back): ", 0, list.size());
        if (pick == 0) return;
        bookAppointment(list.get(pick - 1));
    }

    private void todayAndCheckIn() throws AccessDeniedException, InvalidInputException {
        LocalDateTime now = LocalDateTime.now();
        ArrayList<Appointment> list = appointments.onDay(currentUser, now.toLocalDate(), now);
        printAppointments("TODAY'S APPOINTMENTS (" + now.format(DAY) + ")", list);
        ArrayList<Appointment> open = new ArrayList<>();
        for (Appointment a : list) if (a.getStatus() == AppointmentStatus.SCHEDULED) open.add(a);
        if (open.isEmpty()) return;
        if (!in.askYesNo("Check in a patient? (Y/N): ")) return;
        String id = in.ask("Appointment ID: ", Validator::validateAppointmentId);
        Appointment a = appointments.findById(id);
        if (a == null) throw new InvalidInputException("No appointment has the ID " + id + ".");
        PriorityCategory[] cats = PriorityCategory.values();
        System.out.println("  Priority category recorded by staff:");
        for (int i = 0; i < cats.length; i++) {
            System.out.println("  " + (i + 1) + ". " + (cats[i].isPriority() ? pretty(cats[i]) : "Regular"));
        }
        PriorityCategory priority = cats[in.askChoice("Priority category: ", 1, cats.length) - 1];
        Visit v = appointments.checkIn(currentUser, a.getAppointmentId(), priority, LocalDateTime.now());
        System.out.println("\n  Checked in. " + a.getAppointmentId() + " is now Completed.");
        System.out.println("  Visit ID     : " + v.getVisitId() + "  (" + pretty(v.getVisitType()) + ")");
        System.out.println("  QUEUE NUMBER : " + v.getQueueNumber());
        System.out.println("  Position     : " + visits.positionOf(v));
    }

    private void rescheduleAppointment() throws AccessDeniedException, InvalidInputException {
        header("RESCHEDULE AN APPOINTMENT");
        String id = in.ask("Appointment ID: ", Validator::validateAppointmentId);
        Appointment a = appointments.findById(id);
        if (a == null) throw new InvalidInputException("No appointment has the ID " + id + ".");
        printAppointmentCard(a);
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new InvalidInputException(a.getAppointmentId() + " is " + pretty(a.getStatus()) + " and cannot be rescheduled.");
        }
        while (true) {
            LocalDateTime when = askDateTime("New");
            try {
                appointments.reschedule(currentUser, a.getAppointmentId(), when, LocalDateTime.now());
                System.out.println("\n  Rescheduled (removed from the old slot and added to the new one).");
                printAppointmentCard(a);
                return;
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
                if (!in.askYesNo("Try another date and time? (Y/N): ")) return;
            }
        }
    }

    private void cancelAppointment() throws AccessDeniedException, InvalidInputException {
        header("CANCEL AN APPOINTMENT");
        String id = in.ask("Appointment ID: ", Validator::validateAppointmentId);
        Appointment a = appointments.findById(id);
        if (a == null) throw new InvalidInputException("No appointment has the ID " + id + ".");
        printAppointmentCard(a);
        if (!in.askYesNo("Cancel this appointment? (Y/N): ")) return;
        appointments.cancel(currentUser, a.getAppointmentId(), LocalDateTime.now());
        System.out.println("  Cancelled. It stays in the records with the status Cancelled.");
    }

    private void printAppointments(String title, ArrayList<Appointment> list) {
        header(title);
        if (list.isEmpty()) {
            System.out.println("  None.");
            return;
        }
        System.out.printf("  %-6s %-15s %-6s %-5s %-28s %-10s %s%n", "ID", "Date", "Time", "Svc", "Patient", "Status", "Purpose");
        for (Appointment a : list) {
            Patient p = patients.findById(a.getPatientId());
            String name = p == null ? a.getPatientId() : p.getFullName();
            System.out.printf("  %-6s %-15s %-6s %-5s %-28s %-10s %s%n", a.getAppointmentId(), a.getDateTime().format(DAY),
                    a.getDateTime().toLocalTime(), a.getService().getPrefix(), cut(name, 27), pretty(a.getStatus()),
                    cut(a.getPurpose(), 34));
        }
        System.out.println("  " + list.size() + " appointment(s).");
    }

    private void printAppointmentCard(Appointment a) {
        Patient p = patients.findById(a.getPatientId());
        System.out.println("  Appointment ID : " + a.getAppointmentId());
        System.out.println("  Patient        : " + a.getPatientId() + "  " + (p == null ? "" : p.getFullName()));
        System.out.println("  Service        : " + a.getService().getLabel());
        System.out.println("  Date and time  : " + a.getDateTime().format(DAY) + " at " + a.getDateTime().toLocalTime());
        System.out.println("  Purpose        : " + a.getPurpose());
        if (a.isFollowUp()) System.out.println("  Follow-up of   : " + a.getLinkedVisitId());
        System.out.println("  Status         : " + pretty(a.getStatus())
                + (a.getRescheduleCount() > 0 ? "   (rescheduled " + a.getRescheduleCount() + "x)" : ""));
        if (!a.getCheckInVisitId().isEmpty()) System.out.println("  Checked in as  : " + a.getCheckInVisitId());
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
        Patient p = patients.findById(id);
        RecordVersion last = records.latestChange(p.getPatientId());
        System.out.println("Record version : " + records.currentVersion(p.getPatientId())
                + (last == null ? " (not changed since registration)"
                : " (last changed by " + last.getChangedBy() + " on " + last.getChangedAt().format(STAMP) + ")"));
        System.out.println("Appointments   : " + appointments.upcomingCountOf(p.getPatientId(), LocalDateTime.now()) + " upcoming");
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

    /** Audit log with filters (Milestone 5). Each filter walks the linked list once: O(n). */
    private void viewAuditLog() throws AccessDeniedException, InvalidInputException {
        auth.require(currentUser, Permission.VIEW_AUDIT_LOG);
        while (true) {
            header("AUDIT LOG  (" + auditLog.size() + " entries)");
            System.out.println("  1. Latest 25 entries");
            System.out.println("  2. Entries by user");
            System.out.println("  3. Entries about one record (patient, visit, or appointment ID)");
            System.out.println("  4. Entries by action (e.g. UPDATE, APPOINTMENT, LOGIN)");
            System.out.println("  5. All entries");
            System.out.println("  0. Back");
            int pick = in.askChoice("Choose: ", 0, 5);
            if (pick == 0) return;
            ArrayList<AuditEntry> list;
            String title;
            if (pick == 1) {
                list = auditLog.last(25);
                title = "latest 25";
            } else if (pick == 2) {
                String u = in.ask("Username: ", Validator::validateUsername);
                list = auditLog.byUser(u);
                title = "user " + u;
            } else if (pick == 3) {
                String id = in.ask("Record ID (P0001, V0001, or A0001): ", s -> {
                    String t = s == null ? "" : s.trim().toUpperCase();
                    if (t.startsWith("V")) return Validator.validateVisitId(t);
                    if (t.startsWith("A")) return Validator.validateAppointmentId(t);
                    return Validator.validatePatientId(t);
                });
                list = auditLog.byTarget(id);
                title = "record " + id;
            } else if (pick == 4) {
                String a = in.ask("Action contains: ", s -> {
                    String t = s == null ? "" : s.trim();
                    if (t.length() < 3) throw new InvalidInputException("Type at least 3 letters.");
                    for (int i = 0; i < t.length(); i++) {
                        char c = t.charAt(i);
                        if (!Character.isLetter(c) && c != '_') throw new InvalidInputException("Use letters and _ only.");
                    }
                    return t.toUpperCase();
                });
                list = auditLog.byAction(a);
                title = "action " + a;
            } else {
                list = auditLog.toList();
                title = "all";
            }
            System.out.println();
            System.out.println("  " + list.size() + " entr" + (list.size() == 1 ? "y" : "ies") + " (" + title + "), oldest first:");
            for (AuditEntry e : list) System.out.println("  " + e);
        }
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
