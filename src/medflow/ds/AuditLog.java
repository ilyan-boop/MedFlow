package medflow.ds;

import java.util.ArrayList;

/**
 * Hand-coded singly linked list used as an append-only audit log.
 * Entries are only added at the end and are never removed or changed.
 *
 *   add      O(1)  (we keep a pointer to the tail)
 *   toList   O(n)
 */
public class AuditLog {

    private static class Node {
        final AuditEntry entry;
        Node next;
        Node(AuditEntry entry) { this.entry = entry; }
    }

    private Node head;
    private Node tail;
    private int size;

    /** Adds an entry at the end of the log in O(1). */
    public void add(AuditEntry entry) {
        Node node = new Node(entry);
        if (head == null) {
            head = node;
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** Returns all entries from oldest to newest. */
    public ArrayList<AuditEntry> toList() {
        ArrayList<AuditEntry> list = new ArrayList<>();
        Node current = head;
        while (current != null) {
            list.add(current.entry);
            current = current.next;
        }
        return list;
    }

    /** Returns the last n entries (newest last). */
    public ArrayList<AuditEntry> last(int n) {
        ArrayList<AuditEntry> all = toList();
        int from = Math.max(0, all.size() - n);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    // ------------------------------------------------------------------ filters (Milestone 5)
    // Each filter walks the list once from head to tail: O(n). Entries stay in time order.

    /** Entries made by one user (username, not case-sensitive). */
    public ArrayList<AuditEntry> byUser(String username) {
        ArrayList<AuditEntry> list = new ArrayList<>();
        for (Node n = head; n != null; n = n.next) {
            if (n.entry.getUsername().equalsIgnoreCase(username.trim())) list.add(n.entry);
        }
        return list;
    }

    /** Entries about one record: a patient ID, visit ID, or appointment ID. */
    public ArrayList<AuditEntry> byTarget(String recordId) {
        ArrayList<AuditEntry> list = new ArrayList<>();
        for (Node n = head; n != null; n = n.next) {
            if (n.entry.getTarget().equalsIgnoreCase(recordId.trim())) list.add(n.entry);
        }
        return list;
    }

    /** Entries whose action contains the given text, e.g. "APPOINTMENT" or "RECORD". */
    public ArrayList<AuditEntry> byAction(String text) {
        String t = text.trim().toUpperCase();
        ArrayList<AuditEntry> list = new ArrayList<>();
        for (Node n = head; n != null; n = n.next) {
            if (n.entry.getAction().contains(t)) list.add(n.entry);
        }
        return list;
    }
}
