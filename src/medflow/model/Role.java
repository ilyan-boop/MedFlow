package medflow.model;

/** The five staff roles that can log in to MedFlow. Patients do not log in. */
public enum Role {
    RECEPTIONIST("Receptionist"),
    NURSE("Nurse"),
    CLINICIAN("Clinician"),
    RECORDS_STAFF("Records Staff"),
    ADMINISTRATOR("Administrator");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }
}
