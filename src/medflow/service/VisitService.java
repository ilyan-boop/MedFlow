package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.ds.LinkedQueue;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.ConsultOutcome;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.PriorityCategory;
import medflow.model.Service;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitStatus;
import medflow.model.VisitType;
import medflow.util.Validator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Visit creation (Figure 3), calling patients (Figure 4), and consultation tracking with
 * outcome recording (Milestone 4).
 *
 *   visits              HashMap<visitId, Visit>             O(1) average lookup by visit ID
 *   activeQueueNumbers  HashSet<queue number>               O(1) average "is this number in use?" check
 *   priorityLane        per service, hand-coded LinkedQueue   enqueue and dequeue in O(1)
 *   regularLine         per service, hand-coded LinkedQueue   enqueue and dequeue in O(1)
 *   nowCalling          per service, the patient who was called and has not answered yet
 *   inConsultation      per service, LinkedHashMap<visitId, Visit>   add and remove in O(1), keeps start order
 *   ALLOWED             EnumMap<VisitStatus, EnumSet<VisitStatus>>   O(1) check of every status change
 *
 * A called patient stays WAITING until staff record that the patient came (IN_CONSULTATION),
 * did not come (back of the line, or NO_SHOW on the second miss), or was cancelled.
 * A visit in consultation ends as COMPLETED or REFERRED when the clinician records the outcome.
 */
public class VisitService {

    private static final int MAX_SEQUENCE = 999;
    /** A patient who misses this many calls becomes a no-show. */
    public static final int MAX_MISSED = 2;

    private final HashMap<String, Visit> visits = new HashMap<>();
    private final HashSet<String> activeQueueNumbers = new HashSet<>();
    private final EnumMap<Service, LinkedQueue<Visit>> priorityLane = new EnumMap<>(Service.class);
    private final EnumMap<Service, LinkedQueue<Visit>> regularLine = new EnumMap<>(Service.class);
    private final EnumMap<Service, Visit> nowCalling = new EnumMap<>(Service.class);
    private final EnumMap<Service, Integer> lastSequence = new EnumMap<>(Service.class);
    private final EnumMap<Service, LinkedHashMap<String, Visit>> inConsultation = new EnumMap<>(Service.class);

    /** The only status changes allowed. Completed, Referred, Cancelled, and No-show are final. */
    private static final EnumMap<VisitStatus, EnumSet<VisitStatus>> ALLOWED = new EnumMap<>(VisitStatus.class);
    static {
        ALLOWED.put(VisitStatus.REGISTERED, EnumSet.of(VisitStatus.WAITING, VisitStatus.CANCELLED));
        ALLOWED.put(VisitStatus.WAITING, EnumSet.of(VisitStatus.IN_CONSULTATION, VisitStatus.CANCELLED, VisitStatus.NO_SHOW));
        ALLOWED.put(VisitStatus.IN_CONSULTATION, EnumSet.of(VisitStatus.COMPLETED, VisitStatus.REFERRED));
        ALLOWED.put(VisitStatus.COMPLETED, EnumSet.noneOf(VisitStatus.class));
        ALLOWED.put(VisitStatus.REFERRED, EnumSet.noneOf(VisitStatus.class));
        ALLOWED.put(VisitStatus.CANCELLED, EnumSet.noneOf(VisitStatus.class));
        ALLOWED.put(VisitStatus.NO_SHOW, EnumSet.noneOf(VisitStatus.class));
    }

    private final AuthService auth;
    private final PatientService patients;
    private final AuditLog auditLog;
    private int lastVisitNumber = 0;
    private long lastArrivalOrder = 0;

    public VisitService(AuthService auth, PatientService patients, AuditLog auditLog) {
        this.auth = auth;
        this.patients = patients;
        this.auditLog = auditLog;
        for (Service s : Service.values()) {
            priorityLane.put(s, new LinkedQueue<>());
            regularLine.put(s, new LinkedQueue<>());
            lastSequence.put(s, 0);
            inConsultation.put(s, new LinkedHashMap<>());
        }
    }

    // ================================================================== visit status rules (Milestone 4)

    /** True if a visit may change from one status to the other. O(1). */
    public static boolean canChange(VisitStatus from, VisitStatus to) { return ALLOWED.get(from).contains(to); }

    /** Changes the status of a visit only if the change is allowed. */
    private void changeStatus(Visit v, VisitStatus to) throws InvalidInputException {
        if (!canChange(v.getStatus(), to)) {
            throw new InvalidInputException(v.getVisitId() + " cannot change from " + v.getStatus() + " to " + to + ".");
        }
        v.setStatus(to);
    }

    // ================================================================== visit creation (Figure 3)

