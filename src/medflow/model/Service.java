package medflow.model;

/** The three clinic services, with their queue number prefix and age rule. */
public enum Service {
    FM("Family Medicine", 0, 200),      // all ages
    IM("Internal Medicine", 18, 200),   // 18 years old and above
    PED("Pediatrics", 0, 17);           // below 18 years old

    private final String label;
    private final int minAge;
    private final int maxAge;

    Service(String label, int minAge, int maxAge) {
        this.label = label;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public String getLabel() { return label; }

    /** Queue number prefix, e.g. FM in FM-001. */
    public String getPrefix() { return name(); }

    /** Age rule for the service. */
    public boolean acceptsAge(int age) { return age >= minAge && age <= maxAge; }

    public String getAgeRule() {
        switch (this) {
            case IM:  return "18 years old and above";
            case PED: return "below 18 years old";
            default:  return "all ages";
        }
    }
}
