package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.Sex;
import medflow.model.User;
import medflow.util.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Patient registration, lookup, and duplicate checking.
 *
 *   patients        HashMap<patientId, Patient>                O(1) average lookup by ID
 *   duplicateIndex  HashMap<name+birthdate key, list of IDs>   O(1) average duplicate check
 *   duplicateFlags  ArrayList<DuplicateFlag>                   possible duplicates for records staff
 */
public class PatientService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HashMap<String, Patient> patients = new HashMap<>();
    private final HashMap<String, ArrayList<String>> duplicateIndex = new HashMap<>();
    private final ArrayList<DuplicateFlag> duplicateFlags = new ArrayList<>();
    private final AuthService auth;
    private final AuditLog auditLog;
    private int lastNumber = 0;

    public PatientService(AuthService auth, AuditLog auditLog) {
        this.auth = auth;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------ duplicate key

    /**
     * Builds the duplicate-check key from last name, first name, and birthdate.
     * Only letters are kept and all are lowercase, so "Dela Cruz", "dela cruz", and "De La Cruz"
     * give the same key. Middle names are left out because they are often typed differently.
     */
    public static String duplicateKey(String lastName, String firstName, LocalDate birthdate) {
        return lettersOnly(lastName) + "|" + lettersOnly(firstName) + "|" + birthdate;
    }

    private static String lettersOnly(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private void addToIndex(Patient p) {
        String key = duplicateKey(p.getLastName(), p.getFirstName(), p.getBirthdate());
        ArrayList<String> ids = duplicateIndex.get(key);
        if (ids == null) {
            ids = new ArrayList<>();
            duplicateIndex.put(key, ids);
        }
        ids.add(p.getPatientId());
    }

    private void removeFromIndex(Patient p) {
        String key = duplicateKey(p.getLastName(), p.getFirstName(), p.getBirthdate());
        ArrayList<String> ids = duplicateIndex.get(key);
        if (ids != null) {
            ids.remove(p.getPatientId());
            if (ids.isEmpty()) duplicateIndex.remove(key);
        }
    }

    /** Active (not merged) records with the same name and birthdate. O(1) average to find the list. */
    public ArrayList<Patient> findPossibleDuplicates(String lastName, String firstName, LocalDate birthdate) {
        ArrayList<Patient> result = new ArrayList<>();
        ArrayList<String> ids = duplicateIndex.get(duplicateKey(lastName, firstName, birthdate));
        if (ids != null) {
            for (String id : ids) {
                Patient p = patients.get(id);
                if (p != null && !p.isMerged()) result.add(p);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ registration

    /** Next unused patient ID, e.g. P0001. */
    private String nextPatientId() {
        String id;
        do {
            lastNumber++;
            id = String.format("P%04d", lastNumber);
        } while (patients.containsKey(id));     // never reuse an ID that already exists
        return id;
    }

    /**
     * Creates a new patient record. If records with the same name and birthdate already exist,
     * the new record is still saved, and each match is flagged for records staff to review and merge.
     */
    public Patient registerNew(User user, String lastName, String firstName, String middleName,
                               LocalDate birthdate, Sex sex, String address, String contactNumber)
            throws AccessDeniedException {
        auth.require(user, Permission.REGISTER_PATIENT);

        ArrayList<Patient> matches = findPossibleDuplicates(lastName, firstName, birthdate);
        LocalDateTime now = LocalDateTime.now();

        Patient p = new Patient(nextPatientId(), lastName, firstName, middleName, birthdate, sex,
                address, contactNumber, user.getUsername(), now);
        patients.put(p.getPatientId(), p);
        addToIndex(p);
        auditLog.add(new AuditEntry(now, user.getUsername(), "REGISTER_PATIENT", p.getPatientId(),
                p.getFullName()));

        for (Patient match : matches) {
            duplicateFlags.add(new DuplicateFlag(p.getPatientId(), match.getPatientId(), user.getUsername(), now));
            auditLog.add(new AuditEntry(now, user.getUsername(), "FLAG_DUPLICATE", p.getPatientId(),
                    "Possible duplicate of " + match.getPatientId()));
        }
        return p;
    }

    /** The receptionist confirmed that the patient is already registered, so the old ID is used. */
    public Patient useExisting(User user, String patientId) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.REGISTER_PATIENT);
        Patient p = findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        if (p.isMerged()) p = findById(p.getMergedInto());
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "USE_EXISTING_RECORD",
                p.getPatientId(), p.getFullName()));
        return p;
    }

    // ------------------------------------------------------------------ lookup

    /** O(1) average lookup by patient ID. Returns null if not found. */
    public Patient findById(String patientId) {
        if (patientId == null) return null;
        return patients.get(patientId.trim().toUpperCase());
    }

    /**
     * Partial name search (linear search, O(n)), e.g. "dela" finds "Dela Cruz, Juan".
     * Merged records are skipped. Sorted search with merge sort and binary search comes in Milestone 6.
     */
    public ArrayList<Patient> searchByName(User user, String query) throws AccessDeniedException {
        auth.require(user, Permission.SEARCH_PATIENT);
        String q = lettersOnly(query);
        ArrayList<Patient> result = new ArrayList<>();
        if (q.isEmpty()) return result;
        for (Patient p : patients.values()) {
            if (p.isMerged()) continue;
            String full = lettersOnly(p.getLastName() + p.getFirstName() + p.getMiddleName());
            String firstLast = lettersOnly(p.getFirstName() + p.getLastName());
            if (full.contains(q) || firstLast.contains(q)) result.add(p);
        }
        return result;
    }

    /**
     * Text view of a patient record. Clinical information is shown only to roles that have
     * VIEW_CLINICAL_NOTES (Nurse and Clinician).
     */
    public String viewRecord(User user, String patientId) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_PATIENT_RECORD);
        Patient p = findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");

        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("Patient ID     : ").append(p.getPatientId()).append('\n');
        if (p.isMerged()) {
            sb.append("NOTE           : This record was merged into ").append(p.getMergedInto())
              .append(". Use that ID for new visits.\n");
        }
        sb.append("Name           : ").append(p.getFullName()).append('\n');
        sb.append("Birthdate      : ").append(p.getBirthdate()).append("  (age ").append(p.getAge(today)).append(")\n");
        sb.append("Sex            : ").append(p.getSex().getLabel()).append('\n');
        sb.append("Address        : ").append(p.getAddress()).append('\n');
        sb.append("Contact number : ").append(p.getContactNumber()).append('\n');
        sb.append("Eligible for   : ").append(Validator.eligibleServices(p.getAge(today))).append('\n');
        sb.append("Visits         : ").append(p.getVisitIds().isEmpty() ? "none yet" : String.join(", ", p.getVisitIds())).append('\n');
        sb.append("Registered by  : ").append(p.getCreatedBy()).append(" on ").append(p.getCreatedAt().format(TIME)).append('\n');
        if (auth.can(user, Permission.VIEW_CLINICAL_NOTES)) {
            sb.append("Allergies      : ").append(p.getAllergies().isEmpty() ? "none recorded" : p.getAllergies()).append('\n');
            sb.append("History        : ").append(p.getRelevantHistory().isEmpty() ? "none recorded" : p.getRelevantHistory()).append('\n');
        } else {
            sb.append("Clinical notes : hidden for the ").append(user.getRole().getLabel()).append(" role\n");
        }
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "VIEW_RECORD", p.getPatientId(), ""));
        return sb.toString();
    }

    // ------------------------------------------------------------------ duplicate review (records staff)

    public ArrayList<DuplicateFlag> getPendingFlags(User user) throws AccessDeniedException {
        auth.require(user, Permission.RESOLVE_DUPLICATES);
        ArrayList<DuplicateFlag> pending = new ArrayList<>();
        for (DuplicateFlag f : duplicateFlags) {
            if (f.getStatus() == DuplicateFlag.Status.PENDING) pending.add(f);
        }
        return pending;
    }

    /**
     * Merges the newer record into the existing (older) one. Nothing is deleted: the newer record
     * is kept and marked as merged, and its visits move to the existing record.
     */
    public void merge(User user, DuplicateFlag flag) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.RESOLVE_DUPLICATES);
        if (flag.getStatus() != DuplicateFlag.Status.PENDING) {
            throw new InvalidInputException("This flag was already resolved.");
        }
        Patient keep = findById(flag.getExistingPatientId());
        Patient old = findById(flag.getNewPatientId());
        if (keep == null || old == null) throw new InvalidInputException("One of the records no longer exists.");
        if (keep.isMerged()) throw new InvalidInputException(keep.getPatientId() + " was already merged into "
                + keep.getMergedInto() + ". Review that record instead.");

        for (String visitId : old.getVisitIds()) keep.getVisitIds().add(visitId);
        old.getVisitIds().clear();
        old.setMergedInto(keep.getPatientId());
        removeFromIndex(old);
        flag.resolve(DuplicateFlag.Status.MERGED, user.getUsername());

        // Other pending flags about the merged record are closed too.
        for (DuplicateFlag f : duplicateFlags) {
            if (f.getStatus() == DuplicateFlag.Status.PENDING
                    && (f.getNewPatientId().equals(old.getPatientId()) || f.getExistingPatientId().equals(old.getPatientId()))) {
                f.resolve(DuplicateFlag.Status.MERGED, user.getUsername());
            }
        }
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "MERGE_RECORDS", old.getPatientId(),
                "Merged into " + keep.getPatientId()));
    }

    /** Records staff decided the two records are different people. Both are kept. */
    public void keepBoth(User user, DuplicateFlag flag) throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.RESOLVE_DUPLICATES);
        if (flag.getStatus() != DuplicateFlag.Status.PENDING) {
            throw new InvalidInputException("This flag was already resolved.");
        }
        flag.resolve(DuplicateFlag.Status.KEPT_BOTH, user.getUsername());
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "KEEP_BOTH_RECORDS",
                flag.getNewPatientId(), "Not the same patient as " + flag.getExistingPatientId()));
    }

    // ------------------------------------------------------------------ record updates (Milestone 5)

    /**
     * Called by RecordHistory after the last name, first name, or birthdate of a record changed.
     * Moves the record to its new duplicate key, and flags any other active record that now has
     * the same name and birthdate so records staff can review it.
     */
    public void afterIdentityChange(User user, Patient p, String oldLast, String oldFirst, LocalDate oldBirthdate) {
        String oldKey = duplicateKey(oldLast, oldFirst, oldBirthdate);
        ArrayList<String> ids = duplicateIndex.get(oldKey);
        if (ids != null) {
            ids.remove(p.getPatientId());
            if (ids.isEmpty()) duplicateIndex.remove(oldKey);
        }
        if (p.isMerged()) return;
        ArrayList<Patient> matches = findPossibleDuplicates(p.getLastName(), p.getFirstName(), p.getBirthdate());
        addToIndex(p);
        LocalDateTime now = LocalDateTime.now();
        for (Patient match : matches) {
            duplicateFlags.add(new DuplicateFlag(p.getPatientId(), match.getPatientId(), user.getUsername(), now));
            auditLog.add(new AuditEntry(now, user.getUsername(), "FLAG_DUPLICATE", p.getPatientId(),
                    "Possible duplicate of " + match.getPatientId() + " after a record update"));
        }
    }

    // ------------------------------------------------------------------ loading and saving

    /** Adds a patient read from file (no permission check, no audit entry). */
    public void loadPatient(Patient p) {
        patients.put(p.getPatientId(), p);
        if (!p.isMerged()) addToIndex(p);
        int number = Integer.parseInt(p.getPatientId().substring(1));
        if (number > lastNumber) lastNumber = number;
    }

    public void loadFlag(DuplicateFlag f) { duplicateFlags.add(f); }

    public ArrayList<Patient> getAllPatients() { return new ArrayList<>(patients.values()); }

    public ArrayList<DuplicateFlag> getAllFlags() { return duplicateFlags; }

    public int count() { return patients.size(); }
}
