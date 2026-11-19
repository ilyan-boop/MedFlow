package medflow;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.ds.BinaryHeap;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.io.Csv;
import medflow.io.FileHandler;
import medflow.io.SampleData;
import medflow.model.DailyReport;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.PriorityCategory;
import medflow.model.RecordField;
import medflow.model.Service;
import medflow.model.Sex;
import medflow.model.User;
import medflow.model.Visit;
import medflow.model.VisitStatus;
import medflow.model.VisitType;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.service.RecordHistory;
import medflow.service.ReportService;
import medflow.service.SearchService;
import medflow.service.VisitService;
import medflow.util.MergeUtil;
import medflow.util.SearchUtil;
import medflow.util.SortUtil;
import medflow.util.Validator;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test cases for Milestone 6 (Nov 23): hand-coded merge sort, binary search, min-heap, and k-way merge,
 * search by name, visit ID, and queue number, and daily reports with .txt and .csv export.
 * The report tests use the same visits as data/visits.csv (Tuesday, November 17, 2026), so the
 * expected counts and averages can be checked by hand.
 * Run with:  java -cp out medflow.Milestone6Tests
 */
public class Milestone6Tests {

    private static int passed = 0, failed = 0, number = 0;

    private static final LocalDate NOV17 = LocalDate.of(2026, 11, 17);
    private static final LocalDate NOV18 = LocalDate.of(2026, 11, 18);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 9, 0);

    private interface Check { Object run() throws Exception; }

    private static void expect(String name, Object expected, Check check) {
        number++;
        try {
            Object actual = check.run();
            if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) pass(name, "-> " + actual);
            else fail(name, "expected " + expected + " but got " + actual);
        } catch (Exception e) {
            fail(name, "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void expectError(String name, Class<? extends Exception> type, Check check) {
        number++;
        try {
            Object actual = check.run();
            fail(name, "expected " + type.getSimpleName() + " but got " + actual);
        } catch (Exception e) {
            if (type.isInstance(e)) pass(name, "-> rejected: " + e.getMessage());
            else fail(name, "expected " + type.getSimpleName() + " but got " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void pass(String name, String detail) {
        passed++;
        System.out.printf("PASS  %3d  %-62s %s%n", number, name, detail);
    }

    private static void fail(String name, String detail) {
        failed++;
        System.out.printf("FAIL  %3d  %-62s %s%n", number, name, detail);
    }

    private static void section(String title) { System.out.println("\n--- " + title + " ---"); }

    public static void main(String[] args) throws Exception {
        System.out.println("MedFlow test results (Milestone 6: sorting, searching, k-way merge, daily reports)");

        // ============================================================ merge sort
        section("Hand-coded merge sort (SortUtil)");
        expect("Empty list stays empty", "[]", () -> sorted(new ArrayList<>()).toString());
        expect("One item stays the same", "[7]", () -> sorted(ints(7)).toString());
        expect("Unsorted numbers", "[1, 2, 3, 5, 8, 9]", () -> sorted(ints(5, 2, 9, 1, 8, 3)).toString());
        expect("Already sorted list", "[1, 2, 3, 4]", () -> sorted(ints(1, 2, 3, 4)).toString());
        expect("Reverse sorted list", "[1, 2, 3, 4, 5]", () -> sorted(ints(5, 4, 3, 2, 1)).toString());
        expect("Repeated numbers", "[1, 2, 2, 2, 3, 3]", () -> sorted(ints(3, 2, 1, 2, 3, 2)).toString());
        expect("Stable: equal keys keep their order (b1 before b2)", "[a, b1, b2, c]", () -> {
            ArrayList<String> s = new ArrayList<>(List.of("b1", "c", "a", "b2"));
            SortUtil.mergeSort(s, (x, y) -> Character.compare(x.charAt(0), y.charAt(0)));
            return s.toString();
        });
        expect("sortedCopy leaves the original list unchanged", "[3, 1, 2] / [1, 2, 3]", () -> {
            ArrayList<Integer> a = ints(3, 1, 2);
            return a + " / " + SortUtil.sortedCopy(a, Integer::compare);
        });
        expect("10,000 random numbers come out in order, none lost", "true / 10000", () -> {
            ArrayList<Integer> a = randomInts(10000, 1);
            long before = sum(a);
            SortUtil.mergeSort(a, Integer::compare);
            boolean ok = true;
            for (int i = 1; i < a.size(); i++) if (a.get(i - 1) > a.get(i)) ok = false;
            return (ok && sum(a) == before) + " / " + a.size();
        });
        expect("Descending order with a reversed comparator", "[9, 5, 2]", () -> {
            ArrayList<Integer> a = ints(2, 9, 5);
            SortUtil.mergeSort(a, (x, y) -> Integer.compare(y, x));
            return a.toString();
        });

        // ============================================================ binary search
        section("Hand-coded binary search and linear search (SearchUtil)");
        ArrayList<Integer> nums = ints(2, 4, 6, 8, 10, 12, 14);
        expect("Finds an item in the middle", 3, () -> SearchUtil.binarySearch(nums, 8, Integer::compare));
        expect("Finds the first item", 0, () -> SearchUtil.binarySearch(nums, 2, Integer::compare));
        expect("Finds the last item", 6, () -> SearchUtil.binarySearch(nums, 14, Integer::compare));
        expect("Smaller than every item: not found", -1, () -> SearchUtil.binarySearch(nums, 1, Integer::compare));
        expect("Between two items: not found", -1, () -> SearchUtil.binarySearch(nums, 7, Integer::compare));
        expect("Larger than every item: not found", -1, () -> SearchUtil.binarySearch(nums, 20, Integer::compare));
        expect("Empty list: not found", -1, () -> SearchUtil.binarySearch(new ArrayList<Integer>(), 5, Integer::compare));
        expect("Repeated key: every match is listed", "[3, 3, 3]",
                () -> SearchUtil.allMatches(ints(1, 2, 3, 3, 3, 4, 5), 3, Integer::compare).toString());
        expect("Repeated key at both ends of the list", "[1, 1] / [9, 9]", () ->
                SearchUtil.allMatches(ints(1, 1, 5, 9, 9), 1, Integer::compare) + " / "
                        + SearchUtil.allMatches(ints(1, 1, 5, 9, 9), 9, Integer::compare));
        expect("Prefix compare: \"delacruzjuan\" starts with \"dela\"", 0, () -> SearchUtil.comparePrefix("delacruzjuan", "dela"));
        expect("Prefix compare: shorter text comes before the prefix", true, () -> SearchUtil.comparePrefix("tan", "tank") < 0);
        expect("Linear search keeps every item that passes", "[4, 8, 12]",
                () -> SearchUtil.linearSearch(nums, n -> n % 4 == 0).toString());

        // ============================================================ min-heap
        section("Hand-coded min-heap (BinaryHeap)");
        expect("removeMin gives the items smallest first", "[1, 2, 3, 5, 8, 9]", () -> {
            BinaryHeap<Integer> h = new BinaryHeap<>(Integer::compare);
            for (int n : new int[] {5, 9, 1, 8, 3, 2}) h.insert(n);
            ArrayList<Integer> out = new ArrayList<>();
            while (!h.isEmpty()) out.add(h.removeMin());
            return out.toString();
        });
        expect("peek shows the smallest item without removing it", "1 / 3", () -> {
            BinaryHeap<Integer> h = new BinaryHeap<>(Integer::compare);
            h.insert(4); h.insert(1); h.insert(7);
            return h.peek() + " / " + h.size();
        });
        expect("removeMin and peek on an empty heap give null", "null / null", () -> {
            BinaryHeap<Integer> h = new BinaryHeap<>(Integer::compare);
            return h.removeMin() + " / " + h.peek();
        });
        expect("Repeated values all come out", "[2, 2, 2, 5]", () -> {
            BinaryHeap<Integer> h = new BinaryHeap<>(Integer::compare);
            for (int n : new int[] {2, 5, 2, 2}) h.insert(n);
            ArrayList<Integer> out = new ArrayList<>();
            while (!h.isEmpty()) out.add(h.removeMin());
            return out.toString();
        });
        expect("5,000 random inserts come out in order", true, () -> {
            BinaryHeap<Integer> h = new BinaryHeap<>(Integer::compare);
            for (int n : randomInts(5000, 2)) h.insert(n);
            int last = Integer.MIN_VALUE;
            while (!h.isEmpty()) {
                int n = h.removeMin();
                if (n < last) return false;
                last = n;
            }
            return true;
        });

        // ============================================================ k-way merge
        section("Hand-coded k-way merge (MergeUtil)");
        expect("Three sorted lists become one sorted list", "[1, 2, 3, 4, 5, 6, 7, 8, 9]",
                () -> MergeUtil.kWayMerge(lists(ints(1, 4, 7), ints(2, 5, 8), ints(3, 6, 9)), Integer::compare).toString());
        expect("A list can be empty", "[1, 2, 5, 9]",
                () -> MergeUtil.kWayMerge(lists(ints(1, 9), ints(), ints(2, 5)), Integer::compare).toString());
        expect("All lists empty gives an empty list", "[]",
                () -> MergeUtil.kWayMerge(lists(ints(), ints(), ints()), Integer::compare).toString());
        expect("Equal items: the earlier list comes first (FM, then IM)", "[FM 08:00, IM 08:00, PED 08:00]", () -> {
            ArrayList<ArrayList<String>> l = new ArrayList<>();
            l.add(new ArrayList<>(List.of("FM 08:00")));
            l.add(new ArrayList<>(List.of("IM 08:00")));
            l.add(new ArrayList<>(List.of("PED 08:00")));
            return MergeUtil.kWayMerge(l, (a, b) -> a.substring(a.indexOf(' ')).compareTo(b.substring(b.indexOf(' ')))).toString();
        });
        expect("Size is the sum of the list sizes (3 + 0 + 4)", 7,
                () -> MergeUtil.kWayMerge(lists(ints(1, 2, 3), ints(), ints(0, 4, 5, 6)), Integer::compare).size());

        // ============================================================ search service
        AuditLog log = new AuditLog();
        AuthService auth = new AuthService(log);
        PatientService ps = new PatientService(auth, log);
        SampleData.load(auth, ps);
        VisitService vs = new VisitService(auth, ps, log);
        loadSampleDay(vs, ps);
        SearchService search = new SearchService(auth, ps, vs);
        ReportService reports = new ReportService(auth, ps, vs, log);
        User reception = auth.login("reception", "rec123");
        User nurse = auth.login("nurse", "nur123");
        User records = auth.login("records", "rec456");
        User admin = auth.login("admin", "adm123");

        section("Search by name, visit ID, and queue number (SearchService)");
        expect("\"dela\" finds Dela Cruz by binary search", "P0001 / binary", () ->
                ids(search.searchByName(reception, "dela")) + " / " + (search.lastNameSearchWasPartial() ? "linear" : "binary"));
        expect("\"De La Cruz\" with spaces finds the same patient", "P0001", () -> ids(search.searchByName(reception, "De La Cruz")));
        expect("\"santos\" finds the last name Santos only", "P0004", () -> ids(search.searchByName(reception, "santos")));
        expect("\"Santos Lou\" (last name and start of first name)", "P0004", () -> ids(search.searchByName(reception, "Santos Lou")));
        expect("\"juan\" is not the start of a name, so linear search finds it", "P0001 / linear", () ->
                ids(search.searchByName(reception, "juan")) + " / " + (search.lastNameSearchWasPartial() ? "linear" : "binary"));
        expect("\"clara\" (part of the first name)", "P0002", () -> ids(search.searchByName(reception, "clara")));
        expect("A name that matches nobody gives an empty list", "", () -> ids(search.searchByName(reception, "Zamora")));
        expect("All patients sorted by name", "P0008,P0001,P0003,P0007,P0002,P0004,P0005,P0006",
                () -> ids(search.allPatientsByName(nurse)));
        expect("A new patient appears in the sorted list right away", "P0009 is 1st", () -> {
            ps.registerNew(reception, "Abad", "Carlo", "", LocalDate.of(1990, 5, 5), Sex.MALE,
                    "9 Leon Guinto St., Malate, Manila", "09170001111");
            return search.allPatientsByName(nurse).get(0).getPatientId() + " is 1st";
        });
        expect("A merged record is left out of the name search", "P0001", () -> {
            Patient copy = ps.registerNew(reception, "Dela Cruz", "Juan", "Santos", LocalDate.of(1988, 4, 12), Sex.MALE,
                    "12 Mabini St., Brgy. San Roque, Quezon City", "09171234567");
            for (DuplicateFlag f : ps.getPendingFlags(records)) {
                if (f.getNewPatientId().equals(copy.getPatientId())) ps.merge(records, f);
            }
            return ids(search.searchByName(reception, "dela cruz"));
        });
        expect("A renamed patient is found under the new name", "P0007 / ", () -> {
            new RecordHistory(auth, ps, log).updateField(records, "P0007", RecordField.LAST_NAME, "Mercado");
            return ids(search.searchByName(reception, "mercado")) + " / " + ids(search.searchByName(reception, "mendoza"));
        });
        expect("Visit ID lookup: V0006", "IM-001", () -> search.findVisit(reception, "v0006").getQueueNumber());
        expectError("Unknown visit ID", InvalidInputException.class, () -> search.findVisit(reception, "V0999"));
        expect("FM-001 was used on two days: both visits, newest first", "V0014,V0001",
                () -> vids(search.findByQueueNumber(nurse, "FM-001")));
        expect("PED-003 is found once", "V0012", () -> vids(search.findByQueueNumber(nurse, "ped-003")));
        expect("A queue number that was never used", "", () -> vids(search.findByQueueNumber(nurse, "PED-009")));
        expectError("Records Staff cannot search visits", AccessDeniedException.class, () -> search.findVisit(records, "V0001"));
        expect("Records Staff can still search names", "P0003", () -> ids(search.searchByName(records, "garcia")));

        section("Input checks for search and reports (Validator)");
        expect("Queue number \"fm-1\" becomes FM-001", "FM-001", () -> Validator.validateQueueNumber("fm-1"));
        expect("Queue number \"PED-12\" becomes PED-012", "PED-012", () -> Validator.validateQueueNumber("PED-12"));
        expectError("Queue number with an unknown service", InvalidInputException.class, () -> Validator.validateQueueNumber("XX-001"));
        expectError("Queue number without the dash", InvalidInputException.class, () -> Validator.validateQueueNumber("FM001"));
        expectError("Queue number 000", InvalidInputException.class, () -> Validator.validateQueueNumber("FM-000"));
        expect("Empty report date means today", "2026-11-23", () -> Validator.validateReportDate("", NOW.toLocalDate()).toString());
        expectError("Report date in the future", InvalidInputException.class, () -> Validator.validateReportDate("2026-11-24", NOW.toLocalDate()));
        expectError("Report date in the wrong format", InvalidInputException.class, () -> Validator.validateReportDate("2026/11/17", NOW.toLocalDate()));
        expectError("Report date that does not exist", InvalidInputException.class, () -> Validator.validateReportDate("2026-02-30", NOW.toLocalDate()));

        // ============================================================ daily report
        section("Daily report of Tuesday, November 17, 2026 (ReportService)");
        expectError("Receptionist cannot make reports", AccessDeniedException.class, () -> reports.dailyReport(reception, NOV17, NOW));
        DailyReport r = reports.dailyReport(admin, NOV17, NOW);
        expect("Per-service list is sorted by arrival time (IM)", "V0006,V0007,V0008,V0009", () -> vids(reports.sortedServiceList(Service.IM, NOV17)));
        expect("k-way merge puts all 13 visits in order of arrival",
                "V0001,V0006,V0002,V0010,V0007,V0003,V0004,V0011,V0005,V0008,V0012,V0009,V0013", () -> vids(r.getVisits()));
        expect("Total visits", 13, () -> r.total(null));
        expect("Family Medicine: completed, cancelled, no-show, total", "3/1/1/5", () -> r.count(Service.FM, VisitStatus.COMPLETED)
                + "/" + r.count(Service.FM, VisitStatus.CANCELLED) + "/" + r.count(Service.FM, VisitStatus.NO_SHOW) + "/" + r.total(Service.FM));
        expect("Internal Medicine: completed, referred, no-show", "2/1/1", () -> r.count(Service.IM, VisitStatus.COMPLETED)
                + "/" + r.count(Service.IM, VisitStatus.REFERRED) + "/" + r.count(Service.IM, VisitStatus.NO_SHOW));
        expect("Pediatrics: completed, referred", "3/1", () -> r.count(Service.PED, VisitStatus.COMPLETED) + "/" + r.count(Service.PED, VisitStatus.REFERRED));
        expect("All services: completed, referred, cancelled, no-show", "8/2/1/2", () -> r.count(null, VisitStatus.COMPLETED)
                + "/" + r.count(null, VisitStatus.REFERRED) + "/" + r.count(null, VisitStatus.CANCELLED) + "/" + r.count(null, VisitStatus.NO_SHOW));
        expect("Patients seen (consultation volume)", "10 (FM 3, IM 3, PED 4)",
                () -> r.seen(null) + " (FM " + r.seen(Service.FM) + ", IM " + r.seen(Service.IM) + ", PED " + r.seen(Service.PED) + ")");
        expect("Average wait FM = (15 + 28 + 6) / 3", "16.33", () -> two(r.averageWaitingMinutes(Service.FM)));
        expect("Average wait IM = (5 + 25 + 30) / 3", "20.00", () -> two(r.averageWaitingMinutes(Service.IM)));
        expect("Average wait PED = (25 + 5 + 25 + 10) / 4", "16.25", () -> two(r.averageWaitingMinutes(Service.PED)));
        expect("Average wait, all services = 174 / 10", "17.40", () -> two(r.averageWaitingMinutes(null)));
        expect("Average consultation, all services = 180 / 10", "18.00", () -> two(r.averageConsultMinutes(null)));
        expect("No-show list", "V0004,V0009", () -> vids(r.visitsWithStatus(VisitStatus.NO_SHOW)));
        expect("The report is in the audit log", "VIEW_REPORT 2026-11-17", () -> {
            AuditEntry last = log.toList().get(log.toList().size() - 1);
            return last.getAction() + " " + last.getTarget();
        });
        expect("Nov 18 report has only the 2 visits of that day", "V0014,V0015", () -> vids(reports.dailyReport(admin, NOV18, NOW).getVisits()));
        expect("A day with no visits: 0 visits, no average", "0 / -1.0", () -> {
            DailyReport e = reports.dailyReport(admin, LocalDate.of(2026, 11, 15), NOW);
            return e.total(null) + " / " + e.averageWaitingMinutes(null);
        });
        expect("Today's report counts a patient still waiting", "1 waiting", () -> {
            vs.createVisit(reception, "P0006", Service.FM, "Cough", VisitType.WALK_IN, PriorityCategory.REGULAR);
            return reports.dailyReport(admin, LocalDate.now(), LocalDateTime.now()).count(null, VisitStatus.WAITING) + " waiting";
        });

        // ============================================================ export
        section("Export to .txt and .csv (FileHandler)");
        File base = Files.createTempDirectory("medflow-m6").toFile();
        FileHandler fh = new FileHandler(new File(base, "data").getPath());
        expect("Reports go to a \"reports\" folder next to the data folder", true,
                () -> fh.reportFolder().getName().equals("reports")
                        && fh.reportFolder().getParentFile().getCanonicalPath().equals(base.getCanonicalPath()));
        File txt = fh.exportReportTxt(reports.toText(r), r.getDate());
        expect("Text file name", "daily_report_2026-11-17.txt", txt::getName);
        expect("Text file has the title, the date, and the totals", true, () -> {
            String s = Files.readString(txt.toPath());
            return s.contains("MEDFLOW DAILY REPORT") && s.contains("Tue 2026-11-17") && s.contains("All services")
                    && s.contains("V0013");
        });
        File csv = fh.exportReportCsv(reports.toCsvRows(r), r.getDate());
        List<String> lines = Files.readAllLines(csv.toPath());
        expect("CSV file name", "daily_report_2026-11-17.csv", csv::getName);
        expect("CSV summary row for FM", "FM,0,0,3,0,1,1,5,3,16.3,13.3", () -> lines.get(2));
        expect("CSV summary row for all services", "ALL,0,0,8,2,1,2,13,10,17.4,18.0", () -> lines.get(5));
        expect("CSV has 13 visit rows after the visit header", 13, () -> lines.size() - 8);
        expect("A name with a comma stays one field", "Dela Cruz, Juan Santos", () -> Csv.split(lines.get(8)).get(4));
        expect("Exporting again replaces the old file", 1, () -> {
            fh.exportReportCsv(reports.toCsvRows(r), r.getDate());
            int n = 0;
            for (File f : fh.reportFolder().listFiles()) if (f.getName().endsWith(".csv")) n++;
            return n;
        });

        System.out.println();
        System.out.printf("TOTAL: %d tests, %d passed, %d failed%n", passed + failed, passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ================================================================ sample day (same as data/visits.csv)

    private static void loadSampleDay(VisitService vs, PatientService ps) {
        String d = "2026-11-17T", e = "2026-11-18T";
        Object[][] rows = {
            {"V0001", "P0001", "FM", "WALK_IN", d + "08:05", "COMPLETED", "REGULAR", "FM-001", 1, d + "08:20", d + "08:35"},
            {"V0006", "P0004", "IM", "SCHEDULED", d + "08:10", "COMPLETED", "PRIORITY", "IM-001", 2, d + "08:15", d + "08:40"},
            {"V0002", "P0007", "FM", "WALK_IN", d + "08:12", "COMPLETED", "REGULAR", "FM-002", 3, d + "08:40", d + "08:55"},
            {"V0010", "P0003", "PED", "SCHEDULED", d + "08:20", "COMPLETED", "REGULAR", "PED-001", 4, d + "08:45", d + "09:05"},
            {"V0007", "P0006", "IM", "WALK_IN", d + "08:25", "REFERRED", "REGULAR", "IM-002", 5, d + "08:50", d + "09:10"},
            {"V0003", "P0008", "FM", "WALK_IN", d + "08:30", "COMPLETED", "PRIORITY", "FM-003", 6, d + "08:36", d + "08:46"},
            {"V0004", "P0002", "FM", "SCHEDULED", d + "09:00", "NO_SHOW", "REGULAR", "FM-004", 7, null, null},
            {"V0011", "P0008", "PED", "WALK_IN", d + "09:30", "COMPLETED", "PRIORITY", "PED-002", 8, d + "09:35", d + "09:50"},
            {"V0005", "P0006", "FM", "WALK_IN", d + "09:45", "CANCELLED", "REGULAR", "FM-005", 9, null, null},
            {"V0008", "P0007", "IM", "FOLLOW_UP", d + "10:00", "COMPLETED", "REGULAR", "IM-003", 10, d + "10:30", d + "10:50"},
            {"V0012", "P0005", "PED", "WALK_IN", d + "10:15", "REFERRED", "REGULAR", "PED-003", 11, d + "10:40", d + "11:05"},
            {"V0009", "P0001", "IM", "WALK_IN", d + "13:15", "NO_SHOW", "REGULAR", "IM-004", 12, null, null},
            {"V0013", "P0003", "PED", "SCHEDULED", d + "14:25", "COMPLETED", "REGULAR", "PED-004", 13, d + "14:35", d + "14:50"},
            {"V0014", "P0002", "FM", "WALK_IN", e + "09:10", "COMPLETED", "REGULAR", "FM-001", 14, e + "09:25", e + "09:40"},
            {"V0015", "P0004", "IM", "SCHEDULED", e + "09:50", "COMPLETED", "PRIORITY", "IM-001", 15, e + "10:00", e + "10:20"},
        };
        for (Object[] r : rows) {
            Visit v = new Visit((String) r[0], (String) r[1], Service.valueOf((String) r[2]), "Sample visit",
                    VisitType.valueOf((String) r[3]), LocalDateTime.parse((String) r[4]));
            v.setStatus(VisitStatus.valueOf((String) r[5]));
            v.setPriorityCategory(PriorityCategory.valueOf((String) r[6]));
            v.setQueueNumber((String) r[7]);
            v.setArrivalOrder((Integer) r[8]);
            if (r[9] != null) v.setConsultStart(LocalDateTime.parse((String) r[9]));
            if (r[10] != null) v.setConsultEnd(LocalDateTime.parse((String) r[10]));
            vs.loadVisit(v);
            ps.findById(v.getPatientId()).getVisitIds().add(v.getVisitId());
        }
        vs.rebuildQueues();
    }

    // ================================================================ helpers

    private static ArrayList<Integer> ints(int... values) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int v : values) list.add(v);
        return list;
    }

    private static ArrayList<Integer> randomInts(int n, long seed) {
        Random rnd = new Random(seed);
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(rnd.nextInt(1000));
        return list;
    }

    private static long sum(ArrayList<Integer> list) {
        long s = 0;
        for (int v : list) s += v;
        return s;
    }

    private static ArrayList<Integer> sorted(ArrayList<Integer> list) {
        SortUtil.mergeSort(list, Integer::compare);
        return list;
    }

    @SafeVarargs
    private static ArrayList<ArrayList<Integer>> lists(ArrayList<Integer>... parts) {
        ArrayList<ArrayList<Integer>> all = new ArrayList<>();
        for (ArrayList<Integer> p : parts) all.add(p);
        return all;
    }

    private static String ids(ArrayList<Patient> list) {
        StringBuilder sb = new StringBuilder();
        for (Patient p : list) sb.append(sb.length() == 0 ? "" : ",").append(p.getPatientId());
        return sb.toString();
    }

    private static String vids(ArrayList<Visit> list) {
        StringBuilder sb = new StringBuilder();
        for (Visit v : list) sb.append(sb.length() == 0 ? "" : ",").append(v.getVisitId());
        return sb.toString();
    }

    private static String two(double d) { return String.format("%.2f", d); }
}
