package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.ds.VersionStack;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.RecordField;
import medflow.model.RecordVersion;
import medflow.model.User;
import medflow.util.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Record updates with version history (Figure 6, Milestone 5).
 *
 *   history   HashMap<patientId, VersionStack<RecordVersion>>   O(1) average to find the stack of a record
 *   push      O(1)   the old version goes on top before every change, so nothing is overwritten
 *   peek      O(1)   the latest change, used for "undo last change"
 *   list      O(v)   all versions of one record, newest first (v = number of versions)
 *
 * Every change also adds an entry to the audit log (linked list, O(1)).
 * A change is never deleted. Undoing a change saves a new version that puts the old value back.
 */
public class RecordHistory {

    private final HashMap<String, VersionStack<RecordVersion>> history = new HashMap<>();
    private final AuthService auth;
    private final PatientService patients;
    private final AuditLog auditLog;

    public RecordHistory(AuthService auth, PatientService patients, AuditLog auditLog) {
        this.auth = auth;
        this.patients = patients;
        this.auditLog = auditLog;
    }

    // ================================================================== reading and checking a field

    /** The current value of a field, as text. */
    public static String valueOf(Patient p, RecordField field) {
        switch (field) {
            case LAST_NAME:        return p.getLastName();
            case FIRST_NAME:       return p.getFirstName();
            case MIDDLE_NAME:      return p.getMiddleName();
            case BIRTHDATE:        return p.getBirthdate().toString();
            case SEX:              return p.getSex().getLabel();
            case ADDRESS:          return p.getAddress();
            case CONTACT_NUMBER:   return p.getContactNumber();
            case ALLERGIES:        return p.getAllergies();
            default:               return p.getRelevantHistory();
        }
    }

    /** Checks a typed value with the same rules used at registration. Returns the cleaned value. */
    public static String clean(RecordField field, String input) throws InvalidInputException {
        switch (field) {
            case LAST_NAME:        return Validator.validateName(input, "Last name", true);
            case FIRST_NAME:       return Validator.validateName(input, "First name", true);
            case MIDDLE_NAME:      return Validator.validateName(input, "Middle name", false);
            case BIRTHDATE:        return Validator.validateBirthdate(input, LocalDate.now()).toString();
            case SEX:              return Validator.validateSex(input).getLabel();
            case ADDRESS:          return Validator.validateAddress(input);
            case CONTACT_NUMBER:   return Validator.validateContactNumber(input);
            case ALLERGIES:        return Validator.validateClinicalText(input, "Allergies");
            default:               return Validator.validateClinicalText(input, "Relevant history");
        }
    }

    /** Writes a cleaned value into the record. */
    private static void apply(Patient p, RecordField field, String value) throws InvalidInputException {
        switch (field) {
            case LAST_NAME:        p.setLastName(value); break;
            case FIRST_NAME:       p.setFirstName(value); break;
            case MIDDLE_NAME:      p.setMiddleName(value); break;
            case BIRTHDATE:        p.setBirthdate(LocalDate.parse(value)); break;
            case SEX:              p.setSex(Validator.validateSex(value)); break;
            case ADDRESS:          p.setAddress(value); break;
            case CONTACT_NUMBER:   p.setContactNumber(value); break;
            case ALLERGIES:        p.setAllergies(value); break;
            default:               p.setRelevantHistory(value); break;
        }
    }

    private VersionStack<RecordVersion> stackOf(String patientId) {
        VersionStack<RecordVersion> stack = history.get(patientId);
        if (stack == null) {
            stack = new VersionStack<>();
            history.put(patientId, stack);
        }
        return stack;
    }

    private Patient activeRecord(String patientId) throws InvalidInputException {
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        if (p.isMerged()) {
            throw new InvalidInputException(p.getPatientId() + " was merged into " + p.getMergedInto()
                    + ". Update that record instead.");
        }
        return p;
    }

    // ================================================================== updating (Figure 6)

