package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.Appointment;
import medflow.model.AppointmentStatus;
import medflow.model.ConsultOutcome;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.PriorityCategory;
import medflow.model.Service;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitType;
import medflow.util.Validator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Appointments and follow-ups (Figure 7, Milestone 5).
 *
 *   schedule         TreeMap<date and time, appointments at that time>   kept sorted by date and time
 *                      add, find, remove a time slot: O(log n)
 *                      upcoming = tailMap(now), missed = headMap(now), one day = subMap(day start, next day)
 *   byId             HashMap<appointmentId, Appointment>   O(1) average lookup by ID
 *   followUpByVisit  HashMap<visitId, appointmentId>       O(1) check that a follow-up is already booked
 *
 * Several appointments can share the same date and time (different patients or services),
 * so each TreeMap key holds a small list. A service takes at most SLOT_CAPACITY patients per slot.
 */
public class AppointmentService {

    /** Patients one service can see in the same 30-minute slot. */
    public static final int SLOT_CAPACITY = 3;
    /** Minutes after the appointment time before a patient who has not checked in is marked Missed. */
    public static final int GRACE_MINUTES = 30;

    private final TreeMap<LocalDateTime, ArrayList<Appointment>> schedule = new TreeMap<>();
    private final HashMap<String, Appointment> byId = new HashMap<>();
    private final HashMap<String, String> followUpByVisit = new HashMap<>();

    private final AuthService auth;
    private final PatientService patients;
    private final VisitService visits;
    private final AuditLog auditLog;
    private int lastNumber = 0;

    public AppointmentService(AuthService auth, PatientService patients, VisitService visits, AuditLog auditLog) {
        this.auth = auth;
        this.patients = patients;
        this.visits = visits;
        this.auditLog = auditLog;
    }

    // ================================================================== TreeMap helpers

    private void addToSchedule(Appointment a) {                      // O(log n)
        ArrayList<Appointment> slot = schedule.get(a.getDateTime());
        if (slot == null) {
            slot = new ArrayList<>();
            schedule.put(a.getDateTime(), slot);
        }
        slot.add(a);
    }

    private void removeFromSchedule(Appointment a) {                 // O(log n)
        ArrayList<Appointment> slot = schedule.get(a.getDateTime());
        if (slot == null) return;
        slot.remove(a);
        if (slot.isEmpty()) schedule.remove(a.getDateTime());
    }

    /** Puts every appointment of a part of the TreeMap into one list, keeping date and time order. */
    private static ArrayList<Appointment> flatten(Map<LocalDateTime, ArrayList<Appointment>> part, AppointmentStatus only) {
        ArrayList<Appointment> list = new ArrayList<>();
        for (ArrayList<Appointment> slot : part.values()) {
            for (Appointment a : slot) if (only == null || a.getStatus() == only) list.add(a);
        }
        return list;
    }

    private String nextId() {
        String id;
        do {
            lastNumber++;
            id = String.format("A%04d", lastNumber);
        } while (byId.containsKey(id));
        return id;
    }

    private Patient activePatient(String patientId) throws InvalidInputException {
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        if (p.isMerged()) {
            throw new InvalidInputException(p.getPatientId() + " was merged into " + p.getMergedInto()
                    + ". Use that ID.");
        }
        return p;
    }

    // ================================================================== rules for a time slot

