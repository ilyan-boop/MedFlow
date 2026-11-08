package medflow.model;

import java.time.LocalDateTime;

/**
 * An appointment or follow-up, linked to a patient and (for follow-ups) to the visit that led to it.
 * Data model only for now. The appointment module is built in Milestone 5 (Nov 16).
 */
public class Appointment {
    private final String appointmentId;
    private final String patientId;
    private final String linkedVisitId;   // empty if not a follow-up
    private LocalDateTime dateTime;
    private final Service service;
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    public Appointment(String appointmentId, String patientId, String linkedVisitId,
                       LocalDateTime dateTime, Service service) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.linkedVisitId = linkedVisitId;
        this.dateTime = dateTime;
        this.service = service;
    }

    public String getAppointmentId() { return appointmentId; }
    public String getPatientId() { return patientId; }
    public String getLinkedVisitId() { return linkedVisitId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public Service getService() { return service; }
    public AppointmentStatus getStatus() { return status; }

    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
