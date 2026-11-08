package medflow.exception;

/** Thrown when a user tries an action that their role is not allowed to do. */
public class AccessDeniedException extends Exception {
    public AccessDeniedException(String message) { super(message); }
}
