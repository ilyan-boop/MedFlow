package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.model.DailyReport;
import medflow.model.Patient;
import medflow.model.Permission;
import medflow.model.Service;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitStatus;
import medflow.util.MergeUtil;
import medflow.util.SortUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Daily reports (Section 5.5, "Daily report" pseudocode of the proposal):
 *
 *   FOR each service IN (FM, IM, PED)
 *     list[service] = visits of that service on the date              O(n)
 *     list[service] = mergeSort(list[service]) by arrival time        O(n log n)
 *   allVisits = kWayMerge(list[FM], list[IM], list[PED])              O(N log 3)
 *   COUNT visits by status, averageWait = SUM(start - arrival) / seen O(N)
 *
 * Only the Administrator can make reports (VIEW_REPORTS). Outcome notes are not included.
 */
public class ReportService {

    /** Arrival time, then arrival order (the order the visits were created). */
    public static final Comparator<Visit> BY_ARRIVAL = (a, b) -> {
        int c = a.getArrivalTime().compareTo(b.getArrivalTime());
        return c != 0 ? c : Long.compare(a.getArrivalOrder(), b.getArrivalOrder());
    };

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final AuthService auth;
    private final PatientService patients;
    private final VisitService visits;
    private final AuditLog auditLog;

    public ReportService(AuthService auth, PatientService patients, VisitService visits, AuditLog auditLog) {
        this.auth = auth;
        this.patients = patients;
        this.visits = visits;
        this.auditLog = auditLog;
    }

    /** The visits of one service on one date, sorted by arrival time with merge sort. */
    public ArrayList<Visit> sortedServiceList(Service service, LocalDate date) {
        ArrayList<Visit> list = new ArrayList<>();
        for (Visit v : visits.getAllVisits()) {
            if (v.getService() == service && v.getArrivalTime().toLocalDate().equals(date)) list.add(v);
        }
        SortUtil.mergeSort(list, BY_ARRIVAL);
        return list;
    }

    /** Builds the daily report of a date. */
    public DailyReport dailyReport(User user, LocalDate date, LocalDateTime now) throws AccessDeniedException {
        auth.require(user, Permission.VIEW_REPORTS);
        ArrayList<ArrayList<Visit>> perService = new ArrayList<>();
        for (Service s : Service.values()) perService.add(sortedServiceList(s, date));   // FM, IM, PED
        ArrayList<Visit> all = MergeUtil.kWayMerge(perService, BY_ARRIVAL);
        DailyReport r = new DailyReport(date, user.getUsername(), now, all);
        auditLog.add(new AuditEntry(now, user.getUsername(), "VIEW_REPORT", date.toString(),
                r.total(null) + " visits, " + r.seen(null) + " seen"));
        return r;
    }

    /** Called after a report was written to a file, so the export is in the audit log. */
    public void logExport(User user, DailyReport r, String fileName, LocalDateTime now) {
        auditLog.add(new AuditEntry(now, user.getUsername(), "EXPORT_REPORT", r.getDate().toString(), fileName));
    }

    // ================================================================== text form (screen and .txt file)

    public String toText(DailyReport r) {
        StringBuilder sb = new StringBuilder();
        String rule = "=".repeat(92) + "\n";
        sb.append(rule);
        sb.append("  MEDFLOW DAILY REPORT\n");
        sb.append("  Date    : ").append(r.getDate().format(DAY)).append("\n");
        sb.append("  Made by : ").append(r.getMadeBy()).append(" on ").append(r.getMadeAt().format(STAMP)).append("\n");
        sb.append(rule);

        sb.append("\n  PATIENT COUNTS AND QUEUE STATUS\n");
        sb.append(String.format("  %-19s %7s %10s %9s %8s %9s %7s %6s%n",
                "Service", "Waiting", "In consult", "Completed", "Referred", "Cancelled", "No-show", "Total"));
        for (Service s : Service.values()) countRow(sb, s.getLabel(), r, s);
        sb.append("  ").append("-".repeat(89)).append("\n");
        countRow(sb, "All services", r, null);

        sb.append("\n  WAITING TIME AND CONSULTATION VOLUME\n");
        sb.append(String.format("  %-19s %14s %20s %24s%n", "Service", "Patients seen", "Average wait (min)", "Average consult (min)"));
        for (Service s : Service.values()) timeRow(sb, s.getLabel(), r, s);
        sb.append("  ").append("-".repeat(89)).append("\n");
        timeRow(sb, "All services", r, null);
        sb.append("  Waiting time = consultation start minus arrival time, for patients who were seen.\n");

        sb.append("\n  VISITS IN ORDER OF ARRIVAL (FM, IM, and PED lists joined by k-way merge)\n");
        if (r.getVisits().isEmpty()) {
            sb.append("  No visits on this date.\n");
        } else {
            visitRows(sb, r.getVisits());
        }
        sb.append(rule);
        return sb.toString();
    }

