package medflow.model;

public enum Sex {
    MALE("Male"), FEMALE("Female");

    private final String label;

    Sex(String label) { this.label = label; }

    public String getLabel() { return label; }
}
