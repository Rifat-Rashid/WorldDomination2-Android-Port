package com.example.worlddomination.game.rendering;

/** One Flash parent clip's integer-depth occupancy, in ascending render order. */
public final class SourceDisplayList<T> {
    public static final class Entry<T> {
        private final SourceDisplayList<T> parent;
        private final T value;
        private int depth;
        private boolean attached = true;

        private Entry(SourceDisplayList<T> parent, T value, int depth) {
            this.parent = parent;
            this.value = value;
            this.depth = depth;
        }

        public T getValue() { return value; }
        public int getDepth() { return depth; }
        public boolean isAttached() { return attached; }
    }

    private Entry<T>[] ordered;
    private int size;

    public SourceDisplayList() { this(16); }

    @SuppressWarnings("unchecked")
    public SourceDisplayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("Negative capacity");
        ordered = (Entry<T>[]) new Entry[initialCapacity];
    }

    /** A valid source attach replaces, rather than swaps with, the depth occupant. */
    public Entry<T> attach(T value, int depth) {
        Entry<T> entry = new Entry<>(this, value, depth);
        int index = find(depth);
        if (index >= 0) {
            ordered[index].attached = false;
            ordered[index] = entry;
        } else {
            insert(-index - 1, entry);
        }
        return entry;
    }

    /** A replaced or detached handle cannot remove its successor. */
    public void remove(Entry<T> entry) {
        if (!ownsAttached(entry)) return;
        int index = find(entry.depth);
        if (index < 0 || ordered[index] != entry) return;
        entry.attached = false;
        removeAt(index);
    }

    /** Occupied targets exchange identities; empty targets move the caller. */
    public void swapDepths(Entry<T> entry, int targetDepth) {
        if (!ownsAttached(entry) || entry.depth == targetDepth) return;
        int oldIndex = find(entry.depth);
        if (oldIndex < 0 || ordered[oldIndex] != entry) return;
        int targetIndex = find(targetDepth);
        int oldDepth = entry.depth;
        if (targetIndex >= 0) {
            Entry<T> other = ordered[targetIndex];
            other.depth = oldDepth;
            entry.depth = targetDepth;
            ordered[oldIndex] = other;
            ordered[targetIndex] = entry;
        } else {
            removeAt(oldIndex);
            entry.depth = targetDepth;
            insert(-find(targetDepth) - 1, entry);
        }
    }

    /** A clip-target swap is valid only when both handles belong to this parent. */
    public void swapDepths(Entry<T> entry, Entry<T> target) {
        if (!ownsAttached(entry) || !ownsAttached(target)) return;
        swapDepths(entry, target.depth);
    }

    public int nextHighestDepth() {
        if (size == 0) return 0;
        if (ordered[size - 1].depth == Integer.MAX_VALUE)
            throw new IllegalStateException("No greater integer depth");
        return ordered[size - 1].depth + 1;
    }

    public int size() { return size; }

    /** Allocation-free, sorted access for the renderer. */
    public Entry<T> entryAt(int sortedIndex) {
        if (sortedIndex < 0 || sortedIndex >= size)
            throw new IndexOutOfBoundsException("Index " + sortedIndex);
        return ordered[sortedIndex];
    }

    public Entry<T> atDepth(int depth) {
        int index = find(depth);
        return index >= 0 ? ordered[index] : null;
    }

    public void clear() {
        for (int index = 0; index < size; index++) {
            ordered[index].attached = false;
            ordered[index] = null;
        }
        size = 0;
    }

    private boolean ownsAttached(Entry<T> entry) {
        return entry != null && entry.parent == this && entry.attached;
    }

    /** Binary search; returns insertion point as -(index+1) when absent. */
    private int find(int depth) {
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int found = ordered[middle].depth;
            if (found < depth) low = middle + 1;
            else if (found > depth) high = middle - 1;
            else return middle;
        }
        return -(low + 1);
    }

    private void insert(int index, Entry<T> entry) {
        if (size == ordered.length) grow();
        System.arraycopy(ordered, index, ordered, index + 1, size - index);
        ordered[index] = entry;
        size++;
    }

    private void removeAt(int index) {
        System.arraycopy(ordered, index + 1, ordered, index, size - index - 1);
        ordered[--size] = null;
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        int capacity = Math.max(1, ordered.length + Math.max(1, ordered.length >>> 1));
        Entry<T>[] larger = (Entry<T>[]) new Entry[capacity];
        System.arraycopy(ordered, 0, larger, 0, size);
        ordered = larger;
    }
}
