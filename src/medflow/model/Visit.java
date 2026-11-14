package medflow.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One clinic visit (encounter). Each visit has its own visit ID and is linked to a patient ID.
 * Milestone 3 creates the visit and queues it. Milestone 4 adds the call time, the consultation
 * times, the outcome, and the clinician who recorded it.
 */
public class Visit {
    private final String visitId;
    private final String patientId;
    private final Service service;
    private final String reasonForVisit;
    private final VisitType visitType;
    private final LocalDateTime arrivalTime;

    private VisitStatus status = VisitStatus.REGISTERED;
    private PriorityCategory priorityCategory = PriorityCategory.REGULAR;
    private String queueNumber = "";     // e.g. FM-001
    private long arrivalOrder;           // used to keep first in, first out order
    private int missedCount = 0;
    private LocalDateTime calledTime;
    private LocalDateTime consultStart;
    private LocalDateTime consultEnd;
    private ConsultOutcome outcomeType;  // null until the clinician records the outcome
    private String outcome = "";         // short note written by the clinician
    private String consultedBy = "";     // username of the clinician who recorded the outcome
    private String adminNotes = "";

    public Visit(String visitId, String patientId, Service service, String reasonForVisit,
                 VisitType visitType, LocalDateTime arrivalTime) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.service = service;
        this.reasonForVisit = reasonForVisit;
        this.visitType = visitType;
        this.arrivalTime = arrivalTime;
    }

    public String getVisitId() { return visitId; }
    public String getPatientId() { return patientId; }
    public Service getService() { return service; }
    public String getReasonForVisit() { return reasonForVisit; }
    public VisitType getVisitType() { return visitType; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public VisitStatus getStatus() { return status; }
    public PriorityCategory getPriorityCategory() { return priorityCategory; }
    public String getQueueNumber() { return queueNumber; }
    public long getArrivalOrder() { return arrivalOrder; }
    public int getMissedCount() { return missedCount; }
    public LocalDateTime getCalledTime() { return calledTime; }
    public LocalDateTime getConsultStart() { return consultStart; }
    public LocalDateTime getConsultEnd() { return consultEnd; }
    public ConsultOutcome getOutcomeType() { return outcomeType; }
    public String getOutcome() { return outcome; }
    public String getConsultedBy() { return consultedBy; }
    public String getAdminNotes() { return adminNotes; }

    public void setStatus(VisitStatus status) { this.status = status; }
    public void setPriorityCategory(PriorityCategory c) { this.priorityCategory = c; }
    public void setQueueNumber(String queueNumber) { this.queueNumber = queueNumber; }
    public void setArrivalOrder(long arrivalOrder) { this.arrivalOrder = arrivalOrder; }
    public void addMissed() { missedCount++; }
    public void setCalledTime(LocalDateTime t) { this.calledTime = t; }
    public void setConsultStart(LocalDateTime t) { this.consultStart = t; }
    public void setConsultEnd(LocalDateTime t) { this.consultEnd = t; }
    public void setOutcomeType(ConsultOutcome type) { this.outcomeType = type; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setConsultedBy(String username) { this.consultedBy = username; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    /** Minutes from arrival until the consultation started, or -1 if it has not started. */
    public long getWaitingMinutes() {
        if (consultStart == null) return -1;
        return Duration.between(arrivalTime, consultStart).toMinutes();
    }

    /** Minutes from the start of the consultation to its end, or -1 if it has not ended. */
    public long getConsultMinutes() {
        if (consultStart == null || consultEnd == null) return -1;
        return Duration.between(consultStart, consultEnd).toMinutes();
    }
}
