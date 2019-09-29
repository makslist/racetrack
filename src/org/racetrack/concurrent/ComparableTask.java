package org.racetrack.concurrent;

import java.util.concurrent.*;

public abstract class ComparableTask<V> implements Callable<V>, Comparable<ComparableTask<V>> {
}
