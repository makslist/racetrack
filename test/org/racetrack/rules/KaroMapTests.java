package org.racetrack.rules;

import org.junit.runner.*;
import org.junit.runners.*;
import org.junit.runners.Suite.*;

@RunWith(Suite.class)
@SuiteClasses({ KaroMapTest_FailingOneStepMoves.class, KaroMapTest_FailingThreeStepMoves.class,
    KaroMapTest_FailingTwoStepMoves.class, KaroMapTest_GetForMapTile.class, KaroMapTest_SucessfulThreeStepMoves.class })
public class KaroMapTests {

}
