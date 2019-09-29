package org.racetrack.concurrent;

import java.util.concurrent.*;

public class PriorityTaskThreadPoolExecutor extends ThreadPoolExecutor {

  public PriorityTaskThreadPoolExecutor(int poolSize) {
    super(poolSize, poolSize, 5, TimeUnit.MINUTES, new PriorityBlockingQueue<Runnable>(500));
  }

  public PriorityTaskThreadPoolExecutor(int poolSize, BlockingQueue<Runnable> workQueue) {
    super(poolSize, poolSize, 5, TimeUnit.MINUTES, workQueue);
  }

  @Override
  protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
    // Override newTaskFor() to return wrap ComparableTask
    // with a ComparableFutureTaskWrapper.
    return new ComparableFutureTask<V>((ComparableTask<V>) c);
  }

}
