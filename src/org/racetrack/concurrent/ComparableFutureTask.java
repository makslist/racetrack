package org.racetrack.concurrent;

import java.util.concurrent.*;

// A future task that wraps around the priority task to be used in the queue
public class ComparableFutureTask<T> extends FutureTask<T> implements Comparable<ComparableFutureTask<T>> {

  protected ComparableTask<T> comparableTask;

  public ComparableFutureTask(ComparableTask<T> comparableTask) {
    super(comparableTask);
    this.comparableTask = comparableTask;
  }

  @Override
  public int compareTo(ComparableFutureTask<T> o) {
    if (o == null)
      return -1;
    return comparableTask.compareTo(o.comparableTask);
  }

}