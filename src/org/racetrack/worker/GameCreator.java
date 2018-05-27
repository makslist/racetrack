package org.racetrack.worker;

import org.racetrack.karoapi.*;

public class GameCreator {

  public static void main(String[] args) {
    String login = "userLogin";
    String password = "password";
    KaroClient karo = new KaroClient(login, password, true);

    Game game = Game.newRandom(null, login, null);

    karo.addGame(game);
    karo.logOff();
  }

}
