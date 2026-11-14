package medflow.ds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Hand-coded stack (singly linked list where the head is the top).
 * Used for the version history of each patient record: before every change, the old version
 * is pushed, so the newest change is always on top and older versions stay below it.
 *
 *   push     O(1)
 *   pop      O(1)
 *   peek     O(1)
 *   iterate  O(n)  newest (top) to oldest (bottom), without removing anything
 */
public class VersionStack<T> implements Iterable<T> {

    private static class Node<T> {
        final T item;
        final Node<T> below;
        Node(T item, Node<T> below) {
            this.item = item;
            this.below = below;
        }
    }

    private Node<T> top;
    private int size;

    /** Puts an item on top of the stack. */
    public void push(T item) {
        top = new Node<>(item, top);
        size++;
    }

    /** Removes and returns the top item, or null if the stack is empty. */
    public T pop() {
        if (top == null) return null;
        T item = top.item;
        top = top.below;
        size--;
        return item;
    }

    /** Returns the top item without removing it, or null if the stack is empty. */
    public T peek() { return top == null ? null : top.item; }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** All items from the top (newest) to the bottom (oldest). */
    public ArrayList<T> toListNewestFirst() {
        ArrayList<T> list = new ArrayList<>();
        for (T item : this) list.add(item);
        return list;
    }

    /** Goes from the top (newest) to the bottom (oldest) without changing the stack. */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> current = top;

            @Override
            public boolean hasNext() { return current != null; }

            @Override
            public T next() {
                if (current == null) throw new NoSuchElementException();
                T item = current.item;
                current = current.below;
                return item;
            }
        };
    }
}
