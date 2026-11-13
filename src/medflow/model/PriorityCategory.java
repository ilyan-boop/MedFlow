package medflow.model;

/**
 * Priority categories set by clinic policy. Staff record the category.
 * The system never judges medical urgency.
 */
public enum PriorityCategory {
    REGULAR, PRIORITY;

    public boolean isPriority() { return this != REGULAR; }
}
