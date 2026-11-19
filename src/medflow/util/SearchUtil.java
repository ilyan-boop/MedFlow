package medflow.util;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Hand-coded binary search and linear search (Section 5.4 of the proposal).
 *
 * Binary search needs a list that is already sorted by the same key (merge sort gives this):
 *   1. Set low to the first position and high to the last position.
 *   2. Look at the middle item.
 *   3. If it matches, the item is found. If the target is smaller, search the left half.
 *      If the target is larger, search the right half.
 *   4. Repeat until low is greater than high. Then the item is not in the list.
 *   5. If keys repeat (same name, same queue number), check the items next to the match
 *      to list every match.
 *
 * Time: O(log n) to find one match, O(log n + m) to list all m matches.
 * Linear search checks every item, O(n). It is used for partial matches anywhere in a name.
 */
public final class SearchUtil {
    private SearchUtil() { }

    /** Compares one item of the sorted list with the key being searched for. */
    public interface KeyCompare<T, K> {
        /** Negative if the item comes before the key, 0 if it matches, positive if it comes after. */
        int compare(T item, K key);
    }

    /** Position of one item that matches the key, or -1 if no item matches. */
    public static <T, K> int binarySearch(ArrayList<T> sorted, K key, KeyCompare<T, K> cmp) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = cmp.compare(sorted.get(mid), key);
            if (c == 0) return mid;
            if (c > 0) high = mid - 1;      // the middle item comes after the key: search the left half
            else low = mid + 1;             // the middle item comes before the key: search the right half
        }
        return -1;
    }

    /** Every item that matches the key, in list order. Binary search, then the items next to the match. */
    public static <T, K> ArrayList<T> allMatches(ArrayList<T> sorted, K key, KeyCompare<T, K> cmp) {
        ArrayList<T> result = new ArrayList<>();
        int at = binarySearch(sorted, key, cmp);
        if (at < 0) return result;
        int first = at;
        while (first > 0 && cmp.compare(sorted.get(first - 1), key) == 0) first--;
        int last = at;
        while (last < sorted.size() - 1 && cmp.compare(sorted.get(last + 1), key) == 0) last++;
        for (int i = first; i <= last; i++) result.add(sorted.get(i));
        return result;
    }

    /** Checks every item from the start and keeps the ones that pass the test. O(n). */
    public static <T> ArrayList<T> linearSearch(ArrayList<T> list, Predicate<T> test) {
        ArrayList<T> result = new ArrayList<>();
        for (T item : list) if (test.test(item)) result.add(item);
        return result;
    }

    /**
     * Compares the start of a text with a prefix, for prefix search on a sorted list.
     * "delacruzjuan" against "dela" gives 0, so every name that starts with the prefix matches.
     */
    public static int comparePrefix(String text, String prefix) {
        String start = text.length() > prefix.length() ? text.substring(0, prefix.length()) : text;
        int c = start.compareTo(prefix);
        if (c == 0 && start.length() < prefix.length()) return -1;   // the text is shorter than the prefix
        return c;
    }
}
