package medflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Helper for the final menu tests. It starts the real program (medflow.Main) in a new Java process,
 * logs in as a role, types the input lines and returns everything the program printed.
 * The program works on a COPY of the data folder, so the real data files are never changed.
 */
public class FinalTestSupport {

    /** One test case: who logs in, what is typed, what must (and must not) appear on the screen. */
    public static class Case {
        public final int no;
        public final String group, user, menu, test;
        public final String[] input, expected, notExpected;

        public Case(int no, String group, String user, String menu, String test,
                    String[] input, String[] expected, String[] notExpected) {
            this.no = no; this.group = group; this.user = user; this.menu = menu; this.test = test;
            this.input = input; this.expected = expected; this.notExpected = notExpected;
        }
    }

    /** The result of one case. */
    public static class Result {
        public final Case testCase;
        public final boolean passed;
        public final String screen, reason;

        Result(Case testCase, boolean passed, String screen, String reason) {
            this.testCase = testCase; this.passed = passed; this.screen = screen; this.reason = reason;
        }
    }

    /** User names and passwords of the five roles (from data/users.csv) and the account the admin test creates. */
    private static final Map<String, String> PASSWORDS = new HashMap<>();
    static {
        PASSWORDS.put("reception", "rec123");
        PASSWORDS.put("nurse", "nur123");
        PASSWORDS.put("doctor", "doc123");
        PASSWORDS.put("records", "rec456");
        PASSWORDS.put("admin", "adm123");
        PASSWORDS.put("clerk9", "Cl3rkpass");
    }

    private final File dataCopy;

    /** Copies the data folder of the project to a temporary folder. */
    public FinalTestSupport(File dataFolder) throws IOException {
        Path temp = Files.createTempDirectory("medflow_final_test");
        dataCopy = new File(temp.toFile(), "data");
        dataCopy.mkdirs();
        File[] files = dataFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) Files.copy(f.toPath(), new File(dataCopy, f.getName()).toPath());
            }
        }
    }

    public File getDataCopy() { return dataCopy; }

    /** Runs one case and checks the screen text. */
    public Result run(Case c) {
        ArrayList<String> typed = new ArrayList<>();
        typed.add(c.user);
        typed.add(PASSWORDS.get(c.user));
        for (String s : c.input) typed.add(s);
        String screen;
        try {
            screen = runProgram(typed);
        } catch (Exception e) {
            return new Result(c, false, "", "could not run the program: " + e);
        }
        for (String text : c.expected) {
            if (!screen.contains(text)) return new Result(c, false, screen, "missing on screen: " + text);
        }
        for (String text : c.notExpected) {
            if (screen.contains(text)) return new Result(c, false, screen, "must NOT appear but did: " + text);
        }
        return new Result(c, true, screen, "");
    }

    /** Starts medflow.Main on the data copy, types the lines, and returns all output. The input ends after the last line. */
    private String runProgram(ArrayList<String> lines) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(javaCommand(), "-cp", System.getProperty("java.class.path"),
                "medflow.Main", dataCopy.getPath());
        pb.redirectErrorStream(true);
        pb.environment().put("MEDFLOW_ECHO", "1");
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("_JAVA_OPTIONS");
        Process p = pb.start();
        try (OutputStream out = p.getOutputStream()) {
            for (String line : lines) out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) text.append(line).append('\n');
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly();
        return text.toString();
    }

    private static String javaCommand() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }
}
