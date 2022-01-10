package josh;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Archon extends Robot {
    private int myHQIndex;
    private int initialArchonCount = rc.getArchonCount();
    Archon(RobotController r) throws GameActionException {
        super(r);
        int i;
        for(i=INDEX_MY_HQ;rc.readSharedArray(i)>0 && i<INDEX_MY_HQ+4; i++);
        if(i<INDEX_MY_HQ+4) {
            myHQIndex = i;
            rc.writeSharedArray(i, Robot.locToInt(rc.getLocation()));
        } else {
            rc.disintegrate(); //uh oh something went very wrong
        }
    }
    private int lastTurnMoney=0;
    private int miners=0;
    public void turn() throws GameActionException {
        //int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;
        int income = rc.readSharedArray(INDEX_INCOME)/2;
        int liveMiners = rc.readSharedArray(INDEX_LIVE_MINERS)/2;
        if(DEBUG) {
            MapLocation enemyLoc = Robot.intToChunk(rc.readSharedArray(INDEX_ENEMY_LOCATION+rc.getRoundNum()%Robot.NUM_ENEMY_SOLDIER_CHUNKS));
            rc.setIndicatorString(myHQIndex+" income="+income+" miners="+liveMiners+" enemy="+enemyLoc);
        }
        //determine if it's my turn to build
        if(rc.getTeamLeadAmount(rc.getTeam())>150 || (rc.getRoundNum()%20 + rc.getRoundNum()/20)%initialArchonCount == myHQIndex) {
            if(rc.getTeamLeadAmount(rc.getTeam()) < 1000 && (income>(liveMiners-5)*25 || rc.getRoundNum()<20)) {
                if(build(RobotType.MINER))
                    miners++;
            } else {
                build(RobotType.SOLDIER);
            }
        }
        super.removeOldEnemySoldierLocations();
        super.updateEnemySoliderLocations();
        lastTurnMoney = rc.getTeamLeadAmount(rc.getTeam());
    }
    //builds in a random direction if legal
    private boolean build(RobotType t) throws GameActionException {
        if(rc.getTeamLeadAmount(rc.getTeam()) < t.getLeadWorth(1))
            return false;
        int o = rng.nextInt(8);
        for(int i=0;i<8;i++) {
            Direction dir = directions[(i+o)%8];
            if(rc.canBuildRobot(t, dir)) {
                rc.buildRobot(t, dir);
                return true;
            }
        }
        return false;
    }

}
