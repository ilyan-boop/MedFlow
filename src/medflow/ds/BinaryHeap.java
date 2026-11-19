package medflow.ds;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Hand-coded binary min-heap (Section 5.6 of the proposal). The smallest item, by the given
 * order, is always at the top (index 0). The heap is kept in an ArrayList:
 * the children of position i are at 2i + 1 and 2i + 2, and its parent is at (i - 1) / 2.
 *
 *   insert      O(log n)   add at the end, then sift up
 *   removeMin   O(log n)   move the last item to the top, then sift down
 *   peek        O(1)
 *
 * MergeUtil uses it for the k-way merge of the daily report.
 */
public class BinaryHeap<T> {

    private final ArrayList<T> items = new ArrayList<>();
    private final Comparator<? super T> order;

    public BinaryHeap(Comparator<? super T> order) { this.order = order; }

    /** Adds an item and moves it up until its parent is not larger. O(log n). */
    public void insert(T item) {
        items.add(item);
        siftUp(items.size() - 1);
    }

    /** Removes and returns the smallest item, or null if the heap is empty. O(log n). */
    public T removeMin() {
        if (items.isEmpty()) return null;
        T min = items.get(0);
        T last = items.remove(items.size() - 1);
        if (!items.isEmpty()) {
            items.set(0, last);
            siftDown(0);
        }
        return min;
    }

    /** The smallest item without removing it, or null if the heap is empty. O(1). */
    public T peek() { return items.isEmpty() ? null : items.get(0); }

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** Swaps the item with its parent while the item is smaller than the parent. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (order.compare(items.get(i), items.get(parent)) >= 0) return;
            swap(i, parent);
            i = parent;
        }
    }

    /** Swaps the item with its smaller child while that child is smaller than the item. */
    private void siftDown(int i) {
        int n = items.size();
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int smallest = i;
            if (left < n && order.compare(items.get(left), items.get(smallest)) < 0) smallest = left;
            if (right < n && order.compare(items.get(right), items.get(smallest)) < 0) smallest = right;
            if (smallest == i) return;
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        T t = items.get(a);
        items.set(a, items.get(b));
        items.set(b, t);
    }
}
