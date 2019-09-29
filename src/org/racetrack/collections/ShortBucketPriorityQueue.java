package org.racetrack.collections;

import java.util.*;
import java.util.function.*;

public class ShortBucketPriorityQueue<E> implements Queue<E> {

  private static final int EMPTY_QUEUE = -1;

  public static <E> ShortBucketPriorityQueue<E> empty(Function<E, Short> priority) {
    return new ShortBucketPriorityQueue<E>(priority);
  };

  public static <E> ShortBucketPriorityQueue<E> of(Collection<E> source, Function<E, Short> priority) {
    ShortBucketPriorityQueue<E> queue = new ShortBucketPriorityQueue<>(priority);
    queue.addAll(source);
    return queue;
  };

  private Function<E, Short> priority = elem -> (short) elem.hashCode();
  private int indexFirst = EMPTY_QUEUE;

  private ArrayDeque<E>[] buckets;

  @SuppressWarnings("unchecked")
  private ShortBucketPriorityQueue(Function<E, Short> priority) {
    buckets = new ArrayDeque[Short.MAX_VALUE];
    this.priority = priority;
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E elem : c) {
      changed |= offer(elem);
    }
    return changed;
  }

  @Override
  public void clear() {
    for (int i = indexFirst; i < buckets.length; i++) {
      if (buckets[i] != null) {
        buckets[i] = null;
      }
    }
    indexFirst = EMPTY_QUEUE;
  }

  @Override
  public boolean contains(Object o) {
    if (o == null)
      return false;

    for (Queue<E> queue : buckets) {
      if (queue != null)
        if (queue.contains(o))
          return true;
    }
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    for (Object o : c) {
      if (!contains(o))
        return false;
    }
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

    for (Queue<E> queue : buckets) {
      if (queue != null)
        if (queue.remove(o))
          return true;
    }
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean changed = false;
    for (Object o : c) {
      changed |= remove(o);
    }
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
      for (ArrayDeque<E> subQueue : buckets) {
        if (subQueue != null) {
          size += subQueue.size();
        }
      }
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
      bucket = new ArrayDeque<E>();
      buckets[index] = bucket;
    }
    if (isEmpty() || index < indexFirst) {
      indexFirst = index;
    }
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
    for (int i = 0; i < buckets.length; i++) {
      if (buckets[i] != null) {
        indexFirst = i;
        return;
      }
    }
    indexFirst = EMPTY_QUEUE;
  }

  @Override
  public String toString() {
    return size() + " [" + buckets[indexFirst].toString() + "]";
  }

}