    /** The table of visits with one status, for the "show visits with one status" option. */
    public String statusListText(DailyReport r, VisitStatus status) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Visit> list = r.visitsWithStatus(status);
        sb.append("  ").append(list.size()).append(" visit(s) with status ").append(label(status))
                .append(" on ").append(r.getDate().format(DAY)).append("\n");
        if (!list.isEmpty()) visitRows(sb, list);
        return sb.toString();
    }

    private void visitRows(StringBuilder sb, ArrayList<Visit> list) {
        sb.append(String.format("  %-3s %-5s %-8s %-6s %-26s %-4s %-9s %-15s %5s %8s%n",
                "#", "Time", "Number", "Visit", "Patient", "Svc", "Type", "Status", "Wait", "Consult"));
        int i = 1;
        for (Visit v : list) {
            sb.append(String.format("  %-3d %-5s %-8s %-6s %-26s %-4s %-9s %-15s %5s %8s%n",
                    i++, v.getArrivalTime().format(HM), v.getQueueNumber(), v.getVisitId(), cut(nameOf(v), 26),
                    v.getService().name(), label(v.getVisitType()), label(v.getStatus()),
                    v.getWaitingMinutes() < 0 ? "-" : String.valueOf(v.getWaitingMinutes()),
                    v.getConsultMinutes() < 0 ? "-" : String.valueOf(v.getConsultMinutes())));
        }
    }

    private static void countRow(StringBuilder sb, String name, DailyReport r, Service s) {
        sb.append(String.format("  %-19s %7d %10d %9d %8d %9d %7d %6d%n", name,
                r.count(s, VisitStatus.WAITING), r.count(s, VisitStatus.IN_CONSULTATION), r.count(s, VisitStatus.COMPLETED),
                r.count(s, VisitStatus.REFERRED), r.count(s, VisitStatus.CANCELLED), r.count(s, VisitStatus.NO_SHOW),
                r.total(s)));
    }

    private static void timeRow(StringBuilder sb, String name, DailyReport r, Service s) {
        sb.append(String.format("  %-19s %14d %20s %24s%n", name, r.seen(s),
                minutes(r.averageWaitingMinutes(s)), minutes(r.averageConsultMinutes(s))));
    }

    // ================================================================== .csv form

    /** Rows of the .csv file: a summary per service, an empty row, then one row per visit. */
    public ArrayList<String[]> toCsvRows(DailyReport r) {
        ArrayList<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"MedFlow daily report", r.getDate().toString(), "made by " + r.getMadeBy(), r.getMadeAt().format(STAMP)});
        rows.add(new String[] {"service", "waiting", "inConsultation", "completed", "referred", "cancelled", "noShow",
                "total", "patientsSeen", "averageWaitMinutes", "averageConsultMinutes"});
        for (Service s : Service.values()) rows.add(summaryRow(s.name(), r, s));
        rows.add(summaryRow("ALL", r, null));
        rows.add(new String[] {});
        rows.add(new String[] {"order", "visitId", "queueNumber", "patientId", "patientName", "service", "visitType",
                "priorityCategory", "arrivalTime", "consultStart", "consultEnd", "status", "waitMinutes", "consultMinutes"});
        int i = 1;
        for (Visit v : r.getVisits()) {
            rows.add(new String[] {String.valueOf(i++), v.getVisitId(), v.getQueueNumber(), v.getPatientId(), nameOf(v),
                    v.getService().name(), v.getVisitType().name(), v.getPriorityCategory().name(),
                    v.getArrivalTime().format(STAMP), time(v.getConsultStart()), time(v.getConsultEnd()), v.getStatus().name(),
                    v.getWaitingMinutes() < 0 ? "" : String.valueOf(v.getWaitingMinutes()),
                    v.getConsultMinutes() < 0 ? "" : String.valueOf(v.getConsultMinutes())});
        }
        return rows;
    }

    private static String[] summaryRow(String name, DailyReport r, Service s) {
        return new String[] {name,
                String.valueOf(r.count(s, VisitStatus.WAITING)), String.valueOf(r.count(s, VisitStatus.IN_CONSULTATION)),
                String.valueOf(r.count(s, VisitStatus.COMPLETED)), String.valueOf(r.count(s, VisitStatus.REFERRED)),
                String.valueOf(r.count(s, VisitStatus.CANCELLED)), String.valueOf(r.count(s, VisitStatus.NO_SHOW)),
                String.valueOf(r.total(s)), String.valueOf(r.seen(s)),
                r.averageWaitingMinutes(s) < 0 ? "" : String.format("%.1f", r.averageWaitingMinutes(s)),
                r.averageConsultMinutes(s) < 0 ? "" : String.format("%.1f", r.averageConsultMinutes(s))};
    }

    // ================================================================== helpers

    private String nameOf(Visit v) {
        Patient p = patients.findById(v.getPatientId());
        return p == null ? v.getPatientId() : p.getFullName();
    }

    private static String time(LocalDateTime t) { return t == null ? "" : t.format(STAMP); }

    private static String minutes(double m) { return m < 0 ? "-" : String.format("%.1f", m); }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 3) + "..."; }

    /** NO_SHOW becomes "No-show", IN_CONSULTATION becomes "In consultation". */
    public static String label(Enum<?> e) {
        if (e == VisitStatus.NO_SHOW) return "No-show";
        String s = e.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
