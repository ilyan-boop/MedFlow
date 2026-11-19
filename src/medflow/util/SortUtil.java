package medflow.util;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Hand-coded merge sort (Section 5.4 of the proposal). The program does not call
 * Collections.sort, Arrays.sort, or List.sort.
 *
 *   1. A list with 0 or 1 item is already sorted.
 *   2. Split the list into a left half and a right half.
 *   3. Sort each half in the same way.
 *   4. Merge the halves: copy the smaller front item each time. On a tie, the left item is
 *      taken first, so items with equal keys keep their original order (stable sort).
 *   5. Copy the items left over from either half.
 *
 * Time: O(n log n) in all cases. Extra space: O(n) for the temporary list.
 */
public final class SortUtil {
    private SortUtil() { }

    /** Sorts the list in place, smallest first, using the given order. */
    public static <T> void mergeSort(ArrayList<T> list, Comparator<? super T> order) {
        if (list == null || list.size() < 2) return;
        ArrayList<T> temp = new ArrayList<>(list);
        sort(list, temp, 0, list.size() - 1, order);
    }

    /** Returns a sorted copy and leaves the original list as it is. */
    public static <T> ArrayList<T> sortedCopy(ArrayList<T> list, Comparator<? super T> order) {
        ArrayList<T> copy = new ArrayList<>(list);
        mergeSort(copy, order);
        return copy;
    }

    private static <T> void sort(ArrayList<T> list, ArrayList<T> temp, int low, int high, Comparator<? super T> order) {
        if (low >= high) return;                          // 0 or 1 item
        int mid = (low + high) / 2;
        sort(list, temp, low, mid, order);                // left half
        sort(list, temp, mid + 1, high, order);           // right half
        merge(list, temp, low, mid, high, order);
    }

    private static <T> void merge(ArrayList<T> list, ArrayList<T> temp, int low, int mid, int high,
                                  Comparator<? super T> order) {
        for (int i = low; i <= high; i++) temp.set(i, list.get(i));
        int left = low;
        int right = mid + 1;
        int out = low;
        while (left <= mid && right <= high) {
            if (order.compare(temp.get(left), temp.get(right)) <= 0) {   // <= keeps equal items in order
                list.set(out++, temp.get(left++));
            } else {
                list.set(out++, temp.get(right++));
            }
        }
        while (left <= mid) list.set(out++, temp.get(left++));
        while (right <= high) list.set(out++, temp.get(right++));
    }
}
