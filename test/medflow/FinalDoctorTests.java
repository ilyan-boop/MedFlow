package medflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Final test cases for the menus of the Clinician (user doctor).
 * Every case starts the real program, logs in, types the input lines (valid and invalid) and checks the text on the screen.
 * The cases are run by FinalMenuTests, in the order of their numbers, because a later case may use the data made by an earlier one.
 */
public class FinalDoctorTests {

    /** All cases of this role. Columns: number, group, user, menu, test, input lines, text that must appear, text that must NOT appear. */
    public static List<FinalTestSupport.Case> cases() {
        List<FinalTestSupport.Case> c = new ArrayList<>();
        // ---- Role menus: Main menu ----
        c.add(new FinalTestSupport.Case(64, "Role menus", "doctor", "Main menu",
                "Doctor menu has consultations, clinical notes, version history",
                new String[]{},
                new String[]{"Consultations: record the outcome", "Update clinical notes", "Version history of a record"}, new String[]{"Register a patient", "Manage user accounts", "Correct patient details"}));
        // ---- Role menus: Consultations: record the outcome ----
        c.add(new FinalTestSupport.Case(65, "Role menus", "doctor", "Consultations: record the outcome",
                "List consultations in progress",
                new String[]{"2", "0"},
                new String[]{"CONSULTATIONS IN PROGRESS"}, new String[]{}));
        c.add(new FinalTestSupport.Case(66, "Role menus", "doctor", "Consultations: record the outcome",
                "Choose 0 outcome (back): patient stays",
                new String[]{"2", "1", "0", "0"},
                new String[]{"RECORD OUTCOME"}, new String[]{}));
        c.add(new FinalTestSupport.Case(67, "Role menus", "doctor", "Consultations: record the outcome",
                "Empty outcome note rejected",
                new String[]{"2", "1", "1", "", "Viral infection. Rest and fluids.", "N", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(68, "Role menus", "doctor", "Consultations: record the outcome",
                "Invalid outcome choice 9",
                new String[]{"2", "1", "9", "0", "0"},
                new String[]{"Please enter a number from 0 to 3"}, new String[]{}));
        c.add(new FinalTestSupport.Case(69, "Role menus", "doctor", "Consultations: record the outcome",
                "Save outcome Treated",
                new String[]{"2", "1", "1", "Viral infection. Rest and fluids.", "Y"},
                new String[]{"Outcome recorded", "Completed"}, new String[]{}));
        // ---- Role menus: Call patients / manage the queue ----
        c.add(new FinalTestSupport.Case(70, "Role menus", "doctor", "Call patients / manage the queue",
                "Doctor calls next in Internal Medicine",
                new String[]{"1", "2", "1", "0"},
                new String[]{"Internal Medicine"}, new String[]{}));
        // ---- Role menus: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(71, "Role menus", "doctor", "Visit status board and tracking",
                "Doctor sees the outcome note of V0001",
                new String[]{"3", "1", "1", "V0001", "0"},
                new String[]{"Viral infection"}, new String[]{}));
        // ---- Role menus: Update clinical notes (allergies, history) ----
        c.add(new FinalTestSupport.Case(72, "Role menus", "doctor", "Update clinical notes (allergies, history)",
                "Update allergies of P0002 and save",
                new String[]{"6", "1", "P0002", "1", "Shellfish", "Y", "0"},
                new String[]{"Saved", "Shellfish"}, new String[]{}));
        c.add(new FinalTestSupport.Case(73, "Role menus", "doctor", "Update clinical notes (allergies, history)",
                "Save N: nothing changed",
                new String[]{"6", "1", "P0002", "2", "Asthma", "N", "0"},
                new String[]{"Nothing was changed"}, new String[]{}));
        c.add(new FinalTestSupport.Case(74, "Role menus", "doctor", "Update clinical notes (allergies, history)",
                "Overlong or unsafe text rejected",
                new String[]{"6", "1", "P0002", "1", "<script>xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "Dust", "N", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(75, "Role menus", "doctor", "Update clinical notes (allergies, history)",
                "Unknown patient P7777",
                new String[]{"6", "1", "P7777"},
                new String[]{"No patient has the ID P7777"}, new String[]{}));
        // ---- Role menus: Version history of a record ----
        c.add(new FinalTestSupport.Case(76, "Role menus", "doctor", "Version history of a record",
                "History of P0002 shows the change",
                new String[]{"7", "1", "P0002", "0"},
                new String[]{"Shellfish"}, new String[]{}));
        c.add(new FinalTestSupport.Case(77, "Role menus", "doctor", "Version history of a record",
                "Patient with no changes (P0005)",
                new String[]{"7", "1", "P0005"},
                new String[]{"No changes since the record was registered"}, new String[]{}));
        // ---- Role menus: View a patient record ----
        c.add(new FinalTestSupport.Case(78, "Role menus", "doctor", "View a patient record",
                "Doctor sees allergies of P0001",
                new String[]{"5", "1", "P0001"},
                new String[]{"Penicillin", "Hypertension"}, new String[]{}));
        // ---- Role menus: Search patients and visits ----
        c.add(new FinalTestSupport.Case(79, "Role menus", "doctor", "Search patients and visits",
                "Search visits by queue number FM-001",
                new String[]{"4", "5", "FM-001"},
                new String[]{"FM-001"}, new String[]{}));
        // ---- Queue and consultation workflow: Consultations: record the outcome ----
        c.add(new FinalTestSupport.Case(150, "Queue and consultation workflow", "doctor", "Consultations: record the outcome",
                "Follow-up outcome needs a note and is saved",
                new String[]{"2", "1", "2", "Wound check. Come back in one week.", "Y"},
                new String[]{"Outcome recorded", "Follow-up"}, new String[]{}));
        c.add(new FinalTestSupport.Case(151, "Queue and consultation workflow", "doctor", "Consultations: record the outcome",
                "No consultation is left after the outcome is saved",
                new String[]{"2"},
                new String[]{"No patient is in consultation"}, new String[]{}));
        // ---- Extra checks (fresh copy of the data) ----
        c.add(new FinalTestSupport.Case(166, "Extra checks", "doctor", "Call patients / manage the queue",
                "Invalid service number 9",
                new String[]{"1", "9", "0"},
                new String[]{"Please enter a number from 0 to 3"}, new String[]{}));
        c.add(new FinalTestSupport.Case(167, "Extra checks", "doctor", "Visit status board and tracking",
                "Bad visit ID 'V1' is refused, then V0001 works",
                new String[]{"3", "1", "1", "V1", "V0001", "0"},
                new String[]{"Visit ID must look like V0001", "V0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(168, "Extra checks", "doctor", "Search patients and visits",
                "Bad queue number 'FM1' is refused",
                new String[]{"4", "5", "FM1", "FM-001"},
                new String[]{"ERROR", "FM-001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(169, "Extra checks", "doctor", "Consultations: record the outcome",
                "Nobody in consultation (fresh data): clear message",
                new String[]{"2"},
                new String[]{"CONSULTATIONS IN PROGRESS", "No patient is in consultation."}, new String[]{}));
        return c;
    }
}