    /**
     * Checks that a date and time can be booked for this patient and service:
     * not in the past, not on a Sunday, inside clinic hours on a 30-minute slot, age fits the service
     * on that day, the patient has no other appointment at the same time or in the same service that day,
     * and the slot is not full. "ignore" is the appointment being rescheduled (it does not count).
     */
    private void checkSlot(Patient p, Service service, LocalDateTime when, LocalDateTime now, Appointment ignore)
            throws InvalidInputException {
        if (!when.isAfter(now)) throw new InvalidInputException("That date and time has already passed.");
        if (when.toLocalDate().isAfter(now.toLocalDate().plusDays(Validator.MAX_DAYS_AHEAD))) {
            throw new InvalidInputException("Appointments can only be booked up to " + Validator.MAX_DAYS_AHEAD + " days ahead.");
        }
        if (when.getDayOfWeek() == DayOfWeek.SUNDAY) throw new InvalidInputException("The clinic is closed on Sundays.");
        if (when.toLocalTime().isBefore(Validator.FIRST_SLOT) || when.toLocalTime().isAfter(Validator.LAST_SLOT)
                || (when.getMinute() != 0 && when.getMinute() != 30)) {
            throw new InvalidInputException("Appointments start every 30 minutes from " + Validator.FIRST_SLOT
                    + " to " + Validator.LAST_SLOT + ".");
        }
        int ageThatDay = Period.between(p.getBirthdate(), when.toLocalDate()).getYears();
        Validator.checkServiceEligibility(ageThatDay, service);

        // Same patient: no two appointments at the same time, and one per service per day (subMap of that day)
        LocalDateTime dayStart = when.toLocalDate().atStartOfDay();
        for (Appointment a : flatten(schedule.subMap(dayStart, true, dayStart.plusDays(1), false), AppointmentStatus.SCHEDULED)) {
            if (a == ignore || !a.getPatientId().equals(p.getPatientId())) continue;
            if (a.getDateTime().equals(when)) {
                throw new InvalidInputException(p.getFullName() + " already has " + a.getAppointmentId() + " at that time.");
            }
            if (a.getService() == service) {
                throw new InvalidInputException(p.getFullName() + " already has a " + service.getLabel()
                        + " appointment that day (" + a.getAppointmentId() + " at " + a.getDateTime().toLocalTime() + ").");
            }
        }

        // Slot capacity of the service (TreeMap get: O(log n))
        int taken = 0;
        ArrayList<Appointment> slot = schedule.get(when);
        if (slot != null) {
            for (Appointment a : slot) {
                if (a != ignore && a.getService() == service && a.getStatus() == AppointmentStatus.SCHEDULED) taken++;
            }
        }
        if (taken >= SLOT_CAPACITY) {
            throw new InvalidInputException(service.getLabel() + " is full at " + when.toLocalTime() + " on "
                    + when.toLocalDate() + ". Choose another time.");
        }
    }

    /** Number of open places left for a service at a date and time. */
    public int openPlaces(Service service, LocalDateTime when) {
        int taken = 0;
        ArrayList<Appointment> slot = schedule.get(when);
        if (slot != null) {
            for (Appointment a : slot) if (a.getService() == service && a.getStatus() == AppointmentStatus.SCHEDULED) taken++;
        }
        return SLOT_CAPACITY - taken;
    }

    // ================================================================== create (Figure 7: Action = Create)

    /**
     * Books an appointment. For a follow-up, linkedVisitId is the consultation whose outcome was
     * "Follow-up visit advised". Otherwise it is empty.
     */
    public Appointment book(User user, String patientId, Service service, LocalDateTime when, String purpose,
                            String linkedVisitId, LocalDateTime now) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        Patient p = activePatient(patientId);
        String cleanPurpose = Validator.validateReason(purpose);
        String link = linkedVisitId == null ? "" : linkedVisitId.trim().toUpperCase();
        if (!link.isEmpty()) {
            Visit v = visits.findById(link);
            if (v == null) throw new InvalidInputException("No visit has the ID " + link + ".");
            if (!v.getPatientId().equals(p.getPatientId())) {
                throw new InvalidInputException(link + " belongs to " + v.getPatientId() + ", not " + p.getPatientId() + ".");
            }
            if (v.getOutcomeType() != ConsultOutcome.FOLLOW_UP) {
                throw new InvalidInputException(link + " did not end with \"Follow-up visit advised\".");
            }
            if (followUpByVisit.containsKey(link)) {
                throw new InvalidInputException("The follow-up for " + link + " is already booked as "
                        + followUpByVisit.get(link) + ".");
            }
        }
        checkSlot(p, service, when, now, null);

        Appointment a = new Appointment(nextId(), p.getPatientId(), link, when, service, cleanPurpose,
                user.getUsername(), now);
        byId.put(a.getAppointmentId(), a);
        addToSchedule(a);                                       // TreeMap key = date and time
        if (a.isFollowUp()) followUpByVisit.put(link, a.getAppointmentId());

