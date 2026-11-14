package medflow;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.io.Csv;
import medflow.io.FileHandler;
import medflow.io.SampleData;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.ConsultOutcome;
import medflow.model.Permission;
import medflow.model.PriorityCategory;
import medflow.model.Service;
import medflow.model.Sex;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitStatus;
import medflow.model.VisitType;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.service.VisitService;
import medflow.util.Validator;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;

/**
 * Test cases for Milestones 1 to 4 (login, registration, visits and queues, consultation and outcome). Run with:
 *   java -cp out medflow.MedFlowTests
 * Each line shows PASS or FAIL. The program ends with a summary.
 */
public class MedFlowTests {

    private static int passed = 0, failed = 0, number = 0;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private interface Check { Object run() throws Exception; }

    /** The check must return the expected value. */
    private static void expect(String name, Object expected, Check check) {
        number++;
        try {
            Object actual = check.run();
            if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) pass(name, "-> " + actual);
            else fail(name, "expected " + expected + " but got " + actual);
        } catch (Exception e) {
            fail(name, "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The check must throw the given exception type. */
    private static void expectError(String name, Class<? extends Exception> type, Check check) {
        number++;
        try {
            Object actual = check.run();
            fail(name, "expected " + type.getSimpleName() + " but got " + actual);
        } catch (Exception e) {
            if (type.isInstance(e)) pass(name, "-> rejected: " + e.getMessage());
            else fail(name, "expected " + type.getSimpleName() + " but got " + e.getClass().getSimpleName());
        }
    }

    private static void pass(String name, String detail) {
        passed++;
        System.out.printf("PASS  %3d  %-58s %s%n", number, name, detail);
    }

    private static void fail(String name, String detail) {
        failed++;
        System.out.printf("FAIL  %3d  %-58s %s%n", number, name, detail);
    }

    private static void section(String title) { System.out.println("\n--- " + title + " ---"); }

    public static void main(String[] args) throws Exception {
        System.out.println("MedFlow test results (Milestones 1 to 4), test date used for ages in registration tests: " + TODAY);

        // ------------------------------------------------------------ names
        section("Input validation: names");
        expect("Valid name with period", "Ma. Clara", () -> Validator.validateName("Ma. Clara", "First name", true));
        expect("Valid name with hyphen", "Dela Cruz-Santos", () -> Validator.validateName("Dela Cruz-Santos", "Last name", true));
        expect("Valid name with apostrophe", "O'Brien", () -> Validator.validateName("O'Brien", "Last name", true));
        expect("Valid name with n tilde", "Peña", () -> Validator.validateName("Peña", "Last name", true));
        expect("Extra spaces are removed", "Dela Cruz", () -> Validator.validateName("  Dela   Cruz ", "Last name", true));
        expect("All-lowercase name is capitalized", "Dela Cruz", () -> Validator.validateName("dela cruz", "Last name", true));
        expect("All-uppercase name is capitalized", "Juan Pablo", () -> Validator.validateName("JUAN PABLO", "First name", true));
        expect("Mixed-case name kept as typed", "McArthur", () -> Validator.validateName("McArthur", "Last name", true));
        expect("Empty optional middle name allowed", "", () -> Validator.validateName("", "Middle name", false));
        expectError("Number in name", InvalidInputException.class, () -> Validator.validateName("Juan2", "First name", true));
        expectError("Empty required name", InvalidInputException.class, () -> Validator.validateName("   ", "Last name", true));
        expectError("Symbol @ in name", InvalidInputException.class, () -> Validator.validateName("J@n", "First name", true));
        expectError("Name starting with hyphen", InvalidInputException.class, () -> Validator.validateName("-Juan", "First name", true));
        expectError("Two symbols in a row", InvalidInputException.class, () -> Validator.validateName("Juan--Pedro", "First name", true));
        expectError("Name over 50 characters", InvalidInputException.class, () -> Validator.validateName("A".repeat(51), "Last name", true));

        // ------------------------------------------------------------ birthdates
        section("Input validation: birthdates");
        expect("Valid birthdate", LocalDate.of(2005, 3, 14), () -> Validator.validateBirthdate("2005-03-14", TODAY));
        expect("Leap day in a leap year", LocalDate.of(2024, 2, 29), () -> Validator.validateBirthdate("2024-02-29", TODAY));
        expect("Birthdate today (newborn)", TODAY, () -> Validator.validateBirthdate("2026-11-08", TODAY));
        expectError("Slashes instead of dashes", InvalidInputException.class, () -> Validator.validateBirthdate("2005/03/14", TODAY));
        expectError("Day-month-year order", InvalidInputException.class, () -> Validator.validateBirthdate("14-03-2005", TODAY));
        expectError("February 30", InvalidInputException.class, () -> Validator.validateBirthdate("2005-02-30", TODAY));
        expectError("Leap day in a non-leap year", InvalidInputException.class, () -> Validator.validateBirthdate("2023-02-29", TODAY));
        expectError("Month 13", InvalidInputException.class, () -> Validator.validateBirthdate("2005-13-01", TODAY));
        expectError("Future birthdate", InvalidInputException.class, () -> Validator.validateBirthdate("2027-01-01", TODAY));
        expectError("Age above 120", InvalidInputException.class, () -> Validator.validateBirthdate("1890-01-01", TODAY));
        expectError("Letters in birthdate", InvalidInputException.class, () -> Validator.validateBirthdate("20O5-03-14", TODAY));

        // ------------------------------------------------------------ other fields
        section("Input validation: contact number, sex, address, menu choice");
        expect("Valid contact number", "09171234567", () -> Validator.validateContactNumber("09171234567"));
        expect("Dashes removed", "09171234567", () -> Validator.validateContactNumber("0917-123-4567"));
        expect("Spaces removed", "09171234567", () -> Validator.validateContactNumber("0917 123 4567"));
        expectError("Only 10 digits", InvalidInputException.class, () -> Validator.validateContactNumber("9171234567"));
        expectError("Does not start with 09", InvalidInputException.class, () -> Validator.validateContactNumber("08171234567"));
        expectError("Letter in contact number", InvalidInputException.class, () -> Validator.validateContactNumber("0917123456a"));
        expectError("+63 format not accepted", InvalidInputException.class, () -> Validator.validateContactNumber("+639171234567"));
        expect("Sex m", Sex.MALE, () -> Validator.validateSex("m"));
        expect("Sex Female", Sex.FEMALE, () -> Validator.validateSex("Female"));
        expectError("Sex X", InvalidInputException.class, () -> Validator.validateSex("X"));
        expect("Valid address", "Blk 5 Lot 3, Brgy. Holy Spirit, QC", () -> Validator.validateAddress("Blk 5 Lot 3, Brgy. Holy Spirit, QC"));
        expectError("Address with only digits", InvalidInputException.class, () -> Validator.validateAddress("12345"));
        expectError("Address with a symbol", InvalidInputException.class, () -> Validator.validateAddress("12 Mabini St. @ QC"));
        expect("Patient ID typed in lowercase", "P0007", () -> Validator.validatePatientId("p0007"));
        expectError("Patient ID with a letter", InvalidInputException.class, () -> Validator.validatePatientId("P00A7"));
        expect("Menu choice in range", 3, () -> Validator.validateMenuChoice("3", 0, 5));
        expectError("Menu choice out of range", InvalidInputException.class, () -> Validator.validateMenuChoice("6", 0, 5));
        expectError("Menu choice not a number", InvalidInputException.class, () -> Validator.validateMenuChoice("a", 0, 5));
        expectError("Negative menu choice", InvalidInputException.class, () -> Validator.validateMenuChoice("-1", 0, 5));

        // ------------------------------------------------------------ age rules
        section("Age rules per service");
        expect("Pediatrics accepts age 17", true, () -> Service.PED.acceptsAge(17));
        expect("Pediatrics rejects age 18", false, () -> Service.PED.acceptsAge(18));
        expect("Internal Medicine rejects age 17", false, () -> Service.IM.acceptsAge(17));
        expect("Internal Medicine accepts age 18", true, () -> Service.IM.acceptsAge(18));
        expect("Family Medicine accepts age 0", true, () -> Service.FM.acceptsAge(0));
        expect("Family Medicine accepts age 95", true, () -> Service.FM.acceptsAge(95));
        expect("Eligible services for a 10-year-old", "Family Medicine, Pediatrics", () -> Validator.eligibleServices(10));
        expect("Eligible services for a 30-year-old", "Family Medicine, Internal Medicine", () -> Validator.eligibleServices(30));
        expectError("Age rule message for IM at age 12", InvalidInputException.class, () -> { Validator.checkServiceEligibility(12, Service.IM); return null; });

        // ------------------------------------------------------------ login and permissions
        section("Login and role-based permissions");
        AuditLog log = new AuditLog();
        AuthService auth = new AuthService(log);
        PatientService ps = new PatientService(auth, log);
        SampleData.load(auth, ps);
        User reception = auth.login("reception", "rec123");
        User nurse = auth.login("nurse", "nur123");
        User records = auth.login("records", "rec456");
        User admin = auth.login("admin", "adm123");
        expect("Correct login returns the user", "reception", () -> reception.getUsername());
        expect("Username is not case-sensitive", "nurse", () -> auth.login("NURSE", "nur123").getUsername());
        expect("Wrong password returns null", null, () -> auth.login("reception", "wrong"));
        expect("Unknown username returns null", null, () -> auth.login("ghost", "rec123"));
        expect("Receptionist can register patients", true, () -> auth.can(reception, Permission.REGISTER_PATIENT));
        expect("Receptionist cannot resolve duplicates", false, () -> auth.can(reception, Permission.RESOLVE_DUPLICATES));
        expect("Records staff can resolve duplicates", true, () -> auth.can(records, Permission.RESOLVE_DUPLICATES));
        expect("Nurse can view clinical notes", true, () -> auth.can(nurse, Permission.VIEW_CLINICAL_NOTES));
        expect("Records staff cannot view clinical notes", false, () -> auth.can(records, Permission.VIEW_CLINICAL_NOTES));
        expectError("Nurse tries to register a patient", AccessDeniedException.class, () -> ps.registerNew(nurse, "Lopez", "Ben", "",
                LocalDate.of(2000, 1, 1), Sex.MALE, "1 Rizal St., Manila", "09170000000"));
        expectError("Receptionist tries to open duplicate review", AccessDeniedException.class, () -> ps.getPendingFlags(reception));
        expectError("Receptionist tries to add a user", AccessDeniedException.class, () -> auth.addUser(reception, "newuser", "abc123", "New User", medflow.model.Role.NURSE));
        expect("Admin adds a user", "clinic2", () -> auth.addUser(admin, "clinic2", "abc123", "Rosa Diaz", medflow.model.Role.CLINICIAN).getUsername());
        expectError("Admin adds a taken username", InvalidInputException.class, () -> auth.addUser(admin, "Nurse", "abc123", "Copy Cat", medflow.model.Role.NURSE));
        expect("Receptionist view hides clinical notes", true, () -> ps.viewRecord(reception, "P0001").contains("hidden for the Receptionist role"));
        expect("Nurse view shows allergies", true, () -> ps.viewRecord(nurse, "P0001").contains("Penicillin"));

        // ------------------------------------------------------------ registration and duplicates
        section("Registration and duplicate check");
        LocalDate juanBirth = LocalDate.of(1988, 4, 12);
        expect("Exact name and birthdate finds P0001", 1, () -> ps.findPossibleDuplicates("Dela Cruz", "Juan", juanBirth).size());
        expect("Uppercase name still matches", 1, () -> ps.findPossibleDuplicates("DELA CRUZ", "JUAN", juanBirth).size());
        expect("\"De La Cruz\" spacing still matches", 1, () -> ps.findPossibleDuplicates("De La Cruz", "Juan", juanBirth).size());
        expect("\"Dela-Cruz\" with hyphen still matches", 1, () -> ps.findPossibleDuplicates("Dela-Cruz", "Juan", juanBirth).size());
        expect("Different birthdate does not match", 0, () -> ps.findPossibleDuplicates("Dela Cruz", "Juan", LocalDate.of(1988, 4, 13)).size());
        expect("Different first name does not match", 0, () -> ps.findPossibleDuplicates("Dela Cruz", "Pedro", juanBirth).size());

        Patient newJuan = ps.registerNew(reception, "Dela Cruz", "Juan", "", juanBirth, Sex.MALE, "12 Mabini St., QC", "09171234567");
        expect("New ID follows the last sample ID", "P0009", newJuan::getPatientId);
        expect("Registering a possible duplicate creates 1 pending flag", 1, () -> ps.getPendingFlags(records).size());
        expect("Flag points to P0009 and P0001", "P0009>P0001", () -> {
            DuplicateFlag f = ps.getPendingFlags(records).get(0);
            return f.getNewPatientId() + ">" + f.getExistingPatientId();
        });
        expect("Using the existing record returns P0001", "P0001", () -> ps.useExisting(reception, "p0001").getPatientId());

        DuplicateFlag flag = ps.getPendingFlags(records).get(0);
        ps.merge(records, flag);
        expect("After merge, P0009 points to P0001", "P0001", () -> ps.findById("P0009").getMergedInto());
        expect("After merge, the old record is kept (not deleted)", true, () -> ps.findById("P0009") != null);
        expect("After merge, only P0001 is a possible duplicate", 1, () -> ps.findPossibleDuplicates("Dela Cruz", "Juan", juanBirth).size());
        expect("After merge, no pending flags", 0, () -> ps.getPendingFlags(records).size());
        expect("Using merged P0009 redirects to P0001", "P0001", () -> ps.useExisting(reception, "P0009").getPatientId());
        expectError("Merging the same flag twice", InvalidInputException.class, () -> { ps.merge(records, flag); return null; });

        Patient twin = ps.registerNew(reception, "Reyes", "Maria Clara", "", LocalDate.of(1995, 9, 30), Sex.FEMALE,
                "9 Other St., Cebu City", "09280000000");
        DuplicateFlag f2 = ps.getPendingFlags(records).get(0);
        ps.keepBoth(records, f2);
        expect("Keep both: both records still active", 2, () -> ps.findPossibleDuplicates("Reyes", "Maria Clara", LocalDate.of(1995, 9, 30)).size());
        expect("Keep both: flag status is KEPT_BOTH", DuplicateFlag.Status.KEPT_BOTH, f2::getStatus);
        expect("Partial name search \"dela\" (merged record skipped)", 1, () -> ps.searchByName(reception, "dela").size());
        expect("Search by first and last name \"maria clara reyes\"", 2, () -> ps.searchByName(reception, "maria clara reyes").size());

        // ------------------------------------------------------------ IDs and HashMap lookups
        section("Unique IDs and HashMap lookup (1,000 registrations)");
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Patient p = ps.registerNew(reception, "Test", "Patient " + toLetters(i), "", LocalDate.of(1990, 1, 1).plusDays(i),
                    Sex.FEMALE, "100 Test St., Manila", "09170000000");
            ids.add(p.getPatientId());
        }
        expect("1,000 new patients get 1,000 unique IDs", 1000, ids::size);
        expect("Every new ID is found by HashMap lookup", true, () -> {
            for (String id : ids) if (ps.findById(id) == null) return false;
            return true;
        });
        expect("Lookup of an unknown ID returns null", null, () -> ps.findById("P9999"));

        // ------------------------------------------------------------ audit log
        section("Audit log (hand-coded linked list)");
        AuditLog small = new AuditLog();
        small.add(new AuditEntry(LocalDateTime.of(2026, 11, 8, 9, 0), "a", "ONE", "-", ""));
        small.add(new AuditEntry(LocalDateTime.of(2026, 11, 8, 9, 1), "b", "TWO", "-", ""));
        small.add(new AuditEntry(LocalDateTime.of(2026, 11, 8, 9, 2), "c", "THREE", "-", ""));
        expect("Size after 3 adds", 3, small::size);
        expect("Entries stay in order (oldest first)", "ONE,TWO,THREE", () ->
                small.toList().get(0).getAction() + "," + small.toList().get(1).getAction() + "," + small.toList().get(2).getAction());
        expect("last(2) returns the 2 newest", "TWO,THREE", () -> small.last(2).get(0).getAction() + "," + small.last(2).get(1).getAction());
        expect("Registration and merge actions were logged", true, () -> {
            boolean reg = false, flg = false, mrg = false;
            for (AuditEntry e : log.toList()) {
                if (e.getAction().equals("REGISTER_PATIENT")) reg = true;
                if (e.getAction().equals("FLAG_DUPLICATE")) flg = true;
                if (e.getAction().equals("MERGE_RECORDS")) mrg = true;
            }
            return reg && flg && mrg;
        });
        expect("Denied actions were logged", true, () -> {
            for (AuditEntry e : log.toList()) if (e.getAction().equals("ACCESS_DENIED")) return true;
            return false;
        });

        // ------------------------------------------------------------ visits and queues (Milestone 3)
        section("Visit creation, queue numbers, and priority lane");
        VisitService vs = new VisitService(auth, ps, log);
        User doctor = auth.login("doctor", "doc123");
        Visit vJuan = vs.createVisit(reception, "P0001", Service.FM, "Fever and cough for 3 days", VisitType.WALK_IN, PriorityCategory.REGULAR);
        expect("First Family Medicine visit gets FM-001", "FM-001", vJuan::getQueueNumber);
        expect("New visit has status WAITING", VisitStatus.WAITING, vJuan::getStatus);
        Visit vMaria = vs.createVisit(reception, "P0002", Service.FM, "Headache", VisitType.WALK_IN, PriorityCategory.REGULAR);
        expect("Second Family Medicine visit gets FM-002", "FM-002", vMaria::getQueueNumber);
        Visit vLourdes = vs.createVisit(reception, "P0004", Service.IM, "Blood sugar check", VisitType.SCHEDULED, PriorityCategory.REGULAR);
        expect("Internal Medicine has its own counter: IM-001", "IM-001", vLourdes::getQueueNumber);
        Visit vMiguel = vs.createVisit(reception, "P0003", Service.PED, "Asthma check", VisitType.FOLLOW_UP, PriorityCategory.REGULAR);
        expect("Pediatrics has its own counter: PED-001", "PED-001", vMiguel::getQueueNumber);
        Visit vAna = vs.createVisit(reception, "P0006", Service.FM, "Dizziness", VisitType.WALK_IN, PriorityCategory.PRIORITY);
        expect("Priority visit gets the next number FM-003", "FM-003", vAna::getQueueNumber);
        expect("Priority patient is at position 1", 1, () -> vs.positionOf(vAna));
        expect("Regular patient waits behind the priority lane", 2, () -> vs.positionOf(vJuan));
        expect("Second regular patient is at position 3", 3, () -> vs.positionOf(vMaria));
        expectError("Adult cannot join Pediatrics", InvalidInputException.class, () -> vs.createVisit(reception, "P0001", Service.PED, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expectError("Child cannot join Internal Medicine", InvalidInputException.class, () -> vs.createVisit(reception, "P0003", Service.IM, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expectError("Second active visit in the same service", InvalidInputException.class, () -> vs.createVisit(reception, "P0001", Service.FM, "Again", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expectError("Visit for an unknown patient", InvalidInputException.class, () -> vs.createVisit(reception, "P9998", Service.FM, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expectError("Visit for a merged record is refused", InvalidInputException.class, () -> vs.createVisit(reception, "P0009", Service.FM, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expectError("Nurse cannot create a visit", AccessDeniedException.class, () -> vs.createVisit(nurse, "P0007", Service.FM, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR));
        expect("Family Medicine has 3 waiting", 3, () -> vs.waitingCount(Service.FM));

        VisitService bulk = new VisitService(auth, ps, log);
        HashSet<String> numbers = new HashSet<>();
        int made = 0;
        for (String id : ids) {
            if (made == 150) break;
            numbers.add(bulk.createVisit(reception, id, Service.FM, "Check-up", VisitType.WALK_IN, PriorityCategory.REGULAR).getQueueNumber());
            made++;
        }
        expect("150 visits get 150 different queue numbers (HashSet)", 150, numbers::size);

        // ------------------------------------------------------------ calling patients (Milestone 3 and 4)
        section("Calling patients");
        expectError("Receptionist cannot call patients", AccessDeniedException.class, () -> vs.callNext(reception, Service.FM));
        expect("Call next takes the priority lane first (FM-003)", "FM-003", () -> vs.callNext(nurse, Service.FM).getQueueNumber());
        expect("A called patient is still WAITING", VisitStatus.WAITING, vAna::getStatus);
        expect("Call time is saved when called", true, () -> vAna.getCalledTime() != null);
        expect("Now calling shows FM-003", "FM-003", () -> vs.getNowCalling(Service.FM).getQueueNumber());
        expectError("Cannot call next while someone is being called", InvalidInputException.class, () -> vs.callNext(nurse, Service.FM));
        expect("First miss: still WAITING, missed count 1", "WAITING/1", () -> {
            vs.markMissed(nurse, Service.FM);
            return vAna.getStatus() + "/" + vAna.getMissedCount();
        });
        expect("Priority patient who missed is called again before regular patients", "FM-003", () -> vs.callNext(nurse, Service.FM).getQueueNumber());
        expect("Second miss: status NO_SHOW", VisitStatus.NO_SHOW, () -> vs.markMissed(nurse, Service.FM).getStatus());
        expectError("Mark missed when nobody is being called", InvalidInputException.class, () -> vs.markMissed(nurse, Service.FM));
        expect("Next call is the first regular patient FM-001", "FM-001", () -> vs.callNext(nurse, Service.FM).getQueueNumber());

        section("Consultation tracking (Milestone 4)");
        expect("Patient came: status IN_CONSULTATION", VisitStatus.IN_CONSULTATION, () -> vs.markArrived(nurse, Service.FM).getStatus());
        expect("Consultation start time is saved", true, () -> vJuan.getConsultStart() != null);
        expect("Waiting time is not negative", true, () -> vJuan.getWaitingMinutes() >= 0);
        expect("1 patient in consultation (Family Medicine)", 1, () -> vs.getInConsultation(Service.FM).size());
        expect("Next call is FM-002", "FM-002", () -> vs.callNext(nurse, Service.FM).getQueueNumber());
        vs.markArrived(nurse, Service.FM);
        expect("2 patients in consultation, in start order", "FM-001,FM-002", () ->
                vs.getInConsultation(Service.FM).get(0).getQueueNumber() + "," + vs.getInConsultation(Service.FM).get(1).getQueueNumber());
        expect("No one left waiting in Family Medicine", 0, () -> vs.waitingCount(Service.FM));
        expect("Call next with an empty queue returns null", null, () -> vs.callNext(nurse, Service.FM));
        expectError("Cannot cancel a visit that is in consultation", InvalidInputException.class, () -> { vs.cancel(nurse, vJuan); return null; });
        vs.cancel(nurse, vLourdes);
        expect("Cancelled waiting visit has status CANCELLED", VisitStatus.CANCELLED, vLourdes::getStatus);
        expect("Cancelled visit left the Internal Medicine line", 0, () -> vs.waitingCount(Service.IM));
        expect("Status counts (FM): 2 in consultation, 1 no-show", "2/1", () -> {
            java.util.EnumMap<VisitStatus, Integer> c = vs.statusCounts(admin, Service.FM);
            return c.get(VisitStatus.IN_CONSULTATION) + "/" + c.get(VisitStatus.NO_SHOW);
        });

        section("Visit status rules");
        expect("WAITING to IN_CONSULTATION is allowed", true, () -> VisitService.canChange(VisitStatus.WAITING, VisitStatus.IN_CONSULTATION));
        expect("IN_CONSULTATION to COMPLETED is allowed", true, () -> VisitService.canChange(VisitStatus.IN_CONSULTATION, VisitStatus.COMPLETED));
        expect("IN_CONSULTATION to REFERRED is allowed", true, () -> VisitService.canChange(VisitStatus.IN_CONSULTATION, VisitStatus.REFERRED));
        expect("WAITING to COMPLETED is not allowed", false, () -> VisitService.canChange(VisitStatus.WAITING, VisitStatus.COMPLETED));
        expect("IN_CONSULTATION to NO_SHOW is not allowed", false, () -> VisitService.canChange(VisitStatus.IN_CONSULTATION, VisitStatus.NO_SHOW));
        expect("COMPLETED to WAITING is not allowed", false, () -> VisitService.canChange(VisitStatus.COMPLETED, VisitStatus.WAITING));
        expect("CANCELLED is final", false, () -> VisitService.canChange(VisitStatus.CANCELLED, VisitStatus.WAITING));
        expect("NO_SHOW is final", false, () -> VisitService.canChange(VisitStatus.NO_SHOW, VisitStatus.WAITING));

        section("Recording the outcome (Milestone 4)");
        expectError("Nurse cannot record an outcome", AccessDeniedException.class, () -> vs.recordOutcome(nurse, vJuan.getVisitId(), ConsultOutcome.TREATED, "Rest and fluids"));
        expectError("Receptionist cannot record an outcome", AccessDeniedException.class, () -> vs.recordOutcome(reception, vJuan.getVisitId(), ConsultOutcome.TREATED, "Rest and fluids"));
        expectError("Outcome for a waiting visit is refused", InvalidInputException.class, () -> vs.recordOutcome(doctor, vMiguel.getVisitId(), ConsultOutcome.TREATED, "Rest and fluids"));
        expectError("Outcome for an unknown visit ID", InvalidInputException.class, () -> vs.recordOutcome(doctor, "V9999", ConsultOutcome.TREATED, "Rest and fluids"));
        expectError("Empty outcome note", InvalidInputException.class, () -> vs.recordOutcome(doctor, vJuan.getVisitId(), ConsultOutcome.TREATED, "   "));
        expectError("Outcome note with the symbol @", InvalidInputException.class, () -> vs.recordOutcome(doctor, vJuan.getVisitId(), ConsultOutcome.TREATED, "See me @ clinic"));
        expectError("Outcome note over 200 characters", InvalidInputException.class, () -> vs.recordOutcome(doctor, vJuan.getVisitId(), ConsultOutcome.TREATED, "a".repeat(201)));
        expectError("No outcome type chosen", InvalidInputException.class, () -> vs.recordOutcome(doctor, vJuan.getVisitId(), null, "Rest and fluids"));
        expect("Rejected outcomes leave the visit IN_CONSULTATION", VisitStatus.IN_CONSULTATION, vJuan::getStatus);

        Visit doneJuan = vs.recordOutcome(doctor, vJuan.getVisitId(), ConsultOutcome.TREATED, "Viral infection, rest, fluids, and follow up if fever lasts 3 days");
        expect("TREATED sets status COMPLETED", VisitStatus.COMPLETED, doneJuan::getStatus);
        expect("Outcome type is saved", ConsultOutcome.TREATED, doneJuan::getOutcomeType);
        expect("Clinician username is saved", "doctor", doneJuan::getConsultedBy);
        expect("Consultation end time is saved", true, () -> doneJuan.getConsultEnd() != null);
        expect("Consultation minutes are not negative", true, () -> doneJuan.getConsultMinutes() >= 0);
        expect("Finished visit left the consultations in progress", 1, () -> vs.getInConsultation(Service.FM).size());
        expectError("Outcome cannot be recorded twice", InvalidInputException.class, () -> vs.recordOutcome(doctor, vJuan.getVisitId(), ConsultOutcome.TREATED, "Again"));
        Visit doneMaria = vs.recordOutcome(doctor, vMaria.getVisitId(), ConsultOutcome.REFERRED, "Referred to the neurology clinic for recurring headache");
        expect("REFERRED sets status REFERRED", VisitStatus.REFERRED, doneMaria::getStatus);
        expect("Nobody is in consultation now", 0, () -> vs.getAllInConsultation().size());
        expect("Patient with a finished visit can get a new visit", "FM-004", () ->
                vs.createVisit(reception, "P0001", Service.FM, "Follow-up check", VisitType.FOLLOW_UP, PriorityCategory.REGULAR).getQueueNumber());
        expect("FOLLOW_UP outcome ends as COMPLETED", VisitStatus.COMPLETED, ConsultOutcome.FOLLOW_UP::getFinalStatus);

        section("Visit status board and tracking (Milestone 4)");
        expect("Receptionist can view visit status", true, () -> auth.can(reception, Permission.VIEW_VISIT_STATUS));
        expect("Records staff cannot view visit status", false, () -> auth.can(records, Permission.VIEW_VISIT_STATUS));
        expect("Only the clinician can record outcomes", "false/false/true", () ->
                auth.can(reception, Permission.RECORD_CONSULTATION) + "/" + auth.can(nurse, Permission.RECORD_CONSULTATION) + "/" + auth.can(doctor, Permission.RECORD_CONSULTATION));
        expectError("Records staff cannot open the status board", AccessDeniedException.class, () -> vs.statusCounts(records, Service.FM));
        expect("Status counts (FM): 1 completed, 1 referred, 1 waiting", "1/1/1", () -> {
            java.util.EnumMap<VisitStatus, Integer> c = vs.statusCounts(reception, Service.FM);
            return c.get(VisitStatus.COMPLETED) + "/" + c.get(VisitStatus.REFERRED) + "/" + c.get(VisitStatus.WAITING);
        });
        expect("Track a visit by ID", "FM-001", () -> vs.trackVisit(nurse, vJuan.getVisitId()).getQueueNumber());
        expectError("Track an unknown visit ID", InvalidInputException.class, () -> vs.trackVisit(nurse, "V9999"));
        expect("Track all visits of P0001 (2 visits, newest first)", "FM-004,FM-001", () -> {
            java.util.ArrayList<Visit> list = vs.trackPatientVisits(nurse, "P0001");
            return list.get(0).getQueueNumber() + "," + list.get(1).getQueueNumber();
        });
        expectError("Track visits of an unknown patient", InvalidInputException.class, () -> vs.trackPatientVisits(nurse, "P9998"));
        expect("Average consultation time (FM) is not negative", true, () -> vs.averageConsultMinutes(Service.FM) >= 0);
        expect("Average waiting time is not negative", true, () -> vs.averageWaitingMinutes(Service.FM) >= 0);
        expect("Average consult time is -1 when none has finished (IM)", -1.0, () -> vs.averageConsultMinutes(Service.IM));
        Visit timing = new Visit("V9000", "P0001", Service.FM, "Test", VisitType.WALK_IN, LocalDateTime.of(2026, 11, 14, 9, 0));
        expect("Waiting and consultation minutes are -1 before they start", "-1/-1", () -> timing.getWaitingMinutes() + "/" + timing.getConsultMinutes());
        timing.setConsultStart(LocalDateTime.of(2026, 11, 14, 9, 18));
        timing.setConsultEnd(LocalDateTime.of(2026, 11, 14, 9, 35));
        expect("Waiting 18 min and consultation 17 min", "18/17", () -> timing.getWaitingMinutes() + "/" + timing.getConsultMinutes());
        expect("Visit ID typed in lowercase", "V0003", () -> Validator.validateVisitId("v0003"));
        expectError("Visit ID that is too short", InvalidInputException.class, () -> Validator.validateVisitId("V03"));
        expectError("Visit ID with a letter", InvalidInputException.class, () -> Validator.validateVisitId("V00A3"));
        expect("Calling, consultation, and outcome were logged", true, () -> {
            boolean call = false, start = false, outcome = false, noShow = false;
            for (AuditEntry e : log.toList()) {
                if (e.getAction().equals("CALL_PATIENT")) call = true;
                if (e.getAction().equals("START_CONSULT")) start = true;
                if (e.getAction().equals("RECORD_OUTCOME")) outcome = true;
                if (e.getAction().equals("NO_SHOW")) noShow = true;
            }
            return call && start && outcome && noShow;
        });

        // ------------------------------------------------------------ files
        section("Saving and loading .csv files");
        expect("CSV keeps commas and quotes", "Blk 5, \"Unit A\"", () -> Csv.split(Csv.join("x", "Blk 5, \"Unit A\"")).get(1));
        File folder = Files.createTempDirectory("medflow-test").toFile();
        FileHandler fh = new FileHandler(folder.getPath());
        vs.createVisit(reception, "P0007", Service.IM, "Chest pain follow-up", VisitType.WALK_IN, PriorityCategory.REGULAR);
        vs.callNext(nurse, Service.IM);
        vs.markArrived(nurse, Service.IM);
        fh.saveAll(auth, ps, vs, log);
        AuditLog log2 = new AuditLog();
        AuthService auth2 = new AuthService(log2);
        PatientService ps2 = new PatientService(auth2, log2);
        VisitService vs2 = new VisitService(auth2, ps2, log2);
        fh.loadAll(auth2, ps2, vs2, log2);
        expect("Same number of patients after reload", ps.count(), ps2::count);
        expect("Merged link survives reload", "P0001", () -> ps2.findById("P0009").getMergedInto());
        expect("Address with comma survives reload", ps.findById("P0002").getAddress(), () -> ps2.findById("P0002").getAddress());
        expect("Audit log survives reload", log.size(), log2::size);
        expect("Login works after reload", "admin", () -> auth2.login("admin", "adm123").getUsername());
        expect("Same number of visits after reload", vs.count(), vs2::count);
        expect("Outcome note with commas survives reload", doneJuan.getOutcome(), () -> vs2.findById(vJuan.getVisitId()).getOutcome());
        expect("Outcome type, clinician, and status survive reload", "TREATED/doctor/COMPLETED", () -> {
            Visit v = vs2.findById(vJuan.getVisitId());
            return v.getOutcomeType() + "/" + v.getConsultedBy() + "/" + v.getStatus();
        });
        expect("Call time and consultation times survive reload", true, () -> {
            Visit v = vs2.findById(vJuan.getVisitId());
            return v.getCalledTime() != null && v.getConsultStart() != null && v.getConsultEnd() != null && v.getConsultMinutes() == doneJuan.getConsultMinutes();
        });
        expect("Patient still in consultation is restored (Internal Medicine)", 1, () -> vs2.getInConsultation(Service.IM).size());
        expect("Waiting patient is back in the line (Family Medicine)", 1, () -> vs2.waitingCount(Service.FM));
        expect("Cancelled and no-show statuses survive reload", "CANCELLED/NO_SHOW", () ->
                vs2.findById(vLourdes.getVisitId()).getStatus() + "/" + vs2.findById(vAna.getVisitId()).getStatus());
        File oldFolder = Files.createTempDirectory("medflow-old").toFile();
        Files.writeString(new File(oldFolder, "visits.csv").toPath(),
                "visitId,patientId,service,reasonForVisit,visitType,arrivalTime,status,priorityCategory,queueNumber,arrivalOrder,missedCount,consultStart,consultEnd,outcome,adminNotes\n"
                        + "V0001,P0001,FM,Cough,WALK_IN,2026-11-12T09:00:00,WAITING,REGULAR,FM-001,1,0,,,,\n");
        AuditLog log3 = new AuditLog();
        AuthService auth3 = new AuthService(log3);
        PatientService ps3 = new PatientService(auth3, log3);
        VisitService vs3 = new VisitService(auth3, ps3, log3);
        new FileHandler(oldFolder.getPath()).loadAll(auth3, ps3, vs3, log3);
        expect("An older visits.csv (15 columns) still loads", "FM-001", () -> vs3.findById("V0001").getQueueNumber());
        expect("Next ID continues after reload (no reuse)", "P1011", () -> ps2.registerNew(auth2.login("reception", "rec123"),
                "New", "Person", "", LocalDate.of(2000, 5, 5), Sex.MALE, "1 New St., Manila", "09170001111").getPatientId());

        System.out.println("\n========================================");
        System.out.printf("TOTAL: %d tests, %d passed, %d failed%n", passed + failed, passed, failed);
        System.out.println("========================================");
        if (failed > 0) System.exit(1);
    }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA" ... so generated test names contain no digits. */
    private static String toLetters(int n) {
        StringBuilder sb = new StringBuilder();
        n++;
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }
}
