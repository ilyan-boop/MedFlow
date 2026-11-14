package medflow.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Hand-coded FIFO queue (singly linked list with head and tail pointers).
 * Used for the regular line and the priority lane of each service.
 *
 *   enqueue  O(1)  add at the back (tail)
 *   dequeue  O(1)  remove from the front (head)
 *   peek     O(1)
 *   remove   O(n)  take one item out from anywhere (used when a waiting visit is cancelled)
 *   iterate  O(n)  front to back, without removing anything (used to show the line)
 */
public class LinkedQueue<T> implements Iterable<T> {

    private static class Node<T> {
        final T item;
        Node<T> next;
        Node(T item) { this.item = item; }
    }

    private Node<T> head;   // front: the next one to be served
    private Node<T> tail;   // back: where new items join
    private int size;

    /** Adds an item at the back of the line. */
    public void enqueue(T item) {
        Node<T> node = new Node<>(item);
        if (tail == null) {
            head = node;
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    /** Removes and returns the front item, or null if the queue is empty. */
    public T dequeue() {
        if (head == null) return null;
        T item = head.item;
        head = head.next;
        if (head == null) tail = null;
        size--;
        return item;
    }

    /** Returns the front item without removing it, or null if the queue is empty. */
    public T peek() { return head == null ? null : head.item; }

    /** Removes the first node that holds this exact item. Returns true if it was found. */
    public boolean remove(T item) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            if (current.item == item) {
                if (previous == null) head = current.next;
                else previous.next = current.next;
                if (current == tail) tail = previous;
                size--;
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** Goes through the queue from front to back without changing it. */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> current = head;

            @Override
            public boolean hasNext() { return current != null; }

            @Override
            public T next() {
                if (current == null) throw new NoSuchElementException();
                T item = current.item;
                current = current.next;
                return item;
            }
        };
    }
}