        auditLog.add(new AuditEntry(now, user.getUsername(), a.isFollowUp() ? "BOOK_FOLLOW_UP" : "BOOK_APPOINTMENT",
                a.getAppointmentId(), p.getPatientId() + " " + service.getPrefix() + " " + when.toLocalDate() + " "
                + when.toLocalTime() + (a.isFollowUp() ? " (follow-up of " + link + ")" : "")));
        return a;
    }

    // ================================================================== reschedule and cancel

    /** Figure 7: remove it from the old date and time, then add it with the new date and time. */
    public Appointment reschedule(User user, String appointmentId, LocalDateTime newTime, LocalDateTime now)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        Appointment a = scheduledAppointment(appointmentId);
        if (a.getDateTime().equals(newTime)) throw new InvalidInputException("That is already the appointment time.");
        Patient p = activePatient(a.getPatientId());
        checkSlot(p, a.getService(), newTime, now, a);

        LocalDateTime old = a.getDateTime();
        removeFromSchedule(a);                                  // O(log n)
        a.setDateTime(newTime);
        addToSchedule(a);                                       // O(log n)
        a.setRescheduleCount(a.getRescheduleCount() + 1);
        auditLog.add(new AuditEntry(now, user.getUsername(), "RESCHEDULE", a.getAppointmentId(),
                old.toLocalDate() + " " + old.toLocalTime() + " -> " + newTime.toLocalDate() + " " + newTime.toLocalTime()));
        return a;
    }

    /** Figure 7: set the status to Cancelled. The appointment stays in the TreeMap for the record. */
    public Appointment cancel(User user, String appointmentId, LocalDateTime now)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        Appointment a = scheduledAppointment(appointmentId);
        a.setStatus(AppointmentStatus.CANCELLED);
        if (a.isFollowUp()) followUpByVisit.remove(a.getLinkedVisitId());     // the follow-up can be booked again
        auditLog.add(new AuditEntry(now, user.getUsername(), "CANCEL_APPOINTMENT", a.getAppointmentId(),
                a.getDateTime().toLocalDate() + " " + a.getDateTime().toLocalTime()));
        return a;
    }

    private Appointment scheduledAppointment(String appointmentId) throws InvalidInputException {
        Appointment a = byId.get(appointmentId == null ? "" : appointmentId.trim().toUpperCase());
        if (a == null) throw new InvalidInputException("No appointment has the ID " + appointmentId + ".");
        if (a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new InvalidInputException(a.getAppointmentId() + " is " + a.getStatus() + ". Only a scheduled appointment can be changed.");
        }
        return a;
    }

    // ================================================================== lists (Figure 7: Action = Show lists)

    /**
     * Marks every scheduled appointment that is more than GRACE_MINUTES late as Missed.
     * Uses headMap, so only the past part of the TreeMap is read. Returns how many were marked.
     */
    public int markMissed(LocalDateTime now) {
        int count = 0;
        for (Appointment a : flatten(schedule.headMap(now.minusMinutes(GRACE_MINUTES), true), AppointmentStatus.SCHEDULED)) {
            a.setStatus(AppointmentStatus.MISSED);
            if (a.isFollowUp()) followUpByVisit.remove(a.getLinkedVisitId());   // can be booked again
            auditLog.add(new AuditEntry(now, "system", "MISSED_APPOINTMENT", a.getAppointmentId(),
                    a.getPatientId() + " did not check in for " + a.getDateTime().toLocalDate() + " " + a.getDateTime().toLocalTime()));
            count++;
        }
        return count;
    }

    /** Scheduled appointments from now onward, in date and time order (tailMap). */
    public ArrayList<Appointment> upcoming(User user, LocalDateTime now) throws AccessDeniedException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        markMissed(now);
        return flatten(schedule.tailMap(now.minusMinutes(GRACE_MINUTES), false), AppointmentStatus.SCHEDULED);
    }

    /** Missed appointments, newest first (headMap of now, read backwards). */
    public ArrayList<Appointment> missed(User user, LocalDateTime now) throws AccessDeniedException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        markMissed(now);
        return flatten(schedule.headMap(now, true).descendingMap(), AppointmentStatus.MISSED);
    }

    /** Every appointment of one day, in time order (subMap from the start of the day to the next day). */
    public ArrayList<Appointment> onDay(User user, LocalDate day, LocalDateTime now) throws AccessDeniedException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        markMissed(now);
        LocalDateTime start = day.atStartOfDay();
        return flatten(schedule.subMap(start, true, start.plusDays(1), false), null);
    }

    /** All appointments of one patient, in date and time order. O(n). */
    public ArrayList<Appointment> ofPatient(User user, String patientId, LocalDateTime now)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        markMissed(now);
        ArrayList<Appointment> list = new ArrayList<>();
        for (Appointment a : flatten(schedule, null)) if (a.getPatientId().equals(p.getPatientId())) list.add(a);
        return list;
    }

    /** Scheduled appointments of one patient from now on, without a permission check (for the record view). */
    public int upcomingCountOf(String patientId, LocalDateTime now) {
        int n = 0;
        for (Appointment a : flatten(schedule.tailMap(now, true), AppointmentStatus.SCHEDULED)) {
            if (a.getPatientId().equals(patientId)) n++;
        }
        return n;
    }

    /**
     * Consultations that ended with "Follow-up visit advised" and have no follow-up booked yet.
     * O(V) over all visits, each check O(1) with the followUpByVisit HashMap.
     */
    public ArrayList<Visit> followUpsToBook(User user) throws AccessDeniedException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        ArrayList<Visit> list = new ArrayList<>();
        for (Visit v : visits.getAllVisits()) {
            if (v.getOutcomeType() == ConsultOutcome.FOLLOW_UP && !followUpByVisit.containsKey(v.getVisitId())) list.add(v);
        }
        list.sort((x, y) -> x.getConsultEnd().compareTo(y.getConsultEnd()));     // display only, oldest first
        return list;
    }

    // ================================================================== check-in

    /**
     * The patient arrived for today's appointment. Creates a visit and queue number through
     * VisitService (type Scheduled, or Follow-up if it came from a consultation) and marks the
     * appointment Completed.
     */
    public Visit checkIn(User user, String appointmentId, PriorityCategory priority, LocalDateTime now)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_APPOINTMENTS);
        markMissed(now);
        Appointment a = scheduledAppointment(appointmentId);
        if (!a.getDateTime().toLocalDate().equals(now.toLocalDate())) {
            throw new InvalidInputException(a.getAppointmentId() + " is on " + a.getDateTime().toLocalDate()
                    + ". Patients can only check in on the day of the appointment.");
        }
        VisitType type = a.isFollowUp() ? VisitType.FOLLOW_UP : VisitType.SCHEDULED;
        Visit v = visits.createVisit(user, a.getPatientId(), a.getService(), a.getPurpose(), type, priority);
        a.setStatus(AppointmentStatus.COMPLETED);
        a.setCheckInVisitId(v.getVisitId());
        auditLog.add(new AuditEntry(now, user.getUsername(), "CHECK_IN", a.getAppointmentId(),
                "Visit " + v.getVisitId() + ", queue number " + v.getQueueNumber()));
        return v;
    }

    // ================================================================== lookup, loading, saving

    public Appointment findById(String appointmentId) {
        return appointmentId == null ? null : byId.get(appointmentId.trim().toUpperCase());
    }

    public int count() { return byId.size(); }

    /** Adds an appointment read from file (no permission check, no audit entry). */
    public void loadAppointment(Appointment a) {
        byId.put(a.getAppointmentId(), a);
        addToSchedule(a);
        if (a.isFollowUp() && a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.MISSED) {
            followUpByVisit.put(a.getLinkedVisitId(), a.getAppointmentId());
        }
        int n = Integer.parseInt(a.getAppointmentId().substring(1));
        if (n > lastNumber) lastNumber = n;
    }

    /** Every appointment in date and time order. */
    public ArrayList<Appointment> getAll() { return flatten(schedule, null); }
}
