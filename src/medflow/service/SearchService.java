package medflow.service;

import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.User;
import medflow.model.Visit;
import medflow.util.SearchUtil;
import medflow.util.SortUtil;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Search by patient ID, visit ID, queue number, or patient name (Section 5.2, item 9 of the proposal).
 *
 *   Patient ID, visit ID   HashMap lookup in PatientService and VisitService       O(1) average
 *   Patient name           list sorted by name with merge sort, then binary search  O(log n + m)
 *                          for names that start with the text. If none start with it,
 *                          linear search finds the text anywhere in the name         O(n)
 *   Queue number           list sorted by queue number with merge sort, then        O(log n + m)
 *                          binary search. Numbers can repeat on different days, so
 *                          the items next to the match are checked too.
 *
 * The sorted lists are kept and sorted again only when patients or visits change, so most
 * searches skip the O(n log n) sort.
 */
public class SearchService {

    /** Order of the name list: last name and first name (letters only), then middle name, then patient ID. */
    public static final Comparator<Patient> BY_NAME = (a, b) -> {
        int c = nameKey(a).compareTo(nameKey(b));
        if (c != 0) return c;
        c = lettersOnly(a.getMiddleName()).compareTo(lettersOnly(b.getMiddleName()));
        if (c != 0) return c;
        return a.getPatientId().compareTo(b.getPatientId());
    };

    /** Order of the queue number list: queue number, then arrival time. */
    public static final Comparator<Visit> BY_QUEUE_NUMBER = (a, b) -> {
        int c = a.getQueueNumber().compareTo(b.getQueueNumber());
        if (c != 0) return c;
        return a.getArrivalTime().compareTo(b.getArrivalTime());
    };

    private final AuthService auth;
    private final PatientService patients;
    private final VisitService visits;

    private ArrayList<Patient> byName = new ArrayList<>();
    private int nameListVersion = -1;
    private ArrayList<Visit> byQueueNumber = new ArrayList<>();
    private int queueListSize = -1;
    private boolean lastNameSearchWasPartial = false;

    public SearchService(AuthService auth, PatientService patients, VisitService visits) {
        this.auth = auth;
        this.patients = patients;
        this.visits = visits;
    }

    /** Last name and first name in lowercase letters only: "Dela Cruz", "Juan" gives "delacruzjuan". */
    public static String nameKey(Patient p) {
        return lettersOnly(p.getLastName()) + lettersOnly(p.getFirstName());
    }

    static String lettersOnly(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ================================================================== sorted lists

    /** Sorts the name list again with merge sort only if a patient was added, merged, or renamed. */
    private void refreshNameList() {
        if (nameListVersion == patients.getChangeCount()) return;
        ArrayList<Patient> list = new ArrayList<>();
        for (Patient p : patients.getAllPatients()) if (!p.isMerged()) list.add(p);
        SortUtil.mergeSort(list, BY_NAME);
        byName = list;
        nameListVersion = patients.getChangeCount();
    }

    /** Sorts the queue number list again only if visits were added. Queue numbers never change. */
    private void refreshQueueList() {
        if (queueListSize == visits.count()) return;
        ArrayList<Visit> list = visits.getAllVisits();
        SortUtil.mergeSort(list, BY_QUEUE_NUMBER);
        byQueueNumber = list;
        queueListSize = visits.count();
    }

    /** Every patient (merged records left out), sorted by name. */
    public ArrayList<Patient> allPatientsByName(User user) throws AccessDeniedException {
        auth.require(user, Permission.SEARCH_PATIENT);
        refreshNameList();
        return new ArrayList<>(byName);
    }

    // ================================================================== searches

    /** O(1) average lookup by patient ID. */
    public Patient findPatient(User user, String patientId) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.SEARCH_PATIENT);
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId.trim().toUpperCase() + ".");
        return p;
    }

    /**
     * Name search. Binary search lists the names that start with the text ("dela", "santos lou").
     * If no name starts with it, linear search finds the text anywhere in the name ("juan", "clara").
     * Results are in name order. Merged records are left out.
     */
    public ArrayList<Patient> searchByName(User user, String query) throws AccessDeniedException {
        auth.require(user, Permission.SEARCH_PATIENT);
        lastNameSearchWasPartial = false;
        String q = lettersOnly(query);
        if (q.isEmpty()) return new ArrayList<>();
        refreshNameList();

        ArrayList<Patient> found = SearchUtil.allMatches(byName, q, (p, key) -> SearchUtil.comparePrefix(nameKey(p), key));
        if (!found.isEmpty()) return found;

        lastNameSearchWasPartial = true;
        return SearchUtil.linearSearch(byName, p -> {
            String full = nameKey(p) + lettersOnly(p.getMiddleName());
            String firstLast = lettersOnly(p.getFirstName()) + lettersOnly(p.getLastName());
            return full.contains(q) || firstLast.contains(q);
        });
    }

    /** True if the last name search found nothing by binary search and used linear search instead. */
    public boolean lastNameSearchWasPartial() { return lastNameSearchWasPartial; }

    /** O(1) average lookup by visit ID. */
    public Visit findVisit(User user, String visitId) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_VISIT_STATUS);
        Visit v = visits.findById(visitId.trim().toUpperCase());
        if (v == null) throw new InvalidInputException("No visit has the ID " + visitId.trim().toUpperCase() + ".");
        return v;
    }

    /** Every visit with this queue number, newest first. Binary search on the sorted queue number list. */
    public ArrayList<Visit> findByQueueNumber(User user, String queueNumber) throws AccessDeniedException {
        auth.require(user, Permission.VIEW_VISIT_STATUS);
        refreshQueueList();
        String key = queueNumber.trim().toUpperCase();
        ArrayList<Visit> found = SearchUtil.allMatches(byQueueNumber, key, (v, k) -> v.getQueueNumber().compareTo(k));
        ArrayList<Visit> newestFirst = new ArrayList<>();
        for (int i = found.size() - 1; i >= 0; i--) newestFirst.add(found.get(i));
        return newestFirst;
    }
}
