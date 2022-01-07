package josh;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Archon extends Robot {
    Archon(RobotController r) throws GameActionException {
        super(r);
        int i;
        for(i=INDEX_MY_HQ;rc.readSharedArray(i)>0 && i<INDEX_MY_HQ+4; i++);
        if(i<INDEX_MY_HQ+4)
            rc.writeSharedArray(i, Robot.locToInt(rc.getLocation()));
    }
    private int lastTurnMoney=0;
    private int miners=0;
    public void turn() throws GameActionException {
        //int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;
        int income = rc.readSharedArray(INDEX_INCOME)/2;
        int liveMiners = rc.readSharedArray(INDEX_LIVE_MINERS)/2;
        rc.setIndicatorString("income="+income+" miners="+liveMiners);
        if(rc.getTeamLeadAmount(rc.getTeam()) < 1000 && (income>(liveMiners-5)*10 || rc.getRoundNum()<20)) {
            if(build(RobotType.MINER))
                miners++;
        } else {
            build(RobotType.SOLDIER);
        }
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
