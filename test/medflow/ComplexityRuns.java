package medflow;

import medflow.ds.AuditLog;
import medflow.ds.BinaryHeap;
import medflow.ds.LinkedQueue;
import medflow.ds.VersionStack;
import medflow.model.Patient;
import medflow.model.Sex;
import medflow.service.AuthService;
import medflow.service.PatientService;
import medflow.util.MergeUtil;
import medflow.util.SearchUtil;
import medflow.util.SortUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.TreeMap;

/**
 * Time complexity test runs (Expected Results, Section 5.6 of the proposal): each structure and algorithm is run on
 * sample data of growing size n, and the work per item is printed. Two measures are used:
 *
 *   comparisons   counted exactly with a counting comparator, so the numbers are the same on every
 *                 computer. These show the growth rate clearly (n log n, log n, and so on).
 *   time          average nanoseconds per operation (best of several rounds). Time depends on the
 *                 computer and the JVM, so only the trend matters.
 *
 * Run with:  java -cp out medflow.ComplexityRuns
 */
public class ComplexityRuns {

    private static final int[] SIZES = {1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000};
    private static final int ROUNDS = 7;
    private static long count;                                       // comparisons in the current run

    private static <T> Comparator<T> counting(Comparator<T> c) {
        return (a, b) -> { count++; return c.compare(a, b); };
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    private interface Run { void go(int n); }

    /** Best (smallest) time of several rounds, in nanoseconds. The first rounds also warm up the JVM. */
    private static long bestTime(int n, Run setupAndRun) {
        long best = Long.MAX_VALUE;
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            setupAndRun.go(n);
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best;
    }

    private static ArrayList<Integer> randomInts(int n, long seed) {
        Random rnd = new Random(seed);
        ArrayList<Integer> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(rnd.nextInt(1_000_000));
        return list;
    }

    public static void main(String[] args) {
        // Warm-up: run everything once without printing, so the JVM has compiled the code before timing.
        java.io.PrintStream real = System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        runAll();
        System.setOut(real);
        runAll();
    }

    private static void runAll() {
        System.out.println("MedFlow time complexity test runs (Milestone 6)");
        System.out.println("Computer: Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
                + ", run on " + LocalDateTime.now().withSecond(0).withNano(0));
        System.out.println("Comparisons are exact counts. Times are the best of " + ROUNDS + " rounds after a warm-up run,");
        System.out.println("in nanoseconds (ns) per operation, so only their trend matters.");

        // ------------------------------------------------------------ 1. merge sort
        System.out.println();
        System.out.println("1. Merge sort (SortUtil)  expected O(n log n)");
        System.out.printf("   %8s %14s %14s %16s %14s%n", "n", "comparisons", "n log2 n", "comparisons /", "time per item");
        System.out.printf("   %8s %14s %14s %16s %14s%n", "", "", "", "(n log2 n)", "(ns)");
        for (int n : SIZES) {
            ArrayList<Integer> data = randomInts(n, n);
            count = 0;
            SortUtil.mergeSort(new ArrayList<>(data), counting(Integer::compare));
            long comps = count;
            long t = bestTime(n, k -> SortUtil.mergeSort(new ArrayList<>(data), Integer::compare));
            System.out.printf("   %8d %14d %14.0f %16.3f %14.1f%n", n, comps, n * log2(n), comps / (n * log2(n)), (double) t / n);
        }

        // ------------------------------------------------------------ 2. binary search vs linear search
        System.out.println();
        System.out.println("2. Binary search vs linear search (SearchUtil), average over every item in the list");
        System.out.printf("   %8s %16s %10s %16s %12s %14s%n", "n", "binary: compares", "log2 n", "linear: compares", "n / 2", "binary time");
        System.out.printf("   %8s %16s %10s %16s %12s %14s%n", "", "per search", "", "per search", "", "(ns/search)");
        for (int n : SIZES) {
            ArrayList<Integer> sorted = new ArrayList<>(n);
            for (int i = 0; i < n; i++) sorted.add(i * 2);
            count = 0;
            for (int i = 0; i < n; i++) {
                final int key = i * 2;
                SearchUtil.binarySearch(sorted, key, (item, k) -> { count++; return Integer.compare(item, k); });
            }
            double binary = (double) count / n;
            long linear = 0;
            int step = Math.max(1, n / 500);                             // linear search is slow, so sample 500 keys
            int samples = 0;
            for (int i = 0; i < n; i += step) {
                for (int j = 0; j < n; j++) { linear++; if (sorted.get(j) == i * 2) break; }
                samples++;
            }
            long t = bestTime(n, k -> { for (int i = 0; i < k; i++) { final int key = i * 2;
                SearchUtil.binarySearch(sorted, key, (item, kk) -> Integer.compare(item, kk)); } });
            System.out.printf("   %8d %16.2f %10.2f %16.1f %12.1f %14.1f%n", n, binary, log2(n), (double) linear / samples, n / 2.0, (double) t / n);
        }

        // ------------------------------------------------------------ 3. k-way merge
        System.out.println();
        System.out.println("3. k-way merge of 3 sorted lists (MergeUtil with BinaryHeap)  expected O(N log k), k = 3");
        System.out.printf("   %8s %14s %18s %14s%n", "N", "comparisons", "comparisons / N", "time per item");
        System.out.printf("   %8s %14s %18s %14s%n", "", "", "(stays the same)", "(ns)");
        for (int n : SIZES) {
            ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
            for (int s = 0; s < 3; s++) {
                ArrayList<Integer> l = randomInts(n / 3, n + s);
                SortUtil.mergeSort(l, Integer::compare);
                lists.add(l);
            }
            int total = 3 * (n / 3);
            count = 0;
            MergeUtil.kWayMerge(lists, counting(Integer::compare));
            long comps = count;
            long t = bestTime(n, k -> MergeUtil.kWayMerge(lists, Integer::compare));
            System.out.printf("   %8d %14d %18.3f %14.1f%n", total, comps, (double) comps / total, (double) t / total);
        }
        System.out.println("   For comparison, sorting all N visits again with merge sort would need about N log2 N comparisons.");

        // ------------------------------------------------------------ 4. binary heap
        System.out.println();
        System.out.println("4. Binary heap: n inserts then n removeMin (BinaryHeap)  expected O(log n) per operation");
        System.out.printf("   %8s %18s %10s %14s%n", "n", "compares per op", "log2 n", "time per op");
        System.out.printf("   %8s %18s %10s %14s%n", "", "", "", "(ns)");
        for (int n : SIZES) {
            ArrayList<Integer> data = randomInts(n, n + 7);
            count = 0;
            BinaryHeap<Integer> h = new BinaryHeap<>(counting(Integer::compare));
            for (int x : data) h.insert(x);
            while (!h.isEmpty()) h.removeMin();
            long comps = count;
            long t = bestTime(n, k -> {
                BinaryHeap<Integer> hh = new BinaryHeap<>(Integer::compare);
                for (int x : data) hh.insert(x);
                while (!hh.isEmpty()) hh.removeMin();
            });
            System.out.printf("   %8d %18.2f %10.2f %14.1f%n", n, comps / (2.0 * n), log2(n), t / (2.0 * n));
        }

        // ------------------------------------------------------------ 5. O(1) structures
        System.out.println();
        System.out.println("5. Constant-time operations  expected O(1): the time per operation stays about the same as n grows");
        System.out.printf("   %8s %18s %20s %20s %18s%n", "n", "HashMap lookup", "LinkedQueue enqueue", "VersionStack push", "TreeMap get");
        System.out.printf("   %8s %18s %20s %20s %18s%n", "", "by patient ID (ns)", "+ dequeue (ns)", "+ peek (ns)", "O(log n) (ns)");
        for (int n : SIZES) {
            AuditLog log = new AuditLog();
            AuthService auth = new AuthService(log);
            PatientService ps = new PatientService(auth, log);
            String[] ids = new String[n];
            for (int i = 0; i < n; i++) {
                ids[i] = String.format("P%05d", i + 1);
                ps.loadPatient(new Patient(ids[i], "Last" + i, "First" + i, "", LocalDate.of(1990, 1, 1), Sex.FEMALE,
                        "1 Sample St., Manila", "09170000000", "system", LocalDateTime.of(2026, 11, 1, 8, 0)));
            }
            long tHash = bestTime(n, k -> { for (int i = 0; i < k; i++) ps.findById(ids[i]); });
            long tQueue = bestTime(n, k -> {
                LinkedQueue<Integer> q = new LinkedQueue<>();
                for (int i = 0; i < k; i++) q.enqueue(i);
                while (!q.isEmpty()) q.dequeue();
            });
            long tStack = bestTime(n, k -> {
                VersionStack<Integer> s = new VersionStack<>();
                for (int i = 0; i < k; i++) { s.push(i); s.peek(); }
            });
            TreeMap<Integer, Integer> tree = new TreeMap<>();
            for (int i = 0; i < n; i++) tree.put(i * 3, i);
            long tTree = bestTime(n, k -> { for (int i = 0; i < k; i++) tree.get(i * 3); });
            System.out.printf("   %8d %18.1f %20.1f %20.1f %18.1f%n", n, (double) tHash / n, (double) tQueue / n,
                    (double) tStack / n, (double) tTree / n);
        }
        System.out.println();
        System.out.println("Done.");
    }
}
