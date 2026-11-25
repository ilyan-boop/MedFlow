package medflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Final test cases for the menus of the Administrator (user admin); also the login check of the account the administrator creates (clerk9).
 * Every case starts the real program, logs in, types the input lines (valid and invalid) and checks the text on the screen.
 * The cases are run by FinalMenuTests, in the order of their numbers, because a later case may use the data made by an earlier one.
 */
public class FinalAdminTests {

    /** All cases of this role. Columns: number, group, user, menu, test, input lines, text that must appear, text that must NOT appear. */
    public static List<FinalTestSupport.Case> cases() {
        List<FinalTestSupport.Case> c = new ArrayList<>();
        // ---- Role menus: Main menu ----
        c.add(new FinalTestSupport.Case(92, "Role menus", "admin", "Main menu",
                "Admin menu",
                new String[]{},
                new String[]{"Daily report and export", "View the audit log", "Manage user accounts"}, new String[]{"Register a patient", "Update clinical notes", "Correct patient details"}));
        // ---- Role menus: Daily report and export ----
        c.add(new FinalTestSupport.Case(93, "Role menus", "admin", "Daily report and export",
                "Report for 2026-11-17",
                new String[]{"4", "2026-11-17", "0"},
                new String[]{"DAILY REPORT", "2026-11-17"}, new String[]{}));
        c.add(new FinalTestSupport.Case(94, "Role menus", "admin", "Daily report and export",
                "Report with Enter = today",
                new String[]{"4", "", "0"},
                new String[]{"DAILY REPORT"}, new String[]{}));
        c.add(new FinalTestSupport.Case(95, "Role menus", "admin", "Daily report and export",
                "Future date rejected",
                new String[]{"4", "2030-01-01", "2026-11-17", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(96, "Role menus", "admin", "Daily report and export",
                "Wrong date format 17/11/2026",
                new String[]{"4", "17/11/2026", "2026-11-17", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(97, "Role menus", "admin", "Daily report and export",
                "Export as .txt",
                new String[]{"4", "2026-11-17", "1", "0"},
                new String[]{"Exported to"}, new String[]{}));
        c.add(new FinalTestSupport.Case(98, "Role menus", "admin", "Daily report and export",
                "Export as .csv",
                new String[]{"4", "2026-11-17", "2", "0"},
                new String[]{"Exported to"}, new String[]{}));
        c.add(new FinalTestSupport.Case(99, "Role menus", "admin", "Daily report and export",
                "Show visits with one status",
                new String[]{"4", "2026-11-17", "3", "1", "0"},
                new String[]{"Completed"}, new String[]{}));
        // ---- Role menus: View the audit log ----
        c.add(new FinalTestSupport.Case(100, "Role menus", "admin", "View the audit log",
                "Latest 25 entries",
                new String[]{"5", "1", "0"},
                new String[]{"AUDIT LOG"}, new String[]{}));
        c.add(new FinalTestSupport.Case(101, "Role menus", "admin", "View the audit log",
                "Entries by user doctor",
                new String[]{"5", "2", "doctor", "0"},
                new String[]{"doctor"}, new String[]{}));
        c.add(new FinalTestSupport.Case(102, "Role menus", "admin", "View the audit log",
                "Entries about record P0009",
                new String[]{"5", "3", "P0009", "0"},
                new String[]{"P0009"}, new String[]{}));
        c.add(new FinalTestSupport.Case(103, "Role menus", "admin", "View the audit log",
                "Bad record ID 'ZZZ'",
                new String[]{"5", "3", "ZZZ", "P0009", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(104, "Role menus", "admin", "View the audit log",
                "Action contains 'LOGIN'",
                new String[]{"5", "4", "LOGIN", "0"},
                new String[]{"LOGIN"}, new String[]{}));
        c.add(new FinalTestSupport.Case(105, "Role menus", "admin", "View the audit log",
                "Action text too short 'ab'",
                new String[]{"5", "4", "ab", "LOGIN", "0"},
                new String[]{"Type at least 3 letters"}, new String[]{}));
        c.add(new FinalTestSupport.Case(106, "Role menus", "admin", "View the audit log",
                "All entries",
                new String[]{"5", "5", "0"},
                new String[]{"entries"}, new String[]{}));
        // ---- Role menus: Manage user accounts ----
        c.add(new FinalTestSupport.Case(107, "Role menus", "admin", "Manage user accounts",
                "List users, answer N",
                new String[]{"6", "N"},
                new String[]{"USER ACCOUNTS", "doctor"}, new String[]{}));
        c.add(new FinalTestSupport.Case(108, "Role menus", "admin", "Manage user accounts",
                "Add a valid account",
                new String[]{"6", "Y", "clerk01", "Clerk#123", "Pia Santos", "1"},
                new String[]{"Account created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(109, "Role menus", "admin", "Manage user accounts",
                "Duplicate username rejected",
                new String[]{"6", "Y", "doctor", "Clerk#123", "Pia Santos", "1"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(110, "Role menus", "admin", "Manage user accounts",
                "Weak password rejected",
                new String[]{"6", "Y", "clerk02", "abc", "Clerk#123", "Pia Santos", "1"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(111, "Role menus", "admin", "Manage user accounts",
                "Invalid role 9",
                new String[]{"6", "Y", "clerk03", "Clerk#123", "Pia Santos", "9", "1"},
                new String[]{"Please enter a number from 1 to"}, new String[]{}));
        // ---- Role menus: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(112, "Role menus", "admin", "Visit status board and tracking",
                "Admin tracks visit V0002",
                new String[]{"1", "1", "1", "V0002", "0"},
                new String[]{"V0002"}, new String[]{}));
        // ---- Role menus: Search patients and visits ----
        c.add(new FinalTestSupport.Case(113, "Role menus", "admin", "Search patients and visits",
                "Admin searches all patients",
                new String[]{"2", "3"},
                new String[]{"record(s) found"}, new String[]{}));
        // ---- Role menus: View a patient record ----
        c.add(new FinalTestSupport.Case(114, "Role menus", "admin", "View a patient record",
                "Admin views record P0004",
                new String[]{"3", "1", "P0004"},
                new String[]{"Santos"}, new String[]{}));
        // ---- Edge cases: Manage user accounts ----
        c.add(new FinalTestSupport.Case(134, "Edge cases", "admin", "Manage user accounts",
                "Password with a comma is rejected (rule)",
                new String[]{"6", "Y", "clerk7", "Cl3rk,pass", "Cl3rkpass2", "Pia Santos", "1"},
                new String[]{"Password cannot contain spaces or commas", "Account created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(135, "Edge cases", "admin", "Manage user accounts",
                "Add a valid new account",
                new String[]{"6", "Y", "clerk9", "Cl3rkpass", "Pia Santos", "1"},
                new String[]{"Account created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(136, "Edge cases", "admin", "Manage user accounts",
                "Add account with 300-char username",
                new String[]{"6", "Y", "uuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu", "clerk8", "Cl3rkpass", "Pia Santos", "1"},
                new String[]{"ERROR"}, new String[]{}));
        // ---- Edge cases: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(137, "Edge cases", "admin", "Visit status board and tracking",
                "Track visit id lowercase v0001",
                new String[]{"1", "1", "1", "v0001", "0"},
                new String[]{"V0001"}, new String[]{}));
        // ---- Edge cases: Main menu ----
        c.add(new FinalTestSupport.Case(138, "Edge cases", "clerk9", "Main menu",
                "Login with the new new account works",
                new String[]{},
                new String[]{"(Receptionist)"}, new String[]{}));
        // ---- Queue and consultation workflow: View the audit log ----
        c.add(new FinalTestSupport.Case(154, "Queue and consultation workflow", "admin", "View the audit log",
                "Audit log shows the outcome and call actions",
                new String[]{"5", "4", "CALL", "0"},
                new String[]{"CALL"}, new String[]{}));
        // ---- Queue and consultation workflow: Daily report and export ----
        c.add(new FinalTestSupport.Case(155, "Queue and consultation workflow", "admin", "Daily report and export",
                "Today's report counts the visits of today",
                new String[]{"4", "", "0"},
                new String[]{"DAILY REPORT"}, new String[]{}));
        // ---- Extra checks (fresh copy of the data) ----
        c.add(new FinalTestSupport.Case(175, "Extra checks", "admin", "Search patients and visits",
                "Bad patient ID 'abc' is refused",
                new String[]{"2", "1", "abc", "P0001"},
                new String[]{"Patient ID must look like P0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(176, "Extra checks", "admin", "Search patients and visits",
                "Name with no match 'Zzz'",
                new String[]{"2", "2", "Zzz"},
                new String[]{"No patient matched"}, new String[]{}));
        c.add(new FinalTestSupport.Case(177, "Extra checks", "admin", "View a patient record",
                "Clinical notes are hidden for the Administrator",
                new String[]{"3", "1", "P0001"},
                new String[]{"hidden for the Administrator role"}, new String[]{"Penicillin", "Hypertension"}));
        c.add(new FinalTestSupport.Case(178, "Extra checks", "admin", "View a patient record",
                "Unknown patient P9999",
                new String[]{"3", "1", "P9999"},
                new String[]{"No patient has the ID P9999"}, new String[]{}));
        c.add(new FinalTestSupport.Case(179, "Extra checks", "admin", "Visit status board and tracking",
                "Status board opens",
                new String[]{"1", "0"},
                new String[]{"VISIT STATUS BOARD"}, new String[]{}));
        return c;
    }
}
