package medflow;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * FINAL TESTING OF EVERY MENU WITH EVERY ROLE (Final Tester: Joshua Andrei B. Balingit).
 *
 * Runs the cases of the five role classes, in the order of their numbers:
 *   FinalReceptionTests  (Receptionist)    FinalNurseTests   (Nurse)      FinalDoctorTests (Clinician)
 *   FinalRecordsTests    (Records Staff)   FinalAdminTests   (Administrator)
 * Each case logs in as the role, opens a menu, types valid and invalid input, and checks the screen.
 * Inside one group ("Role menus", "Edge cases", "Queue and consultation workflow") the same data copy is used from
 * one case to the next, so a later role tests what an earlier role saved. Each group starts from a fresh copy of data/.
 *
 * Run from the MedFlow folder:   java -cp out medflow.FinalMenuTests [log.csv]
 * It prints PASS or FAIL for every case, a summary per role, and ends with exit code 1 if a case failed.
 * If a file name is given, the test log (menu, input, expected, result) is also written as a .csv file.
 */
public class FinalMenuTests {

    public static void main(String[] args) throws IOException {
        File data = new File("data");
        if (!data.isDirectory()) {
            System.out.println("Run this from the MedFlow folder (the folder that has data/).");
            System.exit(2);
        }
        List<FinalTestSupport.Case> all = new ArrayList<>();
        all.addAll(FinalReceptionTests.cases());
        all.addAll(FinalNurseTests.cases());
        all.addAll(FinalDoctorTests.cases());
        all.addAll(FinalRecordsTests.cases());
        all.addAll(FinalAdminTests.cases());
        all.sort((a, b) -> Integer.compare(a.no, b.no));

        FinalTestSupport support = null;
        String currentGroup = null;
        System.out.println("MEDFLOW FINAL MENU TESTS: every menu, every role, valid and invalid input");
        System.out.println("Cases: " + all.size() + " (each group works on its own copy of the data folder)\n");

        List<FinalTestSupport.Result> results = new ArrayList<>();
        int failed = 0;
        for (FinalTestSupport.Case c : all) {
            if (!c.group.equals(currentGroup)) {       // a new group starts from a fresh copy of the data
                support = new FinalTestSupport(data);
                currentGroup = c.group;
            }
            FinalTestSupport.Result r = support.run(c);
            results.add(r);
            System.out.printf("%s  %3d  %-9s %-34s %s%n", r.passed ? "PASS" : "FAIL", c.no, c.user,
                    c.menu, c.test);
            if (!r.passed) {
                failed++;
                System.out.println("        -> " + r.reason);
                String[] screen = r.screen.split("\n");
                for (int i = Math.max(0, screen.length - 14); i < screen.length; i++) System.out.println("        | " + screen[i]);
            }
        }

        System.out.println();
        String[] users = {"reception", "nurse", "doctor", "records", "admin", "clerk9"};
        for (String u : users) {
            int n = 0, ok = 0;
            for (FinalTestSupport.Result r : results) {
                if (r.testCase.user.equals(u)) { n++; if (r.passed) ok++; }
            }
            System.out.printf("%-10s %3d cases, %3d passed%n", u, n, ok);
        }
        System.out.println();
        System.out.println(failed == 0 ? "All " + all.size() + " final menu tests passed."
                : failed + " of " + all.size() + " final menu tests FAILED.");

        if (args.length > 0) writeLog(new File(args[0]), results);
        if (failed > 0) System.exit(1);
    }

    /** Writes the test log as a .csv file (No, Group, Role, Menu, Test, Input, Expected, Result). */
    private static void writeLog(File file, List<FinalTestSupport.Result> results) throws IOException {
        try (PrintWriter w = new PrintWriter(file, StandardCharsets.UTF_8)) {
            w.println("No,Group,Role,Menu,Test,Input,Expected,Result");
            for (FinalTestSupport.Result r : results) {
                FinalTestSupport.Case c = r.testCase;
                StringBuilder exp = new StringBuilder(String.join("; ", c.expected));
                for (String n : c.notExpected) exp.append(" | must NOT appear: ").append(n);
                String input = c.input.length == 0 ? "(log in only)" : String.join(" | ", c.input);
                w.println(c.no + "," + csv(c.group) + "," + csv(c.user) + "," + csv(c.menu) + "," + csv(c.test) + ","
                        + csv(input) + "," + csv(exp.toString()) + "," + (r.passed ? "PASS" : "FAIL"));
            }
        }
    }

    private static String csv(String s) {
        return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
