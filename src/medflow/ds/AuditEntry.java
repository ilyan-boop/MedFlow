package medflow.ds;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One line of the audit log: who did what, to which record, and when. */
public class AuditEntry {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalDateTime time;
    private final String username;
    private final String action;
    private final String target;
    private final String details;

    public AuditEntry(LocalDateTime time, String username, String action, String target, String details) {
        this.time = time;
        this.username = username;
        this.action = action;
        this.target = target;
        this.details = details;
    }

    public LocalDateTime getTime() { return time; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetails() { return details; }

    @Override
    public String toString() {
        return String.format("%s  %-12s %-20s %-8s %s", time.format(FMT), username, action, target, details);
    }
}
