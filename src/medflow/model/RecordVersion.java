package medflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One saved version of a patient record. It is a copy of the record as it was BEFORE a change,
 * together with what changed, who changed it, and when. Version 1 is the record as first registered.
 */
public class RecordVersion {

    /** UPDATE for a normal change, RESTORE when a change was undone from the version history. */
    public enum Action { UPDATE, RESTORE }

    private final String patientId;
    private final int versionNumber;

    // copy of the record before the change
    private final String lastName;
    private final String firstName;
    private final String middleName;
    private final LocalDate birthdate;
    private final Sex sex;
    private final String address;
    private final String contactNumber;
    private final String allergies;
    private final String relevantHistory;

    // the change that replaced this version
    private final RecordField changedField;
    private final String oldValue;
    private final String newValue;
    private final String changedBy;
    private final LocalDateTime changedAt;
    private final Action action;

    public RecordVersion(String patientId, int versionNumber, String lastName, String firstName, String middleName,
                         LocalDate birthdate, Sex sex, String address, String contactNumber, String allergies,
                         String relevantHistory, RecordField changedField, String oldValue, String newValue,
                         String changedBy, LocalDateTime changedAt, Action action) {
        this.patientId = patientId;
        this.versionNumber = versionNumber;
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.birthdate = birthdate;
        this.sex = sex;
        this.address = address;
        this.contactNumber = contactNumber;
        this.allergies = allergies;
        this.relevantHistory = relevantHistory;
        this.changedField = changedField;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.action = action;
    }

    /** Copies the current state of a patient record before a change. */
    public static RecordVersion copyOf(Patient p, int versionNumber, RecordField field, String oldValue,
                                       String newValue, String changedBy, LocalDateTime changedAt, Action action) {
        return new RecordVersion(p.getPatientId(), versionNumber, p.getLastName(), p.getFirstName(), p.getMiddleName(),
                p.getBirthdate(), p.getSex(), p.getAddress(), p.getContactNumber(), p.getAllergies(),
                p.getRelevantHistory(), field, oldValue, newValue, changedBy, changedAt, action);
    }

    /** "Last, First Middle" as it was in this version. */
    public String getFullName() {
        return lastName + ", " + firstName + (middleName.isEmpty() ? "" : " " + middleName);
    }

    public String getPatientId() { return patientId; }
    public int getVersionNumber() { return versionNumber; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public LocalDate getBirthdate() { return birthdate; }
    public Sex getSex() { return sex; }
    public String getAddress() { return address; }
    public String getContactNumber() { return contactNumber; }
    public String getAllergies() { return allergies; }
    public String getRelevantHistory() { return relevantHistory; }
    public RecordField getChangedField() { return changedField; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public Action getAction() { return action; }
}
