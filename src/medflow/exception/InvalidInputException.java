package medflow.exception;

/** Thrown when a typed value fails a validation rule. The message is shown to the user. */
public class InvalidInputException extends Exception {
    public InvalidInputException(String message) { super(message); }
}
