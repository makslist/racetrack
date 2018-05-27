package org.racetrack.collections;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class TreeBucketPriorityQueue<T, K extends Comparable<?>> implements Queue<T> {

  public static <T, K extends Comparable<? super K>> TreeBucketPriorityQueue<T, K> empty(
      Function<T, Comparable<? super K>> priority) {
    return new TreeBucketPriorityQueue<T, K>(priority);
  };

  public static <T, K extends Comparable<? super K>> TreeBucketPriorityQueue<T, K> of(Collection<T> source,
      Function<T, Comparable<? super K>> priority) {
    TreeBucketPriorityQueue<T, K> queue = new TreeBucketPriorityQueue<T, K>(priority);
    queue.addAll(source);
    return queue;
  };

  private SortedMap<Comparable<? super K>, ArrayDeque<T>> priorityQueues = new TreeMap<Comparable<? super K>, ArrayDeque<T>>();
  private Function<T, Comparable<? super K>> priority;
  private ArrayDeque<T> firstQueue;

  private TreeBucketPriorityQueue(Function<T, Comparable<? super K>> priority) {
    this.priority = priority;
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    boolean changed = false;
    for (T elem : c) {
      changed |= offer(elem);
    }
    return changed;
  }

  @Override
  public void clear() {
    priorityQueues.clear();
    firstQueue = null;
  }

  @Override
  public boolean contains(Object o) {
    if (o == null)
      return false;

    for (Queue<T> queue : priorityQueues.values())
      if (queue.contains(o))
        return true;

    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    if (c == null || c.isEmpty())
      return false;

    for (Object o : c)
      if (!contains(o))
        return false;

    return true;
  }

  @Override
  public boolean isEmpty() {
    return priorityQueues.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    if (o == null)
      return false;

    final Iterator<ArrayDeque<T>> iterator = priorityQueues.values().iterator();
    while (iterator.hasNext()) {
      ArrayDeque<T> queue = iterator.next();
      if (queue.remove(o)) {
        if (queue.isEmpty()) {
          iterator.remove();
          if (queue == firstQueue) {
            firstQueue = priorityQueues.get(priorityQueues.firstKey());
          }
        }
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    if (c == null || c.isEmpty())
      return false;

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
      for (ArrayDeque<T> queue : priorityQueues.values()) {
        size += queue.size();
      }
      return size;
    }
  }

  @Override
  public Object[] toArray() {
    int totalLength = 0;
    List<Object[]> arrays = new ArrayList<>();
    for (ArrayDeque<T> queue : priorityQueues.values()) {
      arrays.add(queue.toArray());
      totalLength += queue.size();
    }
    Object[] result = new Object[totalLength];
    int offset = 0;
    for (Object[] array : arrays) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <E> E[] toArray(E[] a) {
    int totalLength = 0;
    List<E[]> arrays = new ArrayList<>();
    for (ArrayDeque<T> queue : priorityQueues.values()) {
      E[] array = (E[]) Array.newInstance(a.getClass(), queue.size());
      arrays.add(queue.toArray(array));
      totalLength += queue.size();
    }

    E[] result = (E[]) Array.newInstance(a.getClass(), totalLength);
    int offset = 0;
    for (E[] array : arrays) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }

  @Override
  public boolean add(T e) {
    return offer(e);
  }

  @Override
  public T element() {
    T elem = peek();

    if (elem == null)
      throw new NoSuchElementException();

    return peek();
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean offer(T e) {
    Comparable<? super K> key = priority.apply(e);
    ArrayDeque<T> queue = priorityQueues.get(key);
    if (queue == null) {
      queue = new ArrayDeque<T>();
      priorityQueues.put(key, queue);
    }

    if (key.compareTo((K) priorityQueues.firstKey()) < 0) {
      firstQueue = queue;
    }

    return queue.offer(e);
  }

  @Override
  public T peek() {
    if (isEmpty())
      return null;

    return firstQueue.peek();
  }

  @Override
  public T poll() {
    if (isEmpty())
      return null;

    T elem = firstQueue.poll();
    if (firstQueue.isEmpty()) {
      priorityQueues.remove(priorityQueues.firstKey());
      if (!priorityQueues.isEmpty()) {
        firstQueue = priorityQueues.get(priorityQueues.firstKey());
      }
    }

    return elem;
  }

  @Override
  public T remove() {
    T elem = poll();

    if (elem == null)
      throw new NoSuchElementException();

    return elem;
  }

}
