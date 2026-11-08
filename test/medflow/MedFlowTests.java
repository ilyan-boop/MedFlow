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
import medflow.model.Permission;
import medflow.model.Service;
import medflow.model.Sex;
import medflow.model.User;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.util.Validator;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;

/**
 * Test cases for Milestones 1 and 2. Run with:
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
        System.out.println("MedFlow test results (Milestones 1 and 2), test date used for ages: " + TODAY);

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

        // ------------------------------------------------------------ files
        section("Saving and loading .csv files");
        expect("CSV keeps commas and quotes", "Blk 5, \"Unit A\"", () -> Csv.split(Csv.join("x", "Blk 5, \"Unit A\"")).get(1));
        File folder = Files.createTempDirectory("medflow-test").toFile();
        FileHandler fh = new FileHandler(folder.getPath());
        fh.saveAll(auth, ps, log);
        AuditLog log2 = new AuditLog();
        AuthService auth2 = new AuthService(log2);
        PatientService ps2 = new PatientService(auth2, log2);
        fh.loadAll(auth2, ps2, log2);
        expect("Same number of patients after reload", ps.count(), ps2::count);
        expect("Merged link survives reload", "P0001", () -> ps2.findById("P0009").getMergedInto());
        expect("Address with comma survives reload", ps.findById("P0002").getAddress(), () -> ps2.findById("P0002").getAddress());
        expect("Audit log survives reload", log.size(), log2::size);
        expect("Login works after reload", "admin", () -> auth2.login("admin", "adm123").getUsername());
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
