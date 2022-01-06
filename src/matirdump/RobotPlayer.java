package matirdump;

import battlecode.common.*;

public strictfp class RobotPlayer {

    static int turnCount = 0;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        try {
            switch (rc.getType()) {
                case ARCHON:
                    new Archon(rc).run();
                    break;
                case MINER:
                    new Miner(rc).run();
                    break;
                case SOLDIER:
                    new Soldier(rc).run();
                    break;
                case LABORATORY: // shouldn't get here
                case WATCHTOWER:
                case BUILDER:
                case SAGE:
                    break;
            }
        } catch (GameActionException e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }
    }
}
