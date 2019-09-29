package org.racetrack.concurrent;

import java.util.*;
import java.util.concurrent.*;

public class DistinctPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {

  private static final long serialVersionUID = 1L;

  public DistinctPriorityBlockingQueue() {
    super();
  }

  public DistinctPriorityBlockingQueue(int initialCapacity) {
    super(initialCapacity);
  }

  public DistinctPriorityBlockingQueue(Collection<? extends E> c) {
    super(c);
  }

  public DistinctPriorityBlockingQueue(int initialCapacity, Comparator<? super E> comparator) {
    super(initialCapacity, comparator);
  }

  @Override
  public boolean offer(E e) {
    return !contains(e) ? super.offer(e) : false;
  }

}
