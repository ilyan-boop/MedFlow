package medflow;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.ds.LinkedQueue;
import medflow.ds.VersionStack;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.io.FileHandler;
import medflow.io.SampleData;
import medflow.model.Appointment;
import medflow.model.AppointmentStatus;
import medflow.model.ConsultOutcome;
import medflow.model.PriorityCategory;
import medflow.model.RecordField;
import medflow.model.RecordVersion;
import medflow.model.Service;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitType;
import medflow.service.AppointmentService;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.service.RecordHistory;
import medflow.service.VisitService;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Test cases for Milestone 5 (Nov 16): version history (stack), audit log filters,
 * appointments and follow-ups (TreeMap), and the hand-coded queue used by the service lines.
 * Run with:  java -cp out medflow.Milestone5Tests
 */
public class Milestone5Tests {

    private static int passed = 0, failed = 0, number = 0;

    /** Fixed "now" for appointment rules: Monday, November 16, 2026, 8:00 AM. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 8, 0);

    private interface Check { Object run() throws Exception; }

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

    private static void expectError(String name, Class<? extends Exception> type, Check check) {
        number++;
        try {
            Object actual = check.run();
            fail(name, "expected " + type.getSimpleName() + " but got " + actual);
        } catch (Exception e) {
            if (type.isInstance(e)) pass(name, "-> rejected: " + e.getMessage());
            else fail(name, "expected " + type.getSimpleName() + " but got " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void pass(String name, String detail) {
        passed++;
        System.out.printf("PASS  %3d  %-60s %s%n", number, name, detail);
    }

    private static void fail(String name, String detail) {
        failed++;
        System.out.printf("FAIL  %3d  %-60s %s%n", number, name, detail);
    }

    private static void section(String title) { System.out.println("\n--- " + title + " ---"); }

    private static LocalDateTime at(int day, int hour, int minute) { return LocalDateTime.of(2026, 11, day, hour, minute); }

    public static void main(String[] args) throws Exception {
        System.out.println("MedFlow test results (Milestone 5), fixed appointment clock: " + NOW);

        // ------------------------------------------------------------ hand-coded data structures
        section("Hand-coded LinkedQueue (regular line and priority lane)");
        LinkedQueue<String> q = new LinkedQueue<>();
        expect("Dequeue from an empty queue returns null", null, q::dequeue);
        q.enqueue("FM-001"); q.enqueue("FM-002"); q.enqueue("FM-003");
        expect("Size after 3 enqueues", 3, q::size);
        expect("Peek shows the front without removing it", "FM-001", q::peek);
        expect("Iterating keeps first in, first out order", "FM-001,FM-002,FM-003", () -> String.join(",", list(q)));
        expect("Dequeue returns the first one in", "FM-001", q::dequeue);
        String middle = "FM-003";
        expect("Remove from the middle or back (cancelled visit)", true, () -> q.remove(middle));
        q.enqueue("FM-004");
        expect("Enqueue after removing the tail still works", "FM-002,FM-004", () -> String.join(",", list(q)));
        expect("Removing an item that is not there returns false", false, () -> q.remove("FM-999"));

        section("Hand-coded VersionStack (version history)");
        VersionStack<String> st = new VersionStack<>();
        expect("Pop from an empty stack returns null", null, st::pop);
        st.push("v1"); st.push("v2"); st.push("v3");
        expect("Peek shows the newest version", "v3", st::peek);
        expect("Listing goes newest to oldest", "[v3, v2, v1]", () -> st.toListNewestFirst().toString());
        expect("Pop removes the newest (last in, first out)", "v3", st::pop);
        expect("Size after one pop", 2, st::size);

        // ------------------------------------------------------------ setup
        AuditLog log = new AuditLog();
        AuthService auth = new AuthService(log);
        PatientService ps = new PatientService(auth, log);
        SampleData.load(auth, ps);
        VisitService vs = new VisitService(auth, ps, log);
        RecordHistory rh = new RecordHistory(auth, ps, log);
        AppointmentService as = new AppointmentService(auth, ps, vs, log);
        User reception = auth.login("reception", "rec123");
        User nurse = auth.login("nurse", "nur123");
        User doctor = auth.login("doctor", "doc123");
        User records = auth.login("records", "rec456");
        User admin = auth.login("admin", "adm123");

        // ------------------------------------------------------------ version history
        section("Record updates with version history (stack)");
        RecordVersion v1 = rh.updateField(records, "P0002", RecordField.CONTACT_NUMBER, "0928-555-0000");
        expect("Records staff corrects a contact number", "09285550000", () -> ps.findById("P0002").getContactNumber());
        expect("The old record is pushed as version 1", 1, v1::getVersionNumber);
        expect("Version 1 keeps the old contact number", "09281112233", v1::getContactNumber);
        expect("The record is now version 2", 2, () -> rh.currentVersion("P0002"));
        expectError("Invalid new value is rejected", InvalidInputException.class,
                () -> rh.updateField(records, "P0002", RecordField.CONTACT_NUMBER, "0928555"));
        expectError("Same value as the current one is rejected", InvalidInputException.class,
                () -> rh.updateField(records, "P0002", RecordField.CONTACT_NUMBER, "09285550000"));
        expect("Rejected changes push nothing", 2, () -> rh.currentVersion("P0002"));
        expectError("Receptionist cannot correct records", AccessDeniedException.class,
                () -> rh.updateField(reception, "P0002", RecordField.ADDRESS, "9 New St., Manila"));
        expectError("Records staff cannot change clinical notes", AccessDeniedException.class,
                () -> rh.updateField(records, "P0002", RecordField.ALLERGIES, "Aspirin"));
        rh.updateField(doctor, "P0002", RecordField.ALLERGIES, "Aspirin");
        expect("Clinician updates allergies", "Aspirin", () -> ps.findById("P0002").getAllergies());
        rh.updateField(records, "P0002", RecordField.ADDRESS, "9 New St., Brgy. Malate, Manila");
        expect("History is newest first (top of the stack)", "ADDRESS,ALLERGIES,CONTACT_NUMBER",
                () -> fields(rh.getHistory(records, "P0002")));
        expectError("Nurse cannot open the version history", AccessDeniedException.class, () -> rh.getHistory(nurse, "P0002"));
        RecordVersion undo = rh.undoLatest(records, "P0002");
        expect("Undo latest puts the old address back", "45 Rizal Ave., Brgy. Malate, Manila", () -> ps.findById("P0002").getAddress());
        expect("The undo is saved as a new version (nothing lost)", 5, () -> rh.currentVersion("P0002"));
        expect("The undo version is marked RESTORE", RecordVersion.Action.RESTORE, undo::getAction);
        expectError("Records staff cannot undo a clinical change", AccessDeniedException.class, () -> rh.restore(records, "P0002", 2));
        expect("Clinician can undo the allergy change", "", () -> { rh.restore(doctor, "P0002", 2); return ps.findById("P0002").getAllergies(); });
        expect("Find a saved version by number", "09281112233", () -> rh.findVersion("P0002", 1).getContactNumber());

        section("Name or birthdate change and the duplicate check");
        rh.updateField(records, "P0007", RecordField.FIRST_NAME, "Juan");
        rh.updateField(records, "P0007", RecordField.LAST_NAME, "dela cruz");
        expect("Name typed in lowercase is capitalized", "Dela Cruz", () -> ps.findById("P0007").getLastName());
        expect("No duplicate yet (birthdate still differs)", 0, () -> ps.getPendingFlags(records).size());
        rh.updateField(records, "P0007", RecordField.BIRTHDATE, "1988-04-12");
        expect("Same name and birthdate as P0001 now flags a duplicate", 1, () -> ps.getPendingFlags(records).size());
        expect("The old duplicate key no longer finds P0007", 0,
                () -> ps.findPossibleDuplicates("Mendoza", "Jose", LocalDate.of(1979, 6, 15)).size());

        section("Audit log entries and filters (linked list)");
        expect("Updates are logged as UPDATE_RECORD", true, () -> !log.byAction("UPDATE_RECORD").isEmpty());
        expect("Undos are logged as RESTORE_VERSION", 2, () -> log.byAction("RESTORE").size());
        expect("Clinical values are not written in the log", true, () -> {
            for (AuditEntry e : log.byTarget("P0002")) if (e.getDetails().contains("Aspirin")) return false;
            return true;
        });
        expect("Filter by user finds records staff changes", true, () -> log.byUser("records").size() >= 6);
        expect("Filter by record finds every change to P0007", 4, () -> log.byTarget("p0007").size());

        // ------------------------------------------------------------ appointments
        section("Appointments: booking rules (TreeMap)");
        Appointment a1 = as.book(reception, "P0002", Service.FM, at(18, 9, 0), "General check-up", "", NOW);
        Appointment a2 = as.book(reception, "P0005", Service.FM, at(17, 10, 0), "Fever check", "", NOW);
        expect("First appointment ID", "A0001", a1::getAppointmentId);
        expect("Upcoming list is in date and time order", "A0002,A0001", () -> ids(as.upcoming(reception, NOW)));
        expectError("Nurse cannot book appointments", AccessDeniedException.class,
                () -> as.book(nurse, "P0002", Service.FM, at(19, 9, 0), "Check-up", "", NOW));
        expectError("A time that has passed is rejected", InvalidInputException.class,
                () -> as.book(reception, "P0004", Service.IM, at(16, 7, 30), "Check-up", "", NOW));
        expectError("A Sunday is rejected", InvalidInputException.class,
                () -> as.book(reception, "P0004", Service.IM, at(22, 9, 0), "Check-up", "", NOW));
        expectError("A child cannot book Internal Medicine", InvalidInputException.class,
                () -> as.book(reception, "P0003", Service.IM, at(18, 9, 0), "Check-up", "", NOW));
        expectError("An adult cannot book Pediatrics", InvalidInputException.class,
                () -> as.book(reception, "P0004", Service.PED, at(18, 9, 0), "Check-up", "", NOW));
        expectError("Same patient at the same time is rejected", InvalidInputException.class,
                () -> as.book(reception, "P0002", Service.FM, at(18, 9, 0), "Another visit", "", NOW));
        expectError("Same patient, same service, same day is rejected", InvalidInputException.class,
                () -> as.book(reception, "P0002", Service.FM, at(18, 14, 0), "Another visit", "", NOW));
        as.book(reception, "P0001", Service.FM, at(19, 10, 0), "Check-up", "", NOW);
        as.book(reception, "P0004", Service.FM, at(19, 10, 0), "Check-up", "", NOW);
        as.book(reception, "P0006", Service.FM, at(19, 10, 0), "Check-up", "", NOW);
        expect("Open places after 3 bookings in one slot", 0, () -> as.openPlaces(Service.FM, at(19, 10, 0)));
        expectError("A 4th patient in a full slot is rejected", InvalidInputException.class,
                () -> as.book(reception, "P0008", Service.FM, at(19, 10, 0), "Check-up", "", NOW));
        expect("Another service can still use the same time", "PED",
                () -> as.book(reception, "P0008", Service.PED, at(19, 10, 0), "Vaccination", "", NOW).getService().getPrefix());
        expectError("Invalid time format rejected by the validator", InvalidInputException.class,
                () -> medflow.util.Validator.validateAppointmentTime("9:15"));
        expectError("Time outside clinic hours rejected", InvalidInputException.class,
                () -> medflow.util.Validator.validateAppointmentTime("17:00"));
        expect("Short time 9:30 is accepted", "09:30", () -> medflow.util.Validator.validateAppointmentTime("9:30").toString());

        section("Reschedule and cancel");
        as.reschedule(reception, "A0001", at(20, 11, 0), NOW);
        expect("Rescheduled appointment has the new time", at(20, 11, 0), () -> as.findById("A0001").getDateTime());
        expect("Upcoming order changes with it (A0001 moves to the end)", "A0002,A0003,A0004,A0005,A0006,A0001",
                () -> ids(as.upcoming(reception, NOW)));
        expect("Reschedule count goes up", 1, () -> as.findById("A0001").getRescheduleCount());
        expectError("Rescheduling into a full slot is rejected", InvalidInputException.class,
                () -> as.reschedule(reception, "A0002", at(19, 10, 0), NOW));
        as.cancel(reception, "A0002", NOW);
        expect("Cancelled appointment keeps its record", AppointmentStatus.CANCELLED, () -> as.findById("A0002").getStatus());
        expect("Cancelled appointment leaves the upcoming list", false, () -> ids(as.upcoming(reception, NOW)).contains("A0002"));
        expectError("Cancelling twice is rejected", InvalidInputException.class, () -> as.cancel(reception, "A0002", NOW));

        section("Upcoming, missed, and today's list");
        Appointment early = as.book(reception, "P0004", Service.IM, at(16, 8, 30), "Routine check-up", "", NOW);
        expect("Not missed yet within the 30-minute grace period", 0, () -> as.missed(reception, at(16, 8, 50)).size());
        expect("Missed after the grace period (headMap)", early.getAppointmentId(), () -> ids(as.missed(reception, at(16, 9, 30))));
        expect("Status becomes MISSED", AppointmentStatus.MISSED, early::getStatus);
        expect("Missed appointment is logged by the system", true, () -> !log.byAction("MISSED_APPOINTMENT").isEmpty());

        section("Follow-ups from consultations and check-in");
        Visit v = vs.createVisit(reception, "P0006", Service.IM, "Cough for 2 weeks", VisitType.WALK_IN, PriorityCategory.REGULAR);
        vs.callNext(nurse, Service.IM);
        vs.markArrived(nurse, Service.IM);
        vs.recordOutcome(doctor, v.getVisitId(), ConsultOutcome.FOLLOW_UP, "Come back in one week");
        expect("Consultation with Follow-up advised appears in the list", v.getVisitId(),
                () -> as.followUpsToBook(reception).get(0).getVisitId());
        Appointment fu = as.book(reception, "P0006", Service.IM, at(23, 9, 0), "Follow-up of " + v.getVisitId(), v.getVisitId(), NOW);
        expect("Follow-up is linked to the visit", v.getVisitId(), fu::getLinkedVisitId);
        expect("Booked follow-up leaves the to-book list", 0, () -> as.followUpsToBook(reception).size());
        expectError("The same follow-up cannot be booked twice", InvalidInputException.class,
                () -> as.book(reception, "P0006", Service.IM, at(24, 9, 0), "Follow-up again", v.getVisitId(), NOW));
        Appointment today = as.book(reception, "P0005", Service.PED, at(16, 10, 0), "Vaccination", "", NOW);
        expect("Today's list uses subMap of the day", true, () -> ids(as.onDay(reception, LocalDate.of(2026, 11, 16), NOW)).contains(today.getAppointmentId()));
        expectError("Check-in on another day is rejected", InvalidInputException.class,
                () -> as.checkIn(reception, "A0001", PriorityCategory.REGULAR, at(16, 9, 45)));
        Visit checkedIn = as.checkIn(reception, today.getAppointmentId(), PriorityCategory.REGULAR, at(16, 9, 45));
        expect("Check-in creates a Scheduled visit with a queue number", "SCHEDULED PED", () ->
                checkedIn.getVisitType() + " " + checkedIn.getQueueNumber().substring(0, 3));
        expect("Appointment becomes COMPLETED", AppointmentStatus.COMPLETED, today::getStatus);
        expect("Appointment remembers the visit it created", checkedIn.getVisitId(), today::getCheckInVisitId);

        // ------------------------------------------------------------ files
        section("Saving and loading appointments and version history");
        File folder = Files.createTempDirectory("medflow-m5").toFile();
        FileHandler fh = new FileHandler(folder.getPath());
        fh.saveAll(auth, ps, vs, rh, as, log);
        AuditLog log2 = new AuditLog();
        AuthService auth2 = new AuthService(log2);
        PatientService ps2 = new PatientService(auth2, log2);
        VisitService vs2 = new VisitService(auth2, ps2, log2);
        RecordHistory rh2 = new RecordHistory(auth2, ps2, log2);
        AppointmentService as2 = new AppointmentService(auth2, ps2, vs2, log2);
        fh.loadAll(auth2, ps2, vs2, rh2, as2, log2);
        expect("Same number of appointments after reload", as.count(), as2::count);
        expect("Appointments come back in date and time order", ids(as.getAll()), () -> ids(as2.getAll()));
        expect("Version history comes back with the same top", rh.latestChange("P0002").getVersionNumber(),
                () -> rh2.latestChange("P0002").getVersionNumber());
        expect("Current version number survives reload", rh.currentVersion("P0007"), () -> rh2.currentVersion("P0007"));
        expect("Booked follow-up link survives reload", 0, () -> as2.followUpsToBook(auth2.login("reception", "rec123")).size());
        expect("Next appointment ID continues after reload", "A" + String.format("%04d", as.count() + 1), () ->
                as2.book(auth2.login("reception", "rec123"), "P0008", Service.FM, at(27, 9, 0), "Check-up", "", NOW).getAppointmentId());

        System.out.println("\n========================================");
        System.out.printf("TOTAL: %d tests, %d passed, %d failed%n", passed + failed, passed, failed);
        System.out.println("========================================");
        if (failed > 0) System.exit(1);
    }

    private static ArrayList<String> list(LinkedQueue<String> q) {
        ArrayList<String> out = new ArrayList<>();
        for (String s : q) out.add(s);
        return out;
    }

    private static String fields(ArrayList<RecordVersion> list) {
        StringBuilder sb = new StringBuilder();
        for (RecordVersion v : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(v.getChangedField().name());
        }
        return sb.toString();
    }

    private static String ids(ArrayList<Appointment> list) {
        StringBuilder sb = new StringBuilder();
        for (Appointment a : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.getAppointmentId());
        }
        return sb.toString();
    }
}
