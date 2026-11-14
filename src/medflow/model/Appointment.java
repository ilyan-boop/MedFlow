package medflow.model;

import java.time.LocalDateTime;

/**
 * An appointment or follow-up, linked to a patient and (for follow-ups) to the visit that led to it.
 * Milestone 5: kept in a TreeMap by date and time in AppointmentService.
 */
public class Appointment {
    private final String appointmentId;
    private final String patientId;
    private final String linkedVisitId;   // the consultation that advised the follow-up, empty if not a follow-up
    private LocalDateTime dateTime;
    private final Service service;
    private final String purpose;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;
    private int rescheduleCount = 0;
    private String checkInVisitId = "";    // the visit created when the patient checked in

    public Appointment(String appointmentId, String patientId, String linkedVisitId, LocalDateTime dateTime,
                       Service service, String purpose, String createdBy, LocalDateTime createdAt) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.linkedVisitId = linkedVisitId;
        this.dateTime = dateTime;
        this.service = service;
        this.purpose = purpose;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public boolean isFollowUp() { return !linkedVisitId.isEmpty(); }

    public String getAppointmentId() { return appointmentId; }
    public String getPatientId() { return patientId; }
    public String getLinkedVisitId() { return linkedVisitId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public Service getService() { return service; }
    public String getPurpose() { return purpose; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public AppointmentStatus getStatus() { return status; }
    public int getRescheduleCount() { return rescheduleCount; }
    public String getCheckInVisitId() { return checkInVisitId; }

    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public void setRescheduleCount(int count) { this.rescheduleCount = count; }
    public void setCheckInVisitId(String visitId) { this.checkInVisitId = visitId; }
}
