package de.karopapier.kabotte.utils;

import java.util.*;
import java.util.function.Function;

public class IntBucketPriorityQueue<E> implements Queue<E> {

    private static final int EMPTY_QUEUE = -1;
    private final Function<E, Integer> priority;
    private final ArrayDeque<E>[] buckets;
    private int indexFirst = EMPTY_QUEUE;

    @SuppressWarnings("unchecked")
    private IntBucketPriorityQueue(Function<E, Integer> priority, int bucketCount) {
        buckets = new ArrayDeque[bucketCount];
        this.priority = priority;
    }

    public static <E> IntBucketPriorityQueue<E> empty(Function<E, Integer> priority, int bucketCount) {
        return new IntBucketPriorityQueue<>(priority, bucketCount);
    }

    public static <E> IntBucketPriorityQueue<E> of(Collection<E> source, Function<E, Integer> priority, int bucketCount) {
        IntBucketPriorityQueue<E> queue = new IntBucketPriorityQueue<>(priority, bucketCount);
        queue.addAll(source);
        return queue;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E elem : c)
            changed |= offer(elem);
        return changed;
    }

    @Override
    public void clear() {
        Arrays.fill(buckets, null);
        for (int i = indexFirst; i < buckets.length; i++)
            if (buckets[i] != null)
                buckets[i] = null;
        indexFirst = EMPTY_QUEUE;
    }

    @Override
    public boolean contains(Object o) {
        if (o == null)
            return false;

        for (Queue<E> queue : buckets)
            if (queue != null)
                if (queue.contains(o))
                    return true;
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c)
            if (!contains(o))
                return false;
        return true;
    }

    @Override
    public boolean isEmpty() {
        return indexFirst == EMPTY_QUEUE;
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        if (o == null)
            return false;

        for (Queue<E> queue : buckets)
            if (queue != null)
                if (queue.remove(o))
                    return true;
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object o : c)
            changed |= remove(o);
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        if (isEmpty())
            return 0;
        else {
            int size = 0;
            for (ArrayDeque<E> subQueue : buckets)
                if (subQueue != null)
                    size += subQueue.size();
            return size;
        }
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(E e) {
        return offer(e);
    }

    @Override
    public E element() {
        E elem = peek();
        if (elem == null)
            throw new NoSuchElementException();
        return peek();
    }

    @Override
    public boolean offer(E e) {
        int index = priority.apply(e);
        ArrayDeque<E> bucket = buckets[index];
        if (bucket == null) {
            bucket = new ArrayDeque<>();
            buckets[index] = bucket;
        }
        if (isEmpty() || index < indexFirst)
            indexFirst = index;
        return bucket.offer(e);
    }

    @Override
    public E peek() {
        if (isEmpty())
            return null;
        ArrayDeque<E> bucket = buckets[indexFirst];
        if (bucket != null)
            return bucket.peek();
        else
            return null;
    }

    @Override
    public E poll() {
        if (isEmpty())
            return null;

        ArrayDeque<E> bucket = buckets[indexFirst];
        E elem = bucket.poll();
        if (bucket.isEmpty()) {
            buckets[indexFirst] = null;
            calculateFirst();
        }
        return elem;
    }

    @Override
    public E remove() {
        E elem = poll();
        if (elem == null)
            throw new NoSuchElementException();
        return elem;
    }

    private void calculateFirst() {
        for (int i = 0; i < buckets.length; i++)
            if (buckets[i] != null) {
                indexFirst = i;
                return;
            }
        indexFirst = EMPTY_QUEUE;
    }

    @Override
    public String toString() {
        return "size: " + size() + " [" + buckets[indexFirst].toString() + "]";
    }

}
