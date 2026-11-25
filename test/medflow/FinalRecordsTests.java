package medflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Final test cases for the menus of the Records Staff (user records).
 * Every case starts the real program, logs in, types the input lines (valid and invalid) and checks the text on the screen.
 * The cases are run by FinalMenuTests, in the order of their numbers, because a later case may use the data made by an earlier one.
 */
public class FinalRecordsTests {

    /** All cases of this role. Columns: number, group, user, menu, test, input lines, text that must appear, text that must NOT appear. */
    public static List<FinalTestSupport.Case> cases() {
        List<FinalTestSupport.Case> c = new ArrayList<>();
        // ---- Role menus: Main menu ----
        c.add(new FinalTestSupport.Case(80, "Role menus", "records", "Main menu",
                "Records menu items",
                new String[]{},
                new String[]{"Correct patient details", "Review possible duplicate records", "Version history of a record"}, new String[]{"Register a patient", "Update clinical notes", "Manage user accounts"}));
        // ---- Role menus: Correct patient details ----
        c.add(new FinalTestSupport.Case(81, "Role menus", "records", "Correct patient details",
                "Correct the address of P0009 and save",
                new String[]{"3", "1", "P0009", "6", "Blk 5 Lot 3, Pasig City", "Y", "0"},
                new String[]{"Saved", "Blk 5 Lot 3"}, new String[]{}));
        c.add(new FinalTestSupport.Case(82, "Role menus", "records", "Correct patient details",
                "Invalid contact number rejected",
                new String[]{"3", "1", "P0009", "7", "12345", "09170001111", "N", "0"},
                new String[]{"Contact number must have 11 digits"}, new String[]{}));
        c.add(new FinalTestSupport.Case(83, "Role menus", "records", "Correct patient details",
                "Invalid birthdate (future) rejected",
                new String[]{"3", "1", "P0009", "4", "2031-01-01", "2001-03-04", "N", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(84, "Role menus", "records", "Correct patient details",
                "Save N: nothing changed",
                new String[]{"3", "1", "P0009", "6", "Another St", "N", "0"},
                new String[]{"Nothing was changed"}, new String[]{}));
        c.add(new FinalTestSupport.Case(85, "Role menus", "records", "Correct patient details",
                "Unknown patient P6666",
                new String[]{"3", "1", "P6666"},
                new String[]{"No patient has the ID P6666"}, new String[]{}));
        // ---- Role menus: Version history of a record ----
        c.add(new FinalTestSupport.Case(86, "Role menus", "records", "Version history of a record",
                "History of P0009 shows the correction",
                new String[]{"4", "1", "P0009", "0"},
                new String[]{"Address"}, new String[]{}));
        c.add(new FinalTestSupport.Case(87, "Role menus", "records", "Version history of a record",
                "View saved version 1 in full",
                new String[]{"4", "1", "P0009", "1", "1", "0"},
                new String[]{"Version"}, new String[]{}));
        c.add(new FinalTestSupport.Case(88, "Role menus", "records", "Version history of a record",
                "Undo the latest change",
                new String[]{"4", "1", "P0009", "2", "Y", "0"},
                new String[]{"Undone"}, new String[]{}));
        // ---- Role menus: Review possible duplicate records ----
        c.add(new FinalTestSupport.Case(89, "Role menus", "records", "Review possible duplicate records",
                "List pending duplicate flags",
                new String[]{"5", "0"},
                new String[]{"DUPLICATE"}, new String[]{}));
        // ---- Role menus: Search patients and visits ----
        c.add(new FinalTestSupport.Case(90, "Role menus", "records", "Search patients and visits",
                "Records staff search by name",
                new String[]{"1", "2", "Tan"},
                new String[]{"Tan"}, new String[]{}));
        // ---- Role menus: View a patient record ----
        c.add(new FinalTestSupport.Case(91, "Role menus", "records", "View a patient record",
                "Records staff view record P0003",
                new String[]{"2", "1", "P0003"},
                new String[]{"Garcia"}, new String[]{}));
        c.add(new FinalTestSupport.Case(139, "Edge cases", "records", "View a patient record",
                "Record with quotes and comma in address survives save and reload (second session)",
                new String[]{"2", "1", "P0009"},
                new String[]{"Unit 5, Main St, Pasig", "O'Brien"}, new String[]{}));
        // ---- Extra checks (fresh copy of the data) ----
        c.add(new FinalTestSupport.Case(170, "Extra checks", "records", "Search patients and visits",
                "Bad patient ID 'P99' is refused",
                new String[]{"1", "1", "P99", "P0001"},
                new String[]{"Patient ID must look like P0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(171, "Extra checks", "records", "Search patients and visits",
                "Name with no match 'Zzz'",
                new String[]{"1", "2", "Zzz"},
                new String[]{"No patient matched"}, new String[]{}));
        c.add(new FinalTestSupport.Case(172, "Extra checks", "records", "View a patient record",
                "Clinical notes are hidden for Records Staff",
                new String[]{"2", "1", "P0001"},
                new String[]{"hidden for the Records Staff role"}, new String[]{"Penicillin", "Hypertension"}));
        c.add(new FinalTestSupport.Case(173, "Extra checks", "records", "Review possible duplicate records",
                "No pending flags on the fresh data",
                new String[]{"5", "0"},
                new String[]{"No possible duplicates to review"}, new String[]{}));
        c.add(new FinalTestSupport.Case(174, "Extra checks", "records", "Main menu",
                "Invalid main menu choice 9",
                new String[]{"9"},
                new String[]{"Please enter a number from 0 to 5"}, new String[]{}));
        return c;
    }
}
