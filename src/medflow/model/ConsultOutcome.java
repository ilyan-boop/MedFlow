package medflow.model;

/**
 * How a consultation ended. The clinician picks one when recording the outcome.
 * Each outcome sets the final status of the visit.
 */
public enum ConsultOutcome {
    TREATED("Treated, consultation completed", VisitStatus.COMPLETED),
    FOLLOW_UP("Follow-up visit advised", VisitStatus.COMPLETED),
    REFERRED("Referred to another service or facility", VisitStatus.REFERRED);

    private final String label;
    private final VisitStatus finalStatus;

    ConsultOutcome(String label, VisitStatus finalStatus) {
        this.label = label;
        this.finalStatus = finalStatus;
    }

    public String getLabel() { return label; }

    public VisitStatus getFinalStatus() { return finalStatus; }
}
