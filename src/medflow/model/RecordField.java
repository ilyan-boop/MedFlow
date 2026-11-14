package medflow.model;

/**
 * The parts of a patient record that can be updated, and the permission each one needs.
 * Personal details are corrected by Records Staff. Clinical information is updated by Clinicians.
 */
public enum RecordField {
    LAST_NAME("Last name", false),
    FIRST_NAME("First name", false),
    MIDDLE_NAME("Middle name", false),
    BIRTHDATE("Birthdate", false),
    SEX("Sex", false),
    ADDRESS("Address", false),
    CONTACT_NUMBER("Contact number", false),
    ALLERGIES("Allergies", true),
    RELEVANT_HISTORY("Relevant history", true);

    private final String label;
    private final boolean clinical;

    RecordField(String label, boolean clinical) {
        this.label = label;
        this.clinical = clinical;
    }

    public String getLabel() { return label; }

    /** True for allergies and relevant history, which only Nurses and Clinicians may see. */
    public boolean isClinical() { return clinical; }

    /** The permission needed to change this field. */
    public Permission getEditPermission() {
        return clinical ? Permission.EDIT_CLINICAL_NOTES : Permission.CORRECT_RECORDS;
    }

    /** True for the fields used in the duplicate check (name and birthdate). */
    public boolean isIdentity() {
        return this == LAST_NAME || this == FIRST_NAME || this == BIRTHDATE;
    }
}
