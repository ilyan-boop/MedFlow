package medflow.util;

import medflow.ds.BinaryHeap;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Hand-coded k-way merge using a min-heap (Section 5.4 of the proposal).
 *
 *   1. Put the first item of each sorted list (FM, IM, and PED) into the min-heap.
 *   2. Remove the smallest item from the heap and add it to the result.
 *   3. If the list that item came from has more items, put its next item into the heap.
 *   4. Repeat until the heap is empty.
 *
 * The heap never holds more than one item per list, so each step costs O(log k).
 * Time: O(N log k), where N is the number of items in all lists and k is the number of lists.
 * When two items are equal, the one from the earlier list comes first, so the merge is stable.
 */
public final class MergeUtil {
    private MergeUtil() { }

    /** One heap entry: an item, the list it came from, and its position in that list. */
    private static final class Entry<T> {
        final T item;
        final int list;
        final int position;
        Entry(T item, int list, int position) {
            this.item = item;
            this.list = list;
            this.position = position;
        }
    }

    /** Joins lists that are each already sorted by the given order into one sorted list. */
    public static <T> ArrayList<T> kWayMerge(ArrayList<ArrayList<T>> lists, Comparator<? super T> order) {
        BinaryHeap<Entry<T>> heap = new BinaryHeap<>((a, b) -> {
            int c = order.compare(a.item, b.item);
            return c != 0 ? c : Integer.compare(a.list, b.list);
        });
        int total = 0;
        for (int i = 0; i < lists.size(); i++) {
            ArrayList<T> list = lists.get(i);
            total += list.size();
            if (!list.isEmpty()) heap.insert(new Entry<>(list.get(0), i, 0));     // step 1
        }
        ArrayList<T> result = new ArrayList<>(total);
        while (!heap.isEmpty()) {
            Entry<T> smallest = heap.removeMin();                                    // step 2
            result.add(smallest.item);
            ArrayList<T> from = lists.get(smallest.list);
            int next = smallest.position + 1;
            if (next < from.size()) heap.insert(new Entry<>(from.get(next), smallest.list, next));   // step 3
        }
        return result;
    }
}
