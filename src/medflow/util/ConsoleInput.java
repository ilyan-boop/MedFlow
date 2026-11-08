package medflow.util;

import medflow.exception.InvalidInputException;

import java.util.NoSuchElementException;
import java.util.Scanner;

/** Reads input from the keyboard and asks again until the value passes its check. */
public class ConsoleInput {

    /** A check that turns typed text into a value, or throws InvalidInputException. */
    public interface Parser<T> {
        T parse(String text) throws InvalidInputException;
    }

    /** Thrown when the input stream ends (for example, at the end of a test script). */
    public static class InputClosedException extends RuntimeException { }

    private final Scanner scanner;

    // When the program is run with input from a file (e.g. for a test transcript), setting the
    // environment variable MEDFLOW_ECHO=1 prints each typed line so the transcript is readable.
    private final boolean echo = "1".equals(System.getenv("MEDFLOW_ECHO"));

    public ConsoleInput(Scanner scanner) { this.scanner = scanner; }

    public String readLine(String prompt) {
        System.out.print(prompt);
        try {
            String line = scanner.nextLine();
            if (echo) System.out.println(line);
            return line;
        } catch (NoSuchElementException e) {
            throw new InputClosedException();
        }
    }

    /** Asks until the input is valid. Each error message is shown before asking again. */
    public <T> T ask(String prompt, Parser<T> parser) {
        while (true) {
            String text = readLine(prompt);
            try {
                return parser.parse(text);
            } catch (InvalidInputException e) {
                System.out.println("   ERROR: " + e.getMessage());
            }
        }
    }

    public int askChoice(String prompt, int min, int max) {
        return ask(prompt, s -> Validator.validateMenuChoice(s, min, max));
    }

    public boolean askYesNo(String prompt) {
        return ask(prompt, Validator::validateYesNo);
    }
}
