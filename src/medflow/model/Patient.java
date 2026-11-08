package medflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;

/** A patient record. One patient can have many visits. */
public class Patient {
    private final String patientId;
    private String lastName;
    private String firstName;
    private String middleName;          // may be empty
    private LocalDate birthdate;
    private Sex sex;
    private String address;
    private String contactNumber;

    // Clinical information: only roles with VIEW_CLINICAL_NOTES can see these
    private String allergies = "";
    private String relevantHistory = "";

    private final String createdBy;
    private final LocalDateTime createdAt;

    // If records staff merge this record into another one, the old record is kept
    // (never deleted) and points to the record that replaced it.
    private String mergedInto = "";

    private final ArrayList<String> visitIds = new ArrayList<>();

    public Patient(String patientId, String lastName, String firstName, String middleName,
                   LocalDate birthdate, Sex sex, String address, String contactNumber,
                   String createdBy, LocalDateTime createdAt) {
        this.patientId = patientId;
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.birthdate = birthdate;
        this.sex = sex;
        this.address = address;
        this.contactNumber = contactNumber;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** Age in completed years on a given date. */
    public int getAge(LocalDate onDate) { return Period.between(birthdate, onDate).getYears(); }

    /** Name in "Last, First Middle" form, used for display and sorting. */
    public String getFullName() {
        String name = lastName + ", " + firstName;
        if (!middleName.isEmpty()) name += " " + middleName;
        return name;
    }

    public boolean isMerged() { return !mergedInto.isEmpty(); }

    public String getPatientId() { return patientId; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public LocalDate getBirthdate() { return birthdate; }
    public Sex getSex() { return sex; }
    public String getAddress() { return address; }
    public String getContactNumber() { return contactNumber; }
    public String getAllergies() { return allergies; }
    public String getRelevantHistory() { return relevantHistory; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getMergedInto() { return mergedInto; }
    public ArrayList<String> getVisitIds() { return visitIds; }

    public void setAllergies(String allergies) { this.allergies = allergies; }
    public void setRelevantHistory(String relevantHistory) { this.relevantHistory = relevantHistory; }
    public void setMergedInto(String mergedInto) { this.mergedInto = mergedInto; }
}
