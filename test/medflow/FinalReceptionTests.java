package medflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Final test cases for the menus of the Receptionist (user reception).
 * Every case starts the real program, logs in, types the input lines (valid and invalid) and checks the text on the screen.
 * The cases are run by FinalMenuTests, in the order of their numbers, because a later case may use the data made by an earlier one.
 */
public class FinalReceptionTests {

    /** All cases of this role. Columns: number, group, user, menu, test, input lines, text that must appear, text that must NOT appear. */
    public static List<FinalTestSupport.Case> cases() {
        List<FinalTestSupport.Case> c = new ArrayList<>();
        // ---- Role menus: Main menu ----
        c.add(new FinalTestSupport.Case(1, "Role menus", "reception", "Main menu",
                "Login screen lists only reception menu items; admin items hidden",
                new String[]{},
                new String[]{"Register a patient", "Create a visit and queue number", "Appointments and follow-ups"}, new String[]{"Manage user accounts", "View the audit log", "Daily report", "Consultations:"}));
        c.add(new FinalTestSupport.Case(2, "Role menus", "reception", "Main menu",
                "Invalid menu choice 9",
                new String[]{"9"},
                new String[]{"Please enter a number from 0 to 6"}, new String[]{}));
        c.add(new FinalTestSupport.Case(3, "Role menus", "reception", "Main menu",
                "Invalid menu choice letters 'abc'",
                new String[]{"abc"},
                new String[]{"Please enter a number from 0 to 6"}, new String[]{}));
        // ---- Role menus: Register a patient ----
        c.add(new FinalTestSupport.Case(4, "Role menus", "reception", "Register a patient",
                "Valid details, save, do not create a visit",
                new String[]{"1", "Lim", "Carlo", "", "2001-03-04", "M", "5 Pine St, Pasig", "09175551234", "Y", "N"},
                new String[]{"New record created", "Patient ID        : P0009", "Eligible services"}, new String[]{}));
        c.add(new FinalTestSupport.Case(5, "Role menus", "reception", "Register a patient",
                "Blank last name rejected",
                new String[]{"1", "", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"Last name is required"}, new String[]{}));
        c.add(new FinalTestSupport.Case(6, "Role menus", "reception", "Register a patient",
                "Name with digits rejected",
                new String[]{"1", "Cr4z", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(7, "Role menus", "reception", "Register a patient",
                "Impossible date 2000-02-30 rejected",
                new String[]{"1", "Cruz", "Ana", "", "2000-02-30", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"2000-02-30 is not a real date"}, new String[]{}));
        c.add(new FinalTestSupport.Case(8, "Role menus", "reception", "Register a patient",
                "Future birthdate rejected",
                new String[]{"1", "Cruz", "Ana", "", "2030-01-01", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(9, "Role menus", "reception", "Register a patient",
                "Wrong date format 05/05/1999 rejected",
                new String[]{"1", "Cruz", "Ana", "", "05/05/1999", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(10, "Role menus", "reception", "Register a patient",
                "Sex X rejected",
                new String[]{"1", "Cruz", "Ana", "", "1999-05-05", "X", "F", "Manila", "09171230000", "N"},
                new String[]{"Sex must be M or F"}, new String[]{}));
        c.add(new FinalTestSupport.Case(11, "Role menus", "reception", "Register a patient",
                "Contact number with 4 digits rejected",
                new String[]{"1", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "0917", "09171230000", "N"},
                new String[]{"Contact number must have 11 digits"}, new String[]{}));
        c.add(new FinalTestSupport.Case(12, "Role menus", "reception", "Register a patient",
                "Answer N at Save: nothing saved",
                new String[]{"1", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"Save this patient?"}, new String[]{"New record created"}));
        c.add(new FinalTestSupport.Case(13, "Role menus", "reception", "Register a patient",
                "Possible duplicate of P0001 is detected",
                new String[]{"1", "Dela Cruz", "Juan", "Santos", "1988-04-12", "M", "12 Mabini St., Quezon City", "09171234567", "Y", "P0001", "N"},
                new String[]{"P0001"}, new String[]{}));
        // ---- Role menus: Create a visit and queue number ----
        c.add(new FinalTestSupport.Case(14, "Role menus", "reception", "Create a visit and queue number",
                "Valid walk-in, FM, regular (P0001)",
                new String[]{"2", "1", "P0001", "Fever and cough", "1", "1", "1"},
                new String[]{"Visit created", "QUEUE NUMBER : FM-", "Regular line"}, new String[]{}));
        c.add(new FinalTestSupport.Case(15, "Role menus", "reception", "Create a visit and queue number",
                "Valid priority lane visit (P0004, senior)",
                new String[]{"2", "1", "P0004", "Chest tightness", "1", "2", "2"},
                new String[]{"Visit created", "Priority lane"}, new String[]{}));
        c.add(new FinalTestSupport.Case(16, "Role menus", "reception", "Create a visit and queue number",
                "Create by name search (Reyes)",
                new String[]{"2", "2", "Reyes", "1", "Headache", "1", "1", "1"},
                new String[]{"Visit created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(17, "Role menus", "reception", "Create a visit and queue number",
                "Unknown patient P9999",
                new String[]{"2", "1", "P9999"},
                new String[]{"No patient has the ID P9999"}, new String[]{}));
        c.add(new FinalTestSupport.Case(18, "Role menus", "reception", "Create a visit and queue number",
                "Bad ID format 'ABC'",
                new String[]{"2", "1", "ABC", "P0001", "", "1", "1", "1"},
                new String[]{"Patient ID must look like P0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(19, "Role menus", "reception", "Create a visit and queue number",
                "Blank reason rejected",
                new String[]{"2", "1", "P0001", "", "Sore throat", "1", "1", "1"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(20, "Role menus", "reception", "Create a visit and queue number",
                "Child (P0008, age 2) in Internal Medicine is refused, then Pediatrics",
                new String[]{"2", "1", "P0008", "Cough and colds", "1", "2", "3", "1"},
                new String[]{"ERROR", "Visit created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(21, "Role menus", "reception", "Create a visit and queue number",
                "Invalid visit type 9",
                new String[]{"2", "1", "P0001", "Back pain", "9", "1", "1", "1"},
                new String[]{"Please enter a number from 1 to"}, new String[]{}));
        // ---- Role menus: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(22, "Role menus", "reception", "Visit status board and tracking",
                "Status board shows counts",
                new String[]{"3", "0"},
                new String[]{"VISIT STATUS BOARD", "Family Medicine"}, new String[]{}));
        c.add(new FinalTestSupport.Case(23, "Role menus", "reception", "Visit status board and tracking",
                "Track one visit V0001 (outcome note hidden from reception)",
                new String[]{"3", "1", "1", "V0001", "0"},
                new String[]{"V0001", "hidden for the Receptionist role"}, new String[]{"Viral infection", "paracetamol"}));
        c.add(new FinalTestSupport.Case(24, "Role menus", "reception", "Visit status board and tracking",
                "Track visit with bad ID 'V1'",
                new String[]{"3", "1", "1", "V1", "V0001", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(25, "Role menus", "reception", "Visit status board and tracking",
                "Track unknown visit V9999",
                new String[]{"3", "1", "1", "V9999", "0"},
                new String[]{"V9999"}, new String[]{}));
        c.add(new FinalTestSupport.Case(26, "Role menus", "reception", "Visit status board and tracking",
                "Track all visits of patient P0001",
                new String[]{"3", "1", "2", "1", "P0001", "0"},
                new String[]{"V0001"}, new String[]{}));
        // ---- Role menus: Appointments and follow-ups ----
        c.add(new FinalTestSupport.Case(27, "Role menus", "reception", "Appointments and follow-ups",
                "Upcoming appointments list",
                new String[]{"4", "4", "0"},
                new String[]{"UPCOMING APPOINTMENTS"}, new String[]{}));
        c.add(new FinalTestSupport.Case(28, "Role menus", "reception", "Appointments and follow-ups",
                "Missed appointments list",
                new String[]{"4", "5", "0"},
                new String[]{"MISSED APPOINTMENTS"}, new String[]{}));
        c.add(new FinalTestSupport.Case(29, "Role menus", "reception", "Appointments and follow-ups",
                "Book a valid appointment",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-10", "09:00", "Y", "0"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(30, "Role menus", "reception", "Appointments and follow-ups",
                "Past date rejected",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2020-01-01", "2026-12-11", "09:00", "Y", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(31, "Role menus", "reception", "Appointments and follow-ups",
                "Time outside clinic hours (18:00) rejected",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-12", "18:00", "09:30", "Y", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(32, "Role menus", "reception", "Appointments and follow-ups",
                "Answer N at booking: nothing booked",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-14", "09:00", "N", "0"},
                new String[]{"Nothing was booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(33, "Role menus", "reception", "Appointments and follow-ups",
                "Appointments of a patient (P0001)",
                new String[]{"4", "8", "1", "P0001", "0"},
                new String[]{"APPOINTMENTS OF P0001", "A0005"}, new String[]{}));
        c.add(new FinalTestSupport.Case(34, "Role menus", "reception", "Appointments and follow-ups",
                "Cancel appointment A0007",
                new String[]{"4", "7", "A0007", "Y", "0"},
                new String[]{"A0007"}, new String[]{}));
        c.add(new FinalTestSupport.Case(35, "Role menus", "reception", "Appointments and follow-ups",
                "Cancel with bad appointment ID",
                new String[]{"4", "7", "X1", "A0006", "N", "0"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(36, "Role menus", "reception", "Appointments and follow-ups",
                "Reschedule A0006 to a valid slot",
                new String[]{"4", "6", "A0006", "2026-12-15", "10:00", "Y", "0"},
                new String[]{"A0006"}, new String[]{}));
        c.add(new FinalTestSupport.Case(37, "Role menus", "reception", "Appointments and follow-ups",
                "Today's appointments and check-in (answer N)",
                new String[]{"4", "3", "N", "0"},
                new String[]{"TODAY"}, new String[]{}));
        c.add(new FinalTestSupport.Case(38, "Role menus", "reception", "Appointments and follow-ups",
                "Invalid submenu choice 12",
                new String[]{"4", "12", "0"},
                new String[]{"Please enter a number from 0 to 8"}, new String[]{}));
        // ---- Role menus: Search patients and visits ----
        c.add(new FinalTestSupport.Case(39, "Role menus", "reception", "Search patients and visits",
                "Search by patient ID P0003",
                new String[]{"5", "1", "P0003"},
                new String[]{"Garcia", "1 record(s) found"}, new String[]{}));
        c.add(new FinalTestSupport.Case(40, "Role menus", "reception", "Search patients and visits",
                "Unknown patient ID P0999",
                new String[]{"5", "1", "P0999"},
                new String[]{"No patient has the ID P0999"}, new String[]{}));
        c.add(new FinalTestSupport.Case(41, "Role menus", "reception", "Search patients and visits",
                "Search by name 'Dela'",
                new String[]{"5", "2", "Dela"},
                new String[]{"Dela Cruz", "binary search"}, new String[]{}));
        c.add(new FinalTestSupport.Case(42, "Role menus", "reception", "Search patients and visits",
                "Search by part of a name 'ruz' (linear search)",
                new String[]{"5", "2", "ruz"},
                new String[]{"linear search"}, new String[]{}));
        c.add(new FinalTestSupport.Case(43, "Role menus", "reception", "Search patients and visits",
                "Name with no match 'Zzz'",
                new String[]{"5", "2", "Zzz"},
                new String[]{"No patient matched"}, new String[]{}));
        c.add(new FinalTestSupport.Case(44, "Role menus", "reception", "Search patients and visits",
                "All patients sorted by name",
                new String[]{"5", "3"},
                new String[]{"record(s) found", "Aquino"}, new String[]{}));
        c.add(new FinalTestSupport.Case(45, "Role menus", "reception", "Search patients and visits",
                "Visit by visit ID V0003",
                new String[]{"5", "4", "V0003"},
                new String[]{"V0003"}, new String[]{}));
        c.add(new FinalTestSupport.Case(46, "Role menus", "reception", "Search patients and visits",
                "Visits by queue number FM-001",
                new String[]{"5", "5", "FM-001"},
                new String[]{"FM-001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(47, "Role menus", "reception", "Search patients and visits",
                "Bad queue number 'FM1'",
                new String[]{"5", "5", "FM1", "FM-001"},
                new String[]{"ERROR"}, new String[]{}));
        // ---- Role menus: View a patient record ----
        c.add(new FinalTestSupport.Case(48, "Role menus", "reception", "View a patient record",
                "View record P0001 (reception sees demographics only)",
                new String[]{"6", "1", "P0001"},
                new String[]{"Dela Cruz"}, new String[]{"Penicillin", "Hypertension"}));
        c.add(new FinalTestSupport.Case(49, "Role menus", "reception", "View a patient record",
                "Unknown patient P8888",
                new String[]{"6", "1", "P8888"},
                new String[]{"No patient has the ID P8888"}, new String[]{}));
        // ---- Edge cases: Register a patient ----
        c.add(new FinalTestSupport.Case(115, "Edge cases", "reception", "Register a patient",
                "Name with apostrophe, address with commas",
                new String[]{"1", "O'Brien", "Sean Paul", "", "1990-06-01", "M", "Unit 5, Main St, Pasig", "09175550000", "Y", "N"},
                new String[]{"New record created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(116, "Edge cases", "reception", "Register a patient",
                "Name of 200 letters rejected",
                new String[]{"1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Ana", "", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(117, "Edge cases", "reception", "Register a patient",
                "Name with spaces only rejected",
                new String[]{"1", "   ", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(118, "Edge cases", "reception", "Register a patient",
                "Contact number not starting with 09 (08171234567)",
                new String[]{"1", "Cruz", "Ana", "", "1999-05-05", "F", "Manila", "08171234567", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(119, "Edge cases", "reception", "Register a patient",
                "Birthdate 1890-01-01 (age 136)",
                new String[]{"1", "Cruz", "Ana", "", "1890-01-01", "1999-05-05", "F", "Manila", "09171230000", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(120, "Edge cases", "reception", "Register a patient",
                "Newborn born today (age 0) is registered",
                new String[]{"1", "Baby", "Test", "", "2026-11-24", "F", "Manila", "09171230000", "Y", "N"},
                new String[]{"Pediatrics"}, new String[]{}));
        c.add(new FinalTestSupport.Case(121, "Edge cases", "reception", "Register a patient",
                "Lowercase sex 'f' and lowercase 'y' accepted",
                new String[]{"1", "Reyes", "Lea", "", "1995-01-02", "f", "Manila", "09171230001", "y", "N"},
                new String[]{"New record created"}, new String[]{}));
        // ---- Edge cases: Search patients and visits ----
        c.add(new FinalTestSupport.Case(122, "Edge cases", "reception", "Search patients and visits",
                "Lowercase patient ID p0001",
                new String[]{"5", "1", "p0001"},
                new String[]{"Dela Cruz"}, new String[]{}));
        c.add(new FinalTestSupport.Case(123, "Edge cases", "reception", "Search patients and visits",
                "Patient ID with spaces ' P0001 '",
                new String[]{"5", "1", " P0001 "},
                new String[]{"Dela Cruz"}, new String[]{}));
        c.add(new FinalTestSupport.Case(124, "Edge cases", "reception", "Search patients and visits",
                "Find the apostrophe name O'Brien",
                new String[]{"5", "2", "O'Brien"},
                new String[]{"O'Brien"}, new String[]{}));
        // ---- Edge cases: Appointments and follow-ups ----
        c.add(new FinalTestSupport.Case(125, "Edge cases", "reception", "Appointments and follow-ups",
                "Time 16:30 accepted (last slot)",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-16", "16:30", "Y"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(126, "Edge cases", "reception", "Appointments and follow-ups",
                "Time 16:31 rejected",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-16", "16:31", "09:00", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(127, "Edge cases", "reception", "Appointments and follow-ups",
                "Time 07:59 rejected",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-16", "07:59", "09:00", "N"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(128, "Edge cases", "reception", "Appointments and follow-ups",
                "Book 1: same slot 10:00 on 2026-12-17",
                new String[]{"4", "1", "1", "P0001", "1", "Check-up", "2026-12-17", "10:00", "Y"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(129, "Edge cases", "reception", "Appointments and follow-ups",
                "Book 2: same slot",
                new String[]{"4", "1", "1", "P0002", "1", "Check-up", "2026-12-17", "10:00", "Y"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(130, "Edge cases", "reception", "Appointments and follow-ups",
                "Book 3: same slot",
                new String[]{"4", "1", "1", "P0003", "1", "Check-up", "2026-12-17", "10:00", "Y"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(131, "Edge cases", "reception", "Appointments and follow-ups",
                "Book 4: same slot (capacity reached)",
                new String[]{"4", "1", "1", "P0004", "1", "Check-up", "2026-12-17", "10:00", "Y", "2026-12-17", "10:30", "Y"},
                new String[]{"Booked"}, new String[]{}));
        c.add(new FinalTestSupport.Case(132, "Edge cases", "reception", "Appointments and follow-ups",
                "Book same patient twice in the same slot",
                new String[]{"4", "1", "1", "P0001", "1", "Again", "2026-12-17", "10:00", "Y", "2026-12-18", "09:00", "Y"},
                new String[]{"ERROR"}, new String[]{}));
        c.add(new FinalTestSupport.Case(133, "Edge cases", "reception", "Appointments and follow-ups",
                "Child (P0008) booked into Internal Medicine",
                new String[]{"4", "1", "1", "P0008", "2", "Check-up", "2026-12-18", "11:00", "Y", "2026-12-18", "11:00", "N", "0"},
                new String[]{"ERROR", "Nothing was booked"}, new String[]{"Booked"}));
        // ---- Queue and consultation workflow: Create a visit and queue number ----
        c.add(new FinalTestSupport.Case(140, "Queue and consultation workflow", "reception", "Create a visit and queue number",
                "Regular visit for P0001 (FM)",
                new String[]{"2", "1", "P0001", "Cough", "1", "1", "1"},
                new String[]{"Visit created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(141, "Queue and consultation workflow", "reception", "Create a visit and queue number",
                "Second active visit for the same patient is refused",
                new String[]{"2", "1", "P0001", "Cough again", "1", "1", "1"},
                new String[]{"already has an active Family Medicine visit"}, new String[]{"Visit created"}));
        c.add(new FinalTestSupport.Case(142, "Queue and consultation workflow", "reception", "Create a visit and queue number",
                "Regular visit for P0002 (FM)",
                new String[]{"2", "1", "P0002", "Headache", "1", "1", "1"},
                new String[]{"Visit created"}, new String[]{}));
        c.add(new FinalTestSupport.Case(143, "Queue and consultation workflow", "reception", "Create a visit and queue number",
                "Priority visit for P0004 (FM)",
                new String[]{"2", "1", "P0004", "Dizziness", "1", "1", "2"},
                new String[]{"Priority lane"}, new String[]{}));
        // ---- Queue and consultation workflow: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(144, "Queue and consultation workflow", "reception", "Visit status board and tracking",
                "Board shows 3 waiting in Family Medicine",
                new String[]{"3", "0"},
                new String[]{"Family Medicine        3"}, new String[]{}));
        // ---- Queue and consultation workflow: Appointments and follow-ups ----
        c.add(new FinalTestSupport.Case(152, "Queue and consultation workflow", "reception", "Appointments and follow-ups",
                "Follow-up waiting to be booked is listed",
                new String[]{"4", "2", "0"},
                new String[]{"FOLLOW-UPS TO BOOK"}, new String[]{}));
        // ---- Queue and consultation workflow: Visit status board and tracking ----
        c.add(new FinalTestSupport.Case(153, "Queue and consultation workflow", "reception", "Visit status board and tracking",
                "Track all visits of P0004",
                new String[]{"3", "1", "2", "1", "P0004", "0"},
                new String[]{"P0004"}, new String[]{}));
        // ---- Extra checks (fresh copy of the data) ----
        c.add(new FinalTestSupport.Case(156, "Extra checks", "reception", "Search patients and visits",
                "Bad patient ID 'P99' is refused",
                new String[]{"5", "1", "P99", "P0001"},
                new String[]{"Patient ID must look like P0001"}, new String[]{}));
        c.add(new FinalTestSupport.Case(157, "Extra checks", "reception", "Search patients and visits",
                "Invalid sub-menu choice 9",
                new String[]{"5", "9"},
                new String[]{"Please enter a number from 0 to 5"}, new String[]{}));
        c.add(new FinalTestSupport.Case(158, "Extra checks", "reception", "View a patient record",
                "Bad patient ID 'abc' is refused",
                new String[]{"6", "1", "abc", "P0002"},
                new String[]{"Patient ID must look like P0001", "P0002"}, new String[]{}));
        c.add(new FinalTestSupport.Case(159, "Extra checks", "reception", "Visit status board and tracking",
                "Invalid sub-menu choice 7",
                new String[]{"3", "7", "0"},
                new String[]{"Please enter a number from 0 to"}, new String[]{}));
        return c;
    }
}
