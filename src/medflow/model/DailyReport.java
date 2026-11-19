package medflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;

/**
 * The daily report of one date (Section 5.2, item 9 of the proposal): patient counts, queue status
 * summary, average waiting time, consultation volume, and the visits of the day in order of arrival.
 * Built by ReportService.
 */
public class DailyReport {

    /** Statuses shown in the report, in this order. */
    public static final VisitStatus[] STATUSES = {
            VisitStatus.WAITING, VisitStatus.IN_CONSULTATION, VisitStatus.COMPLETED,
            VisitStatus.REFERRED, VisitStatus.CANCELLED, VisitStatus.NO_SHOW };

    private final LocalDate date;
    private final String madeBy;
    private final LocalDateTime madeAt;
    private final ArrayList<Visit> visits;                                   // all services, in order of arrival
    private final EnumMap<Service, EnumMap<VisitStatus, Integer>> counts = new EnumMap<>(Service.class);
    private final EnumMap<Service, Long> waitTotal = new EnumMap<>(Service.class);
    private final EnumMap<Service, Integer> waitCount = new EnumMap<>(Service.class);
    private final EnumMap<Service, Long> consultTotal = new EnumMap<>(Service.class);
    private final EnumMap<Service, Integer> consultCount = new EnumMap<>(Service.class);

    public DailyReport(LocalDate date, String madeBy, LocalDateTime madeAt, ArrayList<Visit> visitsInArrivalOrder) {
        this.date = date;
        this.madeBy = madeBy;
        this.madeAt = madeAt;
        this.visits = visitsInArrivalOrder;
        for (Service s : Service.values()) {
            EnumMap<VisitStatus, Integer> m = new EnumMap<>(VisitStatus.class);
            for (VisitStatus st : VisitStatus.values()) m.put(st, 0);
            counts.put(s, m);
            waitTotal.put(s, 0L);
            waitCount.put(s, 0);
            consultTotal.put(s, 0L);
            consultCount.put(s, 0);
        }
        for (Visit v : visitsInArrivalOrder) {                               // one pass, O(n)
            Service s = v.getService();
            counts.get(s).merge(v.getStatus(), 1, Integer::sum);
            if (v.getConsultStart() != null) {                               // the patient was seen
                waitTotal.merge(s, v.getWaitingMinutes(), Long::sum);
                waitCount.merge(s, 1, Integer::sum);
            }
            if (v.getConsultEnd() != null) {
                consultTotal.merge(s, v.getConsultMinutes(), Long::sum);
                consultCount.merge(s, 1, Integer::sum);
            }
        }
    }

    public LocalDate getDate() { return date; }
    public String getMadeBy() { return madeBy; }
    public LocalDateTime getMadeAt() { return madeAt; }
    public ArrayList<Visit> getVisits() { return visits; }

    /** Number of visits of a service with a status. Pass null as the service for all services. */
    public int count(Service service, VisitStatus status) {
        if (service != null) return counts.get(service).get(status);
        int n = 0;
        for (Service s : Service.values()) n += counts.get(s).get(status);
        return n;
    }

    /** Number of visits of a service. Pass null for all services. */
    public int total(Service service) {
        int n = 0;
        for (VisitStatus st : VisitStatus.values()) n += count(service, st);
        return n;
    }

    /** Patients seen (the consultation started). This is the consultation volume. Pass null for all services. */
    public int seen(Service service) {
        if (service != null) return waitCount.get(service);
        int n = 0;
        for (Service s : Service.values()) n += waitCount.get(s);
        return n;
    }

    /** Average minutes from arrival to the start of the consultation, or -1 if nobody was seen. Pass null for all. */
    public double averageWaitingMinutes(Service service) {
        long total = 0;
        int n = 0;
        for (Service s : Service.values()) {
            if (service != null && s != service) continue;
            total += waitTotal.get(s);
            n += waitCount.get(s);
        }
        return n == 0 ? -1 : (double) total / n;
    }

    /** Average minutes of finished consultations, or -1 if none finished. Pass null for all. */
    public double averageConsultMinutes(Service service) {
        long total = 0;
        int n = 0;
        for (Service s : Service.values()) {
            if (service != null && s != service) continue;
            total += consultTotal.get(s);
            n += consultCount.get(s);
        }
        return n == 0 ? -1 : (double) total / n;
    }

    /** The visits with one status, still in order of arrival. */
    public ArrayList<Visit> visitsWithStatus(VisitStatus status) {
        ArrayList<Visit> list = new ArrayList<>();
        for (Visit v : visits) if (v.getStatus() == status) list.add(v);
        return list;
    }
}
