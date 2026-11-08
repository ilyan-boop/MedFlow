package medflow.service;

import medflow.ds.AuditEntry;
import medflow.ds.AuditLog;
import medflow.exception.AccessDeniedException;
import medflow.exception.InvalidInputException;
import medflow.model.Permission;
import medflow.model.Role;
import medflow.model.User;
import medflow.util.Validator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

/**
 * Handles login and role-based permissions.
 *
 *   users            HashMap<username, User>          O(1) average lookup at login
 *   rolePermissions  HashMap<Role, Set<Permission>>   O(1) average permission check
 */
public class AuthService {

    private final HashMap<String, User> users = new HashMap<>();
    private final HashMap<Role, Set<Permission>> rolePermissions = new HashMap<>();
    private final AuditLog auditLog;

    public AuthService(AuditLog auditLog) {
        this.auditLog = auditLog;
        loadDefaultPermissions();
    }

    /** Permissions of each role, based on the Users table in Section 4.1 of the proposal. */
    private void loadDefaultPermissions() {
        rolePermissions.put(Role.RECEPTIONIST, EnumSet.of(
                Permission.REGISTER_PATIENT, Permission.SEARCH_PATIENT, Permission.VIEW_PATIENT_RECORD,
                Permission.CREATE_VISIT, Permission.MANAGE_APPOINTMENTS));

        rolePermissions.put(Role.NURSE, EnumSet.of(
                Permission.SEARCH_PATIENT, Permission.VIEW_PATIENT_RECORD, Permission.VIEW_CLINICAL_NOTES,
                Permission.MANAGE_QUEUE));

        rolePermissions.put(Role.CLINICIAN, EnumSet.of(
                Permission.SEARCH_PATIENT, Permission.VIEW_PATIENT_RECORD, Permission.VIEW_CLINICAL_NOTES,
                Permission.EDIT_CLINICAL_NOTES, Permission.MANAGE_QUEUE, Permission.RECORD_CONSULTATION));

        rolePermissions.put(Role.RECORDS_STAFF, EnumSet.of(
                Permission.SEARCH_PATIENT, Permission.VIEW_PATIENT_RECORD, Permission.RESOLVE_DUPLICATES,
                Permission.CORRECT_RECORDS, Permission.VIEW_VERSION_HISTORY));

        rolePermissions.put(Role.ADMINISTRATOR, EnumSet.of(
                Permission.SEARCH_PATIENT, Permission.VIEW_PATIENT_RECORD, Permission.MANAGE_USERS,
                Permission.VIEW_AUDIT_LOG, Permission.VIEW_REPORTS));
    }

    /** Adds an account while loading data from file (no permission check). */
    public void loadUser(User user) { users.put(user.getUsername().toLowerCase(), user); }

    /**
     * Checks a username and password. Returns the user if correct, or null if not.
     * Both successful and failed logins are written to the audit log.
     */
    public User login(String username, String password) {
        User user = users.get(username.trim().toLowerCase());
        if (user != null && user.checkPassword(password)) {
            auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "LOGIN", "-",
                    "Logged in as " + user.getRole().getLabel()));
            return user;
        }
        auditLog.add(new AuditEntry(LocalDateTime.now(), username.trim(), "LOGIN_FAILED", "-",
                "Wrong username or password"));
        return null;
    }

    public void logout(User user) {
        auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "LOGOUT", "-", ""));
    }

    /** True if the role of the user has the permission. */
    public boolean can(User user, Permission permission) {
        Set<Permission> allowed = rolePermissions.get(user.getRole());
        return allowed != null && allowed.contains(permission);
    }

    /** Throws AccessDeniedException if the role of the user does not have the permission. */
    public void require(User user, Permission permission) throws AccessDeniedException {
        if (!can(user, permission)) {
            auditLog.add(new AuditEntry(LocalDateTime.now(), user.getUsername(), "ACCESS_DENIED", "-",
                    "Tried " + permission));
            throw new AccessDeniedException("Access denied. A " + user.getRole().getLabel()
                    + " cannot do this action.");
        }
    }

    /** Creates a new staff account. Only the Administrator can do this. */
    public User addUser(User admin, String username, String password, String fullName, Role role)
            throws AccessDeniedException, InvalidInputException {
        require(admin, Permission.MANAGE_USERS);
        String cleanUsername = Validator.validateUsername(username);
        if (users.containsKey(cleanUsername.toLowerCase())) {
            throw new InvalidInputException("The username \"" + cleanUsername + "\" is already taken.");
        }
        String cleanPassword = Validator.validatePassword(password);
        String cleanName = Validator.validateName(fullName, "Full name", true);
        User user = new User(cleanUsername, cleanPassword, cleanName, role);
        users.put(cleanUsername.toLowerCase(), user);
        auditLog.add(new AuditEntry(LocalDateTime.now(), admin.getUsername(), "ADD_USER", cleanUsername,
                "Role: " + role.getLabel()));
        return user;
    }

    public ArrayList<User> getAllUsers() { return new ArrayList<>(users.values()); }

    public Set<Permission> getPermissions(Role role) { return rolePermissions.get(role); }
}
