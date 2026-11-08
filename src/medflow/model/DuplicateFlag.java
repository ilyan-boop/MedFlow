package medflow.model;

import java.time.LocalDateTime;

/**
 * A possible duplicate found during registration: a new record that has the same name and
 * birthdate as an existing record. Records staff review it and either merge or keep both.
 */
public class DuplicateFlag {
    public enum Status { PENDING, MERGED, KEPT_BOTH }

    private final String newPatientId;
    private final String existingPatientId;
    private final String flaggedBy;
    private final LocalDateTime flaggedAt;
    private Status status = Status.PENDING;
    private String resolvedBy = "";

    public DuplicateFlag(String newPatientId, String existingPatientId,
                         String flaggedBy, LocalDateTime flaggedAt) {
        this.newPatientId = newPatientId;
        this.existingPatientId = existingPatientId;
        this.flaggedBy = flaggedBy;
        this.flaggedAt = flaggedAt;
    }

    public String getNewPatientId() { return newPatientId; }
    public String getExistingPatientId() { return existingPatientId; }
    public String getFlaggedBy() { return flaggedBy; }
    public LocalDateTime getFlaggedAt() { return flaggedAt; }
    public Status getStatus() { return status; }
    public String getResolvedBy() { return resolvedBy; }

    public void resolve(Status status, String resolvedBy) {
        this.status = status;
        this.resolvedBy = resolvedBy;
    }
}
