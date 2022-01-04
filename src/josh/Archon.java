package josh;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Archon extends Robot {
    Archon(RobotController r) {
        super(r);
    }
    private int lastTurnMoney=0;
    private int miners=0;
    public void turn() throws GameActionException {
        int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;
        if(income>miners*2 || miners<3) {
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
        int o = Robot.rng.nextInt(8);
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
