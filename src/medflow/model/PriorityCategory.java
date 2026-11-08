package medflow.model;

/**
 * Priority categories set by clinic policy. Staff record the category.
 * The system never judges medical urgency.
 */
public enum PriorityCategory {
    NONE, SENIOR_CITIZEN, PWD, PREGNANT;

    public boolean isPriority() { return this != NONE; }
}
