package org.racetrack.karoapi;

import org.eclipse.collections.api.list.*;
import org.eclipse.collections.impl.list.mutable.*;

public enum BlacklistedUser {

  Nebresh, Akari, Rennsumsa, Hfoertel, sauzwerg, buttertasse, HCBerti, christian, angel, N3bukadnezar, roterPanda, blackrog;

  public static MutableList<BlacklistedUser> unfair() {
    return new FastList<BlacklistedUser>().with(Nebresh, blackrog);
  }

  public static MutableList<BlacklistedUser> inactive() {
    return new FastList<BlacklistedUser>().with(Akari, Rennsumsa, Hfoertel, sauzwerg, buttertasse, HCBerti, christian, angel,
        N3bukadnezar, roterPanda);
  }

}
