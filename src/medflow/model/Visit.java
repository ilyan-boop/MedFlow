package medflow.model;

import java.time.LocalDateTime;

/**
 * One clinic visit (encounter). Each visit has its own visit ID and is linked to a patient ID.
 * Data model only for now. Visit creation and queuing are built in Milestone 3 (Nov 12).
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
    private LocalDateTime consultStart;
    private LocalDateTime consultEnd;
    private String outcome = "";
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
    public LocalDateTime getConsultStart() { return consultStart; }
    public LocalDateTime getConsultEnd() { return consultEnd; }
    public String getOutcome() { return outcome; }
    public String getAdminNotes() { return adminNotes; }

    public void setStatus(VisitStatus status) { this.status = status; }
    public void setPriorityCategory(PriorityCategory c) { this.priorityCategory = c; }
    public void setQueueNumber(String queueNumber) { this.queueNumber = queueNumber; }
    public void setArrivalOrder(long arrivalOrder) { this.arrivalOrder = arrivalOrder; }
    public void addMissed() { missedCount++; }
    public void setConsultStart(LocalDateTime t) { this.consultStart = t; }
    public void setConsultEnd(LocalDateTime t) { this.consultEnd = t; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