    /**
     * Changes one field of a patient record. Steps follow the updateRecord pseudocode:
     * check the role, check the new value, push a copy of the record on the version stack,
     * set the new value, and add an entry to the audit log.
     * Returns the version that was pushed (the record as it was before the change).
     */
    public RecordVersion updateField(User user, String patientId, RecordField field, String input)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, field.getEditPermission());
        Patient p = activeRecord(patientId);
        String newValue = clean(field, input);
        return change(user, p, field, newValue, RecordVersion.Action.UPDATE);
    }

    private RecordVersion change(User user, Patient p, RecordField field, String newValue, RecordVersion.Action action)
            throws InvalidInputException {
        String oldValue = valueOf(p, field);
        if (oldValue.equals(newValue)) {
            throw new InvalidInputException("The new " + field.getLabel().toLowerCase() + " is the same as the current one.");
        }
        String oldLast = p.getLastName(), oldFirst = p.getFirstName();
        LocalDate oldBirth = p.getBirthdate();

        VersionStack<RecordVersion> stack = stackOf(p.getPatientId());
        LocalDateTime now = LocalDateTime.now();
        RecordVersion saved = RecordVersion.copyOf(p, stack.size() + 1, field, oldValue, newValue,
                user.getUsername(), now, action);
        stack.push(saved);                                     // the old version goes on top: O(1)
        apply(p, field, newValue);                             // then the record changes

        if (field.isIdentity()) patients.afterIdentityChange(user, p, oldLast, oldFirst, oldBirth);

        String details = field.isClinical()
                ? field.getLabel() + " changed (clinical, value not shown in the log)"
                : field.getLabel() + ": \"" + oldValue + "\" -> \"" + newValue + "\"";
        String what = action == RecordVersion.Action.RESTORE ? "RESTORE_VERSION" : "UPDATE_RECORD";
        auditLog.add(new AuditEntry(now, user.getUsername(), what, p.getPatientId(), details));
        return saved;
    }

    // ================================================================== version history

    /** All saved versions of a record, newest first. Records Staff only. */
    public ArrayList<RecordVersion> getHistory(User user, String patientId)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_VERSION_HISTORY);
        Patient p = patients.findById(patientId);
        if (p == null) throw new InvalidInputException("No patient has the ID " + patientId + ".");
        VersionStack<RecordVersion> stack = history.get(p.getPatientId());
        return stack == null ? new ArrayList<>() : stack.toListNewestFirst();
    }

    /** The newest saved version (the change made last), or null if the record was never changed. O(1). */
    public RecordVersion latestChange(String patientId) {
        VersionStack<RecordVersion> stack = history.get(patientId);
        return stack == null ? null : stack.peek();
    }

    /** Number of the current version: 1 if the record was never changed. */
    public int currentVersion(String patientId) {
        VersionStack<RecordVersion> stack = history.get(patientId);
        return stack == null ? 1 : stack.size() + 1;
    }

    /** Finds a saved version by its number, going down the stack. O(v). */
    public RecordVersion findVersion(String patientId, int versionNumber) {
        VersionStack<RecordVersion> stack = history.get(patientId);
        if (stack == null) return null;
        for (RecordVersion v : stack) if (v.getVersionNumber() == versionNumber) return v;
        return null;
    }

    /**
     * Undoes the change that replaced the given version: the field goes back to its old value.
     * Nothing is popped or deleted. The undo itself is saved as a new version, so it can be undone too.
     * The user needs the edit permission of that field.
     */
    public RecordVersion restore(User user, String patientId, int versionNumber)
            throws AccessDeniedException, InvalidInputException {
        auth.require(user, Permission.VIEW_VERSION_HISTORY);
        Patient p = activeRecord(patientId);
        RecordVersion target = findVersion(p.getPatientId(), versionNumber);
        if (target == null) throw new InvalidInputException(p.getPatientId() + " has no saved version " + versionNumber + ".");
        RecordField field = target.getChangedField();
        auth.require(user, field.getEditPermission());
        return change(user, p, field, target.getOldValue(), RecordVersion.Action.RESTORE);
    }

    /** Undoes the newest change (the version on top of the stack). */
    public RecordVersion undoLatest(User user, String patientId) throws AccessDeniedException, InvalidInputException {
        RecordVersion top = latestChange(patientId);
        if (top == null) throw new InvalidInputException(patientId + " has not been changed since it was registered.");
        return restore(user, patientId, top.getVersionNumber());
    }

    // ================================================================== loading and saving

    /** Adds a version read from file. Versions must be loaded oldest first, so the newest ends on top. */
    public void loadVersion(RecordVersion v) { stackOf(v.getPatientId()).push(v); }

    /** Every saved version of every record, oldest first per record (the order needed to load them back). */
    public ArrayList<RecordVersion> getAllVersionsOldestFirst() {
        ArrayList<RecordVersion> all = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>(history.keySet());
        ids.sort(String::compareTo);                       // only to keep the file tidy
        for (String id : ids) {
            ArrayList<RecordVersion> newestFirst = history.get(id).toListNewestFirst();
            for (int i = newestFirst.size() - 1; i >= 0; i--) all.add(newestFirst.get(i));
        }
        return all;
    }
}