    /**
     * Creates a visit and puts it in the queue of the chosen service.
     * Steps follow Figure 3: age check, visit ID, queue number (checked against the active set),
     * priority lane or regular line, status Waiting, audit entry.
     */
    public Visit createVisit(User user, String patientId, Service service, String reason,
                             VisitType type, PriorityCategory priority)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.CREATE_VISIT);

        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        if (p.isMerged()) {
            throw new InvalidInputException(p.getPatientId() + " was merged into " + p.getMergedInto()
                    + ". Create the visit under that ID.");
        }

        LocalDateTime now = LocalDateTime.now();
        Validator.checkServiceEligibility(p.getAge(now.toLocalDate()), service);   // "Age fits the service?"
        if (hasActiveVisit(p, service)) {
            throw new InvalidInputException(p.getFullName() + " already has an active " + service.getLabel()
                    + " visit. Cancel it first or finish it before creating another.");
        }

        String queueNumber = nextQueueNumber(service);            // also adds the number to the active set

        Visit v = new Visit(nextVisitId(), p.getPatientId(), service, reason, type, now);
        v.setPriorityCategory(priority);
        v.setQueueNumber(queueNumber);
        v.setArrivalOrder(++lastArrivalOrder);
        visits.put(v.getVisitId(), v);                            // status = REGISTERED
        p.getVisitIds().add(v.getVisitId());

        enqueue(v);                                               // priority lane or regular line
        changeStatus(v, VisitStatus.WAITING);

        auditLog.add(new AuditEntry(now, user.getUsername(), "CREATE_VISIT", v.getVisitId(),
                queueNumber + " for " + p.getPatientId()
                        + (priority.isPriority() ? " (priority: " + priority.name() + ")" : "")));
        return v;
    }

    private boolean hasActiveVisit(Patient p, Service service) {
        for (String id : p.getVisitIds()) {
            Visit v = visits.get(id);
            if (v != null && v.getService() == service && isActive(v.getStatus())) return true;
        }
        return false;
    }

    private static boolean isActive(VisitStatus s) {
        return s == VisitStatus.REGISTERED || s == VisitStatus.WAITING || s == VisitStatus.IN_CONSULTATION;
    }

    private String nextVisitId() {
        String id;
        do {
            lastVisitNumber++;
            id = String.format("V%04d", lastVisitNumber);
        } while (visits.containsKey(id));
        return id;
    }

    /**
     * Makes the next queue number of the service, e.g. FM-001. If the number is already in the
     * active set, the next one is tried. The number is added to the active set before it is returned.
     */
    private String nextQueueNumber(Service service) throws InvalidInputException {
        String number;
        int tries = 0;
        do {
            if (tries++ >= MAX_SEQUENCE) {
                throw new InvalidInputException("All queue numbers for " + service.getLabel() + " are in use.");
            }
            int next = lastSequence.get(service) + 1;
            if (next > MAX_SEQUENCE) next = 1;
            lastSequence.put(service, next);
            number = String.format("%s-%03d", service.getPrefix(), next);
        } while (activeQueueNumbers.contains(number));
        activeQueueNumbers.add(number);
        return number;
    }

    private void enqueue(Visit v) {
        if (v.getPriorityCategory().isPriority()) priorityLane.get(v.getService()).enqueue(v);
        else regularLine.get(v.getService()).enqueue(v);
    }

    /** Place in line: the priority lane is served first, so regular patients count it too. O(n). */
    public int positionOf(Visit v) {
        Service s = v.getService();
        int index = 1;
        if (v.getPriorityCategory().isPriority()) {
            for (Visit x : priorityLane.get(s)) { if (x == v) return index; index++; }
        } else {
            index = priorityLane.get(s).size() + 1;
            for (Visit x : regularLine.get(s)) { if (x == v) return index; index++; }
        }
        return 0;
    }

    // ================================================================== calling patients (Figure 4)

    /** The patient being called now (not yet answered), or null. */
    public Visit getNowCalling(Service service) { return nowCalling.get(service); }

    /** Patient being called first (if any), then the priority lane, then the regular line. */
    public ArrayList<Visit> getQueueView(Service service) {
        ArrayList<Visit> list = new ArrayList<>();
        Visit calling = nowCalling.get(service);
        if (calling != null) list.add(calling);
        for (Visit v : priorityLane.get(service)) list.add(v);
        for (Visit v : regularLine.get(service)) list.add(v);
        return list;
    }

    /** Number of patients still in line (not counting the one being called). */
    public int waitingCount(Service service) {
        return priorityLane.get(service).size() + regularLine.get(service).size();
    }

    /**
     * Action = Call next. Takes the front of the priority lane, or of the regular line if the
     * priority lane is empty. Returns null when nobody is waiting.
     */
    public Visit callNext(User user, Service service) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_QUEUE);
        Visit current = nowCalling.get(service);
        if (current != null) {
            throw new InvalidInputException(current.getQueueNumber() + " is still being called. "
                    + "Record first whether the patient came or did not come.");
        }
        Visit next = priorityLane.get(service).dequeue();
        if (next == null) next = regularLine.get(service).dequeue();
        if (next == null) return null;

        nowCalling.put(service, next);
        next.setCalledTime(LocalDateTime.now());
        log(user, "CALL_PATIENT", next, "Now calling " + next.getQueueNumber());
        return next;
    }

    /**
     * Action = Patient did not come. Missed count + 1. On the second miss the visit becomes
     * NO_SHOW and the queue number is freed. Otherwise the patient goes to the back of the same line.
     */
    public Visit markMissed(User user, Service service) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_QUEUE);
        Visit v = nowCalling.remove(service);
        if (v == null) throw new InvalidInputException("No patient is being called in " + service.getLabel() + ".");

        v.addMissed();
        if (v.getMissedCount() >= MAX_MISSED) {
            changeStatus(v, VisitStatus.NO_SHOW);
            activeQueueNumbers.remove(v.getQueueNumber());
            log(user, "NO_SHOW", v, v.getQueueNumber() + " missed " + v.getMissedCount() + " calls");
        } else {
            v.setArrivalOrder(++lastArrivalOrder);       // back of the same line (also keeps the order after a restart)
            enqueue(v);
            log(user, "MISSED_CALL", v, v.getQueueNumber() + " missed call " + v.getMissedCount()
                    + ", moved to the back of the line");
        }
        return v;
    }

    /**
     * The called patient came. Moves the visit to IN_CONSULTATION so the queue can continue,
     * saves the start time, and adds the visit to the consultations in progress.
     */
    public Visit markArrived(User user, Service service) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_QUEUE);
        Visit v = nowCalling.remove(service);
        if (v == null) throw new InvalidInputException("No patient is being called in " + service.getLabel() + ".");
        changeStatus(v, VisitStatus.IN_CONSULTATION);
        v.setConsultStart(LocalDateTime.now());
        inConsultation.get(service).put(v.getVisitId(), v);
        log(user, "START_CONSULT", v, v.getQueueNumber() + " came to the clinician (waited "
                + v.getWaitingMinutes() + " min)");
        return v;
    }

    /** Action = Cancel. Works for a waiting patient or the one being called. */
    public void cancel(User user, Visit v) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_QUEUE);
        if (v.getStatus() != VisitStatus.WAITING) {
            throw new InvalidInputException("Only a waiting visit can be cancelled.");
        }
        Service s = v.getService();
        if (nowCalling.get(s) == v) nowCalling.remove(s);
        else if (!priorityLane.get(s).remove(v)) regularLine.get(s).remove(v);   // O(n)

        changeStatus(v, VisitStatus.CANCELLED);
        activeQueueNumbers.remove(v.getQueueNumber());
        log(user, "CANCEL_VISIT", v, v.getQueueNumber() + " cancelled");
    }

    // ================================================================== consultation tracking (Milestone 4)

    /** Visits in consultation for one service, in the order they started. O(k) for k visits. */
    public ArrayList<Visit> getInConsultation(Service service) {
        return new ArrayList<>(inConsultation.get(service).values());
    }

    /** Visits in consultation for all services, oldest start first. */
    public ArrayList<Visit> getAllInConsultation() {
        ArrayList<Visit> all = new ArrayList<>();
        for (Service s : Service.values()) all.addAll(inConsultation.get(s).values());
        all.sort((a, b) -> a.getConsultStart().compareTo(b.getConsultStart()));
        return all;
    }

    /**
     * Action = Record outcome. Only a clinician can do this, and only for a visit that is in consultation.
     * Saves the end time, the outcome type, and the note. The status becomes COMPLETED or REFERRED
     * (from the outcome type) and the queue number is freed.
     */
    public Visit recordOutcome(User user, String visitId, ConsultOutcome type, String note)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.RECORD_CONSULTATION);
        Visit v = visits.get(visitId);
        if (v == null) throw new InvalidInputException("No visit has the ID " + visitId + ".");
        if (v.getStatus() != VisitStatus.IN_CONSULTATION) {
            throw new InvalidInputException(v.getVisitId() + " is not in consultation (status: " + v.getStatus()
                    + "). An outcome can only be recorded for a visit that is in consultation.");
        }
        if (type == null) throw new InvalidInputException("Choose an outcome.");
        String cleanNote = Validator.validateOutcomeNote(note);

        changeStatus(v, type.getFinalStatus());
        v.setConsultEnd(LocalDateTime.now());
        v.setOutcomeType(type);
        v.setOutcome(cleanNote);
        v.setConsultedBy(user.getUsername());
        inConsultation.get(v.getService()).remove(v.getVisitId());
        activeQueueNumbers.remove(v.getQueueNumber());

        log(user, "RECORD_OUTCOME", v, v.getQueueNumber() + " " + type.name() + " (" + v.getStatus() + "), consult "
                + v.getConsultMinutes() + " min");
        return v;
    }

    // ================================================================== visit status and tracking (Milestone 4)

    /** Finds a visit by its ID for the status screens. */
    public Visit trackVisit(User user, String visitId) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_VISIT_STATUS);
        Visit v = visits.get(visitId);
        if (v == null) throw new InvalidInputException("No visit has the ID " + visitId + ".");
        return v;
    }

    /** All visits of one patient, newest first (uses the visit IDs saved in the patient record). */
    public ArrayList<Visit> trackPatientVisits(User user, String patientId)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_VISIT_STATUS);
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        ArrayList<Visit> list = new ArrayList<>();
        for (String id : p.getVisitIds()) {
            Visit v = visits.get(id);
            if (v != null) list.add(v);
        }
        list.sort((a, b) -> b.getArrivalTime().compareTo(a.getArrivalTime()));
        return list;
    }

    /**
     * Number of visits in each status for one service. A waiting visit that is being called
     * is counted as WAITING here (the board shows "now calling" on its own line). O(n).
     */
    public EnumMap<VisitStatus, Integer> statusCounts(User user, Service service) throws AccessDeniedException {
        auth.require(user, Permission.VIEW_VISIT_STATUS);
        EnumMap<VisitStatus, Integer> counts = new EnumMap<>(VisitStatus.class);
        for (VisitStatus s : VisitStatus.values()) counts.put(s, 0);
        for (Visit v : visits.values()) {
            if (v.getService() == service) counts.put(v.getStatus(), counts.get(v.getStatus()) + 1);
        }
        return counts;
    }

    /** Average minutes from arrival to the start of the consultation, or -1 if no consultation has started. */
    public double averageWaitingMinutes(Service service) {
        long total = 0;
        int n = 0;
        for (Visit v : visits.values()) {
            if (v.getService() == service && v.getConsultStart() != null) { total += v.getWaitingMinutes(); n++; }
        }
        return n == 0 ? -1 : (double) total / n;
    }

    /** Average minutes of finished consultations, or -1 if none has finished. */
    public double averageConsultMinutes(Service service) {
        long total = 0;
        int n = 0;
        for (Visit v : visits.values()) {
            if (v.getService() == service && v.getConsultEnd() != null) { total += v.getConsultMinutes(); n++; }
        }
        return n == 0 ? -1 : (double) total / n;
    }

    private void log(User user, String action, Visit v, String details) {
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), action, v.getVisitId(), details));
    }

    // ================================================================== lookup

    public Visit findById(String visitId) { return visits.get(visitId); }

    public ArrayList<Visit> getAllVisits() { return new ArrayList<>(visits.values()); }

    public int count() { return visits.size(); }

    // ================================================================== loading

    /** Adds a visit read from file (no permission check, no audit entry). */
    public void loadVisit(Visit v) {
        visits.put(v.getVisitId(), v);
        if (v.getVisitId().length() > 1) {
            int n = Integer.parseInt(v.getVisitId().substring(1));
            if (n > lastVisitNumber) lastVisitNumber = n;
        }
        if (v.getArrivalOrder() > lastArrivalOrder) lastArrivalOrder = v.getArrivalOrder();

        if (isActive(v.getStatus()) && !v.getQueueNumber().isEmpty()) {
            activeQueueNumbers.add(v.getQueueNumber());
            int dash = v.getQueueNumber().indexOf('-');
            if (dash > 0) {
                int seq = Integer.parseInt(v.getQueueNumber().substring(dash + 1));
                if (seq > lastSequence.get(v.getService())) lastSequence.put(v.getService(), seq);
            }
        }
    }

    /** Puts every WAITING visit back in its line, in arrival order. Call once after all visits are loaded. */
    public void rebuildQueues() {
        ArrayList<Visit> waiting = new ArrayList<>();
        for (Visit v : visits.values()) if (v.getStatus() == VisitStatus.WAITING) waiting.add(v);
        waiting.sort((a, b) -> Long.compare(a.getArrivalOrder(), b.getArrivalOrder()));   // restores FIFO order only
        for (Visit v : waiting) enqueue(v);

        ArrayList<Visit> consulting = new ArrayList<>();                                  // consultations in progress
        for (Visit v : visits.values()) {
            if (v.getStatus() != VisitStatus.IN_CONSULTATION) continue;
            if (v.getConsultStart() == null) v.setConsultStart(v.getArrivalTime());       // safety for a damaged file
            consulting.add(v);
        }
        consulting.sort((a, b) -> a.getConsultStart().compareTo(b.getConsultStart()));
        for (Visit v : consulting) inConsultation.get(v.getService()).put(v.getVisitId(), v);
    }
}