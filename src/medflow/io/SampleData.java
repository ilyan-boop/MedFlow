package medflow.io;

import medflow.model.Patient;
import medflow.model.Role;
import medflow.model.Sex;
import medflow.model.User;
import medflow.service.AuthService;
import medflow.service.PatientService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fictional sample accounts and patients, used the first time the program runs. */
public final class SampleData {
    private SampleData() { }

    public static void load(AuthService auth, PatientService patients) {
        auth.loadUser(new User("reception", "rec123", "Liza Mercado", Role.RECEPTIONIST));
        auth.loadUser(new User("nurse", "nur123", "Paolo Santos", Role.NURSE));
        auth.loadUser(new User("doctor", "doc123", "Dr. Andrea Lim", Role.CLINICIAN));
        auth.loadUser(new User("records", "rec456", "Joy Villanueva", Role.RECORDS_STAFF));
        auth.loadUser(new User("admin", "adm123", "Carlo Reyes", Role.ADMINISTRATOR));

        LocalDateTime created = LocalDateTime.of(2026, 11, 3, 8, 30);
        add(patients, "P0001", "Dela Cruz", "Juan", "Santos", "1988-04-12", Sex.MALE,
                "12 Mabini St., Brgy. San Roque, Quezon City", "09171234567", "Penicillin", "Hypertension", created);
        add(patients, "P0002", "Reyes", "Maria Clara", "Lopez", "1995-09-30", Sex.FEMALE,
                "45 Rizal Ave., Brgy. Malate, Manila", "09281112233", "", "", created);
        add(patients, "P0003", "Garcia", "Miguel", "", "2016-02-05", Sex.MALE,
                "88 Taft Ave., Brgy. 720, Manila", "09175550101", "Peanuts", "Asthma", created);
        add(patients, "P0004", "Santos", "Lourdes", "Bautista", "1951-07-19", Sex.FEMALE,
                "3 Kalayaan St., Brgy. Pinyahan, Quezon City", "09189990000", "", "Type 2 diabetes", created);
        add(patients, "P0005", "Tan", "Kevin", "Uy", "2009-11-23", Sex.MALE,
                "21 Ongpin St., Binondo, Manila", "09451237788", "", "", created);
        add(patients, "P0006", "Villanueva", "Ana", "Cruz", "2001-01-01", Sex.FEMALE,
                "7 P. Noval St., Sampaloc, Manila", "09062223344", "Sulfa drugs", "", created);
        add(patients, "P0007", "Mendoza", "Jose", "Ramos", "1979-06-15", Sex.MALE,
                "102 Shaw Blvd., Mandaluyong City", "09998887766", "", "", created);
        add(patients, "P0008", "Aquino", "Sofia", "", "2024-03-08", Sex.FEMALE,
                "15 Ortigas Ave., Pasig City", "09173334455", "", "", created);
    }

    private static void add(PatientService ps, String id, String last, String first, String middle, String birth,
                            Sex sex, String address, String contact, String allergies, String history,
                            LocalDateTime created) {
        Patient p = new Patient(id, last, first, middle, LocalDate.parse(birth), sex, address, contact, "system", created);
        p.setAllergies(allergies);
        p.setRelevantHistory(history);
        ps.loadPatient(p);
    }
}
