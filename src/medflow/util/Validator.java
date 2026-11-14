package medflow.util;

import medflow.exception.InvalidInputException;
import medflow.model.Service;
import medflow.model.Sex;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

/**
 * Checks every typed value before it is saved. All checks go through each character
 * one by one (no regular expressions), as described in Table 2 of the proposal.
 * Each method returns the cleaned value, or throws InvalidInputException with a clear message.
 *
 * Time cost: O(m) per field, where m is the length of the input.
 */
public final class Validator {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_AGE = 120;

    private Validator() { }

    /** Removes spaces at both ends and turns any run of spaces inside into one space. */
    public static String collapseSpaces(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean lastWasSpace = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace && sb.length() > 0) sb.append(' ');
                lastWasSpace = true;
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        int end = sb.length();
        if (end > 0 && sb.charAt(end - 1) == ' ') sb.setLength(end - 1);
        return sb.toString();
    }

    /**
     * A name may only have letters (including ñ and accented letters), spaces, periods, hyphens,
     * and apostrophes, e.g. "Ma. Clara", "Dela Cruz-Santos", "O'Brien". It must start with a letter.
     */
    public static String validateName(String input, String fieldName, boolean required)
            throws InvalidInputException {
        String name = collapseSpaces(input);
        if (name.isEmpty()) {
            if (required) throw new InvalidInputException(fieldName + " is required.");
            return "";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException(fieldName + " must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (!Character.isLetter(name.charAt(0))) {
            throw new InvalidInputException(fieldName + " must start with a letter.");
        }
        char previous = ' ';
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isDigit(c)) {
                throw new InvalidInputException(fieldName + " cannot contain numbers (found \"" + c + "\").");
            }
            boolean symbol = c == '.' || c == '-' || c == '\'';
            if (!Character.isLetter(c) && c != ' ' && !symbol) {
                throw new InvalidInputException(fieldName + " cannot contain the character \"" + c + "\".");
            }
            if (symbol && (previous == '.' || previous == '-' || previous == '\'')) {
                throw new InvalidInputException(fieldName + " cannot have two symbols in a row.");
            }
            previous = c;
        }
        return properCase(name);
    }

    /**
     * If a name was typed in all lowercase or all uppercase, e.g. "dela cruz" or "DELA CRUZ",
     * each word is capitalized ("Dela Cruz"). Names typed in mixed case are kept as typed (e.g. "McArthur").
     */
    public static String properCase(String name) {
        boolean hasLower = false, hasUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            if (Character.isUpperCase(c)) hasUpper = true;
        }
        if (hasLower && hasUpper) return name;
        StringBuilder sb = new StringBuilder();
        boolean startOfWord = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            } else {
                sb.append(c);
                startOfWord = (c == ' ' || c == '-' || c == '.');
            }
        }
        return sb.toString();
    }

    /** Birthdate must be typed as YYYY-MM-DD, be a real date, not be in the future, and give an age of at most 120. */
    public static LocalDate validateBirthdate(String input, LocalDate today) throws InvalidInputException {
        String s = input == null ? "" : input.trim();
        String formatMessage = "Birthdate must follow the format YYYY-MM-DD, e.g. 2005-03-14.";
        if (s.length() != 10) throw new InvalidInputException(formatMessage);
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            if (i == 4 || i == 7) {
                if (c != '-') throw new InvalidInputException(formatMessage);
            } else if (c < '0' || c > '9') {
                throw new InvalidInputException(formatMessage);
            }
        }
        int year = toNumber(s, 0, 4);
        int month = toNumber(s, 5, 7);
        int day = toNumber(s, 8, 10);
        if (month < 1 || month > 12) throw new InvalidInputException("Month must be from 01 to 12.");
        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new InvalidInputException(s + " is not a real date.");
        }
        if (date.isAfter(today)) throw new InvalidInputException("Birthdate cannot be in the future.");
        if (Period.between(date, today).getYears() > MAX_AGE) {
            throw new InvalidInputException("Birthdate gives an age above " + MAX_AGE + ". Please check the year.");
        }
        return date;
    }

    /** Converts the digits from index start (inclusive) to end (exclusive) into a number. */
    private static int toNumber(String s, int start, int end) {
        int value = 0;
        for (int i = start; i < end; i++) value = value * 10 + (s.charAt(i) - '0');
        return value;
    }

    /** Accepts M, F, Male, or Female (any capitalization). */
    public static Sex validateSex(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim().toUpperCase();
        if (s.equals("M") || s.equals("MALE")) return Sex.MALE;
        if (s.equals("F") || s.equals("FEMALE")) return Sex.FEMALE;
        throw new InvalidInputException("Sex must be M or F.");
    }

    /**
     * Philippine mobile number: 11 digits starting with 09, e.g. 09171234567.
     * Spaces and dashes are allowed while typing and are removed, e.g. 0917-123-4567.
     */
    public static String validateContactNumber(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else if (c != ' ' && c != '-') {
                throw new InvalidInputException("Contact number can only contain digits (found \"" + c + "\").");
            }
        }
        if (digits.length() != 11) {
            throw new InvalidInputException("Contact number must have 11 digits, e.g. 09171234567.");
        }
        if (digits.charAt(0) != '0' || digits.charAt(1) != '9') {
            throw new InvalidInputException("Contact number must start with 09.");
        }
        return digits.toString();
    }

    /** Address: 5 to 100 characters of letters, digits, spaces, and , . - # / ' */
    public static String validateAddress(String input) throws InvalidInputException {
        String s = collapseSpaces(input);
        if (s.isEmpty()) throw new InvalidInputException("Address is required.");
        if (s.length() < 5) throw new InvalidInputException("Address is too short.");
        if (s.length() > 100) throw new InvalidInputException("Address must be at most 100 characters.");
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            boolean ok = Character.isLetterOrDigit(c) || c == ' ' || c == ',' || c == '.'
                    || c == '-' || c == '#' || c == '/' || c == '\'';
            if (!ok) throw new InvalidInputException("Address cannot contain the character \"" + c + "\".");
        }
        if (!hasLetter) throw new InvalidInputException("Address must contain a street, barangay, or city name.");
        return s;
    }

    /** A whole-number menu choice from min to max. */
    public static int validateMenuChoice(String input, int min, int max) throws InvalidInputException {
        String s = input == null ? "" : input.trim();
        String message = "Please enter a number from " + min + " to " + max + ".";
        if (s.isEmpty() || s.length() > 3) throw new InvalidInputException(message);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new InvalidInputException(message);
        }
        int value = toNumber(s, 0, s.length());
        if (value < min || value > max) throw new InvalidInputException(message);
        return value;
    }

    /** Y or N answer. */
    public static boolean validateYesNo(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim().toUpperCase();
        if (s.equals("Y") || s.equals("YES")) return true;
        if (s.equals("N") || s.equals("NO")) return false;
        throw new InvalidInputException("Please answer Y or N.");
    }

    /** Patient ID: P followed by 4 to 6 digits, e.g. P0007. Lowercase p is accepted. */
    public static String validatePatientId(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim().toUpperCase();
        if (s.length() < 5 || s.length() > 7 || s.charAt(0) != 'P') {
            throw new InvalidInputException("Patient ID must look like P0001.");
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new InvalidInputException("Patient ID must look like P0001.");
        }
        return s;
    }

    /** Reason for visit: 3 to 150 characters of letters, digits, spaces, and , . - / ' ( ) : ; */
    public static String validateReason(String input) throws InvalidInputException {
        String s = collapseSpaces(input);
        if (s.isEmpty()) throw new InvalidInputException("Reason for visit is required.");
        if (s.length() < 3) throw new InvalidInputException("Reason for visit is too short.");
        if (s.length() > 150) throw new InvalidInputException("Reason for visit must be at most 150 characters.");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == ' ' || c == ',' || c == '.' || c == '-'
                    || c == '/' || c == '\'' || c == '(' || c == ')' || c == ':' || c == ';';
            if (!ok) throw new InvalidInputException("Reason for visit cannot contain the character \"" + c + "\".");
        }
        return s;
    }

    /** Visit ID: V followed by 4 to 6 digits, e.g. V0003. Lowercase v is accepted. */
    public static String validateVisitId(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim().toUpperCase();
        if (s.length() < 5 || s.length() > 7 || s.charAt(0) != 'V') {
            throw new InvalidInputException("Visit ID must look like V0001.");
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') throw new InvalidInputException("Visit ID must look like V0001.");
        }
        return s;
    }

    /** Outcome note written by the clinician: 3 to 200 characters, same allowed characters as the reason. */
    public static String validateOutcomeNote(String input) throws InvalidInputException {
        String s = collapseSpaces(input);
        if (s.isEmpty()) throw new InvalidInputException("The outcome note is required.");
        if (s.length() < 3) throw new InvalidInputException("The outcome note is too short.");
        if (s.length() > 200) throw new InvalidInputException("The outcome note must be at most 200 characters.");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == ' ' || c == ',' || c == '.' || c == '-'
                    || c == '/' || c == '\'' || c == '(' || c == ')' || c == ':' || c == ';';
            if (!ok) throw new InvalidInputException("The outcome note cannot contain the character \"" + c + "\".");
        }
        return s;
    }

    /** Username: 4 to 20 letters, digits, dots, or underscores. */
    public static String validateUsername(String input) throws InvalidInputException {
        String s = input == null ? "" : input.trim();
        if (s.length() < 4 || s.length() > 20) {
            throw new InvalidInputException("Username must be 4 to 20 characters.");
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '_') {
                throw new InvalidInputException("Username can only have letters, digits, dots, and underscores.");
            }
        }
        return s;
    }

    /** Password: at least 6 characters with at least one letter and one digit, and no commas or spaces. */
    public static String validatePassword(String input) throws InvalidInputException {
        String s = input == null ? "" : input;
        if (s.length() < 6) throw new InvalidInputException("Password must be at least 6 characters.");
        boolean letter = false, digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == ',') throw new InvalidInputException("Password cannot contain spaces or commas.");
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) throw new InvalidInputException("Password must have at least one letter and one digit.");
        return s;
    }

    /** Age rule of each service: Pediatrics below 18, Internal Medicine 18 and above, Family Medicine all ages. */
    public static void checkServiceEligibility(int age, Service service) throws InvalidInputException {
        if (!service.acceptsAge(age)) {
            throw new InvalidInputException("Not eligible for " + service.getLabel() + ". This service is for "
                    + service.getAgeRule() + ", and the patient is " + age + ".");
        }
    }

    /** Lists the services a patient of this age can use. */
    public static String eligibleServices(int age) {
        StringBuilder sb = new StringBuilder();
        for (Service s : Service.values()) {
            if (s.acceptsAge(age)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getLabel());
            }
        }
        return sb.toString();
    }
}
