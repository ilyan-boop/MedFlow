package medflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Final test cases for the menus of the Nurse (user nurse).
 * Every case starts the real program, logs in, types the input lines (valid and invalid) and checks the text on the screen.
 * The cases are run by FinalMenuTests, in the order of their numbers, because a later case may use the data made by an earlier one.
 */
public class FinalNurseTests {

    /** All cases of this role. Columns: number, group, user, menu, test, input lines, text that must appear, text that must NOT appear. */
    public static List<FinalTestSupport.Case> cases() {
        List<FinalTestSupport.Case> c = new ArrayList<>();
        // ---- Role menus: Main menu ----
        c.add(new FinalTestSupport.Case(50, "Role menus", "nurse", "Main menu",
                "Nurse menu shows only queue, status board, search, view record",
                new String[]{},
                new String[]{"Call patients / manage the queue", "Visit status board and tracking"}, new String[]{"Register a patient", "Consultations:", "Manage user accounts", "Update clinical notes"}));
        // ---- Role menus: Call patients / manage the queue ----
        c.add(new FinalTestSupport.Case(51, "Role menus", "nurse", "Call patients / manage the queue",
                "Open the Family Medicine queue",
                new String[]{"1", "1", "0"},
                new String[]{"Family Medicine queue"}, new String[]{}));
        c.add(new FinalTestSupport.Case(52, "Role menus", "nurse", "Call patients / manage the queue",
                "Call next patient in Family Medicine",
                new String[]{"1", "1", "1", "0"},
                new String[]{"NOW CALLING"}, new String[]{}));
        c.add(new FinalTestSupport.Case(53, "Role menus", "nurse", "Call patients / manage the queue",
                "Patient missing moves to the back",
                new String[]{"1", "1", "1", "2", "0"},
                new String[]{"marked as missed"}, new String[]{}));
        c.add(new FinalTestSupport.Case(54, "Role menus", "nurse", "Call patients / manage the queue",
                "Call next again, then start consultation",
                new String[]{"1", "1", "1", "1", "3", "0"},
                new String[]{"now in consultation"}, new String[]{}));
        c.add(new FinalTestSupport.Case(55, "Role menus", "nurse", "Call patients / manage the queue",
                "Start consultation with nobody called (Internal Medicine)",
                new String[]{"1", "2", "3", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(56, "Role menus", "nurse", "Call patients / manage the queue",
                "Call next in an empty line (Pediatrics)",
                new String[]{"1", "3", "1", "0"},
                new String[]{"Pediatrics"}, new String[]{}));
        c.add(new FinalTestSupport.Case(57, "Role menus", "nurse", "Call patients / manage the queue",
                "Invalid queue choice 7",
                new String[]{"1", "7", "0"},
                new String[]{"Please enter a number from 0 to 3"}, new String[]{}));
        c.add(new FinalTestSupport.Case(58, "Role menus", "nurse", "Call patients / manage the queue",
                "Cancel a visit, answer N",
                new String[]{"1", "1", "4", "1", "N", "0"},
                new String[]{"Cancel"}, new String[]{}));
        // ---- Role menus: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(59, "Role menus", "nurse", "Visit status board and tracking",
                "Status board",
                new String[]{"2", "0"},
                new String[]{"VISIT STATUS BOARD"}, new String[]{}));
        c.add(new FinalTestSupport.Case(60, "Role menus", "nurse", "Visit status board and tracking",
                "Nurse (clinical-notes permission) sees the outcome note of V0001",
                new String[]{"2", "1", "1", "V0001", "0"},
                new String[]{"V0001", "Viral infection"}, new String[]{}));
        // ---- Role menus: Search patients and visits ----
        c.add(new FinalTestSupport.Case(61, "Role menus", "nurse", "Search patients and visits",
                "Search by name 'Garcia'",
                new String[]{"3", "2", "Garcia"},
                new String[]{"Garcia"}, new String[]{}));
        c.add(new FinalTestSupport.Case(62, "Role menus", "nurse", "Search patients and visits",
                "Invalid name 'G@rcia'",
                new String[]{"3", "2", "G@rcia", "Garcia"},
                new String[]{"ERROR"}, new String[]{}));
        // ---- Role menus: View a patient record ----
        c.add(new FinalTestSupport.Case(63, "Role menus", "nurse", "View a patient record",
                "Nurse sees no clinical notes? view P0003",
                new String[]{"4", "1", "P0003"},
                new String[]{"Garcia"}, new String[]{}));
        // ---- Queue and consultation workflow: Call patients / manage the queue ----
        c.add(new FinalTestSupport.Case(145, "Queue and consultation workflow", "nurse", "Call patients / manage the queue",
                "Queue shows the priority patient first (position 1)",
                new String[]{"1", "1", "0"},
                new String[]{"Priority", "Waiting"}, new String[]{}));
        c.add(new FinalTestSupport.Case(146, "Queue and consultation workflow", "nurse", "Call patients / manage the queue",
                "Call next calls the priority patient (Santos) first",
                new String[]{"1", "1", "1", "0"},
                new String[]{"NOW CALLING", "Santos"}, new String[]{}));
        c.add(new FinalTestSupport.Case(147, "Queue and consultation workflow", "nurse", "Call patients / manage the queue",
                "Call next, then patient missing: moved to the back of the line",
                new String[]{"1", "1", "1", "2", "0"},
                new String[]{"marked as missed", "Moved to the back of the same line"}, new String[]{}));
        c.add(new FinalTestSupport.Case(148, "Queue and consultation workflow", "nurse", "Call patients / manage the queue",
                "Call next then start consultation",
                new String[]{"1", "1", "1", "3", "0"},
                new String[]{"now in consultation"}, new String[]{}));
        c.add(new FinalTestSupport.Case(149, "Queue and consultation workflow", "nurse", "Call patients / manage the queue",
                "Start consultation again with nobody called",
                new String[]{"1", "1", "3", "0"},
                new String[]{"ERROR"}, new String[]{}));
        // ---- Extra checks (fresh copy of the data) ----
        c.add(new FinalTestSupport.Case(160, "Extra checks", "nurse", "Search patients and visits",
                "Invalid sub-menu choice 9",
                new String[]{"3", "9"},
                new String[]{"Please enter a number from 0 to 5"}, new String[]{}));
        c.add(new FinalTestSupport.Case(161, "Extra checks", "nurse", "Search patients and visits",
                "Search by patient ID P0001",
                new String[]{"3", "1", "P0001"},
                new String[]{"1 record(s) found"}, new String[]{}));
        c.add(new FinalTestSupport.Case(162, "Extra checks", "nurse", "View a patient record",
                "Unknown patient P9999",
                new String[]{"4", "1", "P9999"},
                new String[]{"No patient has the ID P9999"}, new String[]{}));
        c.add(new FinalTestSupport.Case(163, "Extra checks", "nurse", "View a patient record",
                "Bad patient ID 'abc' is refused",
                new String[]{"4", "1", "abc", "P0001"},
                new String[]{"Patient ID must look like P0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(164, "Extra checks", "nurse", "Call patients / manage the queue",
                "Invalid service number 9",
                new String[]{"1", "9", "0"},
                new String[]{"Please enter a number from 0 to 3"}, new String[]{}));
        c.add(new FinalTestSupport.Case(165, "Extra checks", "nurse", "Visit status board and tracking",
                "Status board opens",
                new String[]{"2", "0"},
                new String[]{"VISIT STATUS BOARD"}, new String[]{}));
        return c;
    }
}
