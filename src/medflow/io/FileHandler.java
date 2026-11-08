package medflow.io;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.model.DuplicateFlag;
import medflow.model.Patient;
import medflow.model.Role;
import medflow.model.Sex;
import medflow.model.User;
import medflow.service.AuthService;
import medflow.service.PatientService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Loads data from .csv files when the program starts and saves it back when the program closes.
 * All data are fictional sample data.
 */
public class FileHandler {

    private final File folder;

    public FileHandler(String folderPath) {
        this.folder = new File(folderPath);
    }

    private File file(String name) { return new File(folder, name); }

    public boolean hasData() { return file("users.csv").exists(); }

    // ------------------------------------------------------------------ reading

    private ArrayList<ArrayList<String>> readRows(String name) throws IOException {
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        File f = file(name);
        if (!f.exists()) return rows;
        try (BufferedReader in = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String line = in.readLine();               // skip the header row
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) rows.add(Csv.split(line));
            }
        }
        return rows;
    }

    public void loadAll(AuthService auth, PatientService patients, AuditLog log) throws IOException {
        for (ArrayList<String> r : readRows("users.csv")) {
            auth.loadUser(new User(r.get(0), r.get(1), r.get(2), Role.valueOf(r.get(3))));
        }
        for (ArrayList<String> r : readRows("patients.csv")) {
            Patient p = new Patient(r.get(0), r.get(1), r.get(2), r.get(3), LocalDate.parse(r.get(4)),
                    Sex.valueOf(r.get(5)), r.get(6), r.get(7), r.get(10), LocalDateTime.parse(r.get(11)));
            p.setAllergies(r.get(8));
            p.setRelevantHistory(r.get(9));
            p.setMergedInto(r.get(12));
            if (!r.get(13).isEmpty()) {
                for (String v : r.get(13).split(";")) p.getVisitIds().add(v);
            }
            patients.loadPatient(p);
        }
        for (ArrayList<String> r : readRows("duplicate_flags.csv")) {
            DuplicateFlag f = new DuplicateFlag(r.get(0), r.get(1), r.get(2), LocalDateTime.parse(r.get(3)));
            DuplicateFlag.Status status = DuplicateFlag.Status.valueOf(r.get(4));
            if (status != DuplicateFlag.Status.PENDING) f.resolve(status, r.get(5));
            patients.loadFlag(f);
        }
        for (ArrayList<String> r : readRows("audit_log.csv")) {
            log.add(new AuditEntry(LocalDateTime.parse(r.get(0)), r.get(1), r.get(2), r.get(3), r.get(4)));
        }
    }

    // ------------------------------------------------------------------ writing

    private void writeRows(String name, String header, ArrayList<String> lines) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create folder " + folder);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file(name), StandardCharsets.UTF_8))) {
            out.write(header);
            out.newLine();
            for (String line : lines) {
                out.write(line);
                out.newLine();
            }
        }
    }

    public void saveAll(AuthService auth, PatientService patients, AuditLog log) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (User u : auth.getAllUsers()) {
            lines.add(Csv.join(u.getUsername(), u.getPassword(), u.getFullName(), u.getRole().name()));
        }
        writeRows("users.csv", "username,password,fullName,role", lines);

        lines = new ArrayList<>();
        ArrayList<Patient> all = patients.getAllPatients();
        all.sort((a, b) -> a.getPatientId().compareTo(b.getPatientId()));   // only to keep the file tidy
        for (Patient p : all) {
            lines.add(Csv.join(p.getPatientId(), p.getLastName(), p.getFirstName(), p.getMiddleName(),
                    p.getBirthdate().toString(), p.getSex().name(), p.getAddress(), p.getContactNumber(),
                    p.getAllergies(), p.getRelevantHistory(), p.getCreatedBy(), p.getCreatedAt().toString(),
                    p.getMergedInto(), String.join(";", p.getVisitIds())));
        }
        writeRows("patients.csv", "patientId,lastName,firstName,middleName,birthdate,sex,address,contactNumber,"
                + "allergies,relevantHistory,createdBy,createdAt,mergedInto,visitIds", lines);

        lines = new ArrayList<>();
        for (DuplicateFlag f : patients.getAllFlags()) {
            lines.add(Csv.join(f.getNewPatientId(), f.getExistingPatientId(), f.getFlaggedBy(),
                    f.getFlaggedAt().toString(), f.getStatus().name(), f.getResolvedBy()));
        }
        writeRows("duplicate_flags.csv", "newPatientId,existingPatientId,flaggedBy,flaggedAt,status,resolvedBy", lines);

        lines = new ArrayList<>();
        for (AuditEntry e : log.toList()) {
            lines.add(Csv.join(e.getTime().toString(), e.getUsername(), e.getAction(), e.getTarget(), e.getDetails()));
        }
        writeRows("audit_log.csv", "time,username,action,target,details", lines);
    }
}
