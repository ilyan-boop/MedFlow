package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Visit creation (Figure 3) and calling patients in the queue (Figure 4).
 *
 *   visits              HashMap<visitId, Visit>             O(1) average lookup by visit ID
 *   activeQueueNumbers  HashSet<queue number>               O(1) average "is this number in use?" check
 *   priorityLane        per service, ArrayDeque (queue)     enqueue and dequeue in O(1)
 *   regularLine         per service, ArrayDeque (queue)     enqueue and dequeue in O(1)
 *   nowCalling          per service, the patient who was called and has not answered yet
 *
 * A called patient stays WAITING until staff record that the patient came (IN_CONSULTATION),
 * did not come (back of the line, or NO_SHOW on the second miss), or was cancelled.
 */
public class VisitService {

    private static final int MAX_SEQUENCE = 999;
    /** A patient who misses this many calls becomes a no-show. */
    public static final int MAX_MISSED = 2;

    private final HashMap<String, Visit> visits = new HashMap<>();
    private final HashSet<String> activeQueueNumbers = new HashSet<>();
    private final EnumMap<Service, ArrayDeque<Visit>> priorityLane = new EnumMap<>(Service.class);
    private final EnumMap<Service, ArrayDeque<Visit>> regularLine = new EnumMap<>(Service.class);
    private final EnumMap<Service, Visit> nowCalling = new EnumMap<>(Service.class);
    private final EnumMap<Service, Integer> lastSequence = new EnumMap<>(Service.class);

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
            priorityLane.put(s, new ArrayDeque<>());
            regularLine.put(s, new ArrayDeque<>());
            lastSequence.put(s, 0);
        }
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
        v.setStatus(VisitStatus.WAITING);

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
        if (v.getPriorityCategory().isPriority()) priorityLane.get(v.getService()).addLast(v);
        else regularLine.get(v.getService()).addLast(v);
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
        list.addAll(priorityLane.get(service));
        list.addAll(regularLine.get(service));
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
        Visit next = priorityLane.get(service).pollFirst();
        if (next == null) next = regularLine.get(service).pollFirst();
        if (next == null) return null;

        nowCalling.put(service, next);
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
            v.setStatus(VisitStatus.NO_SHOW);
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
     * The called patient came. Moves the visit to IN_CONSULTATION so the queue can continue.
     * (The consultation record itself is built in a later milestone.)
     */
    public Visit markArrived(User user, Service service) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.MANAGE_QUEUE);
        Visit v = nowCalling.remove(service);
        if (v == null) throw new InvalidInputException("No patient is being called in " + service.getLabel() + ".");
        v.setStatus(VisitStatus.IN_CONSULTATION);
        v.setConsultStart(LocalDateTime.now());
        log(user, "START_CONSULT", v, v.getQueueNumber() + " came to the clinician");
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

        v.setStatus(VisitStatus.CANCELLED);
        activeQueueNumbers.remove(v.getQueueNumber());
        log(user, "CANCEL_VISIT", v, v.getQueueNumber() + " cancelled");
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
    }
}