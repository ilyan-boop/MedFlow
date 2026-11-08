package medflow.model;

/** A staff account. Passwords are plain sample data only (see Delimitations). */
public class User {
    private final String username;
    private String password;
    private String fullName;
    private Role role;

    public User(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    public boolean checkPassword(String attempt) { return password.equals(attempt); }

    @Override
    public String toString() { return fullName + " (" + role.getLabel() + ")"; }
}
