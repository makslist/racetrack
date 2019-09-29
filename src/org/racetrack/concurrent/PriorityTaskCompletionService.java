package org.racetrack.concurrent;

import java.util.concurrent.*;

public class PriorityTaskCompletionService<V> implements CompletionService<V> {

  private final Executor executor;
  private final BlockingQueue<Future<V>> completionQueue;

  public PriorityTaskCompletionService(Executor executor) {
    if (executor == null)
      throw new NullPointerException();
    this.executor = executor;
    this.completionQueue = new LinkedBlockingQueue<Future<V>>();
  }

  public PriorityTaskCompletionService(Executor executor, BlockingQueue<Future<V>> completionQueue) {
    if (executor == null || completionQueue == null)
      throw new NullPointerException();
    this.executor = executor;
    this.completionQueue = completionQueue;
  }

  @Override
  public Future<V> submit(Callable<V> task) {
    if (task == null)
      throw new NullPointerException();
    ComparableTask<V> compTask = (ComparableTask<V>) task;
    ComparableFutureTask<V> f = new ComparableFutureTask<V>(compTask) {
      @Override
      protected void done() {
        completionQueue.add(this);
      }
    };
    executor.execute(f);
    return f;
  }

  @Override
  public Future<V> submit(Runnable task, V result) {
    throw new UnsupportedOperationException();
    // if (task == null)
    // throw new NullPointerException();
    // executor.execute(new ComparableFutureTask<V>(task));
    // return new FutureTask<V>(task, result);
  }

  @Override
  public Future<V> take() throws InterruptedException {
    return completionQueue.take();
  }

  @Override
  public Future<V> poll() {
    return completionQueue.poll();
  }

  @Override
  public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
    return completionQueue.poll(timeout, unit);
  }

}
