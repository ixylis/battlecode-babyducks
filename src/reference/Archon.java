package reference;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.RobotInfo;

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
    private int totalSpent = 0;
    public void turn() throws GameActionException {
        //int income = rc.getTeamLeadAmount(rc.getTeam()) - lastTurnMoney;
        int income = rc.readSharedArray(INDEX_INCOME)/2;
        int liveMiners = rc.readSharedArray(INDEX_LIVE_MINERS)/2;
        if(DEBUG) {
            MapLocation enemyLoc = Robot.intToChunk(rc.readSharedArray(INDEX_ENEMY_LOCATION+rc.getRoundNum()%Robot.NUM_ENEMY_SOLDIER_CHUNKS));
            rc.setIndicatorString(myHQIndex+" income="+income+" miners="+liveMiners+" enemy="+enemyLoc);
        }
        //determine if it's my turn to build
        //it's my turn if every other archon has spent at least my spending minus 100
        boolean myTurn = true;
        for(int i=0;i<4;i++) {
            if(i == myHQIndex)
                continue;
            int x = rc.readSharedArray(INDEX_HQ_SPENDING + i);
            if((x&0x4000)==0) //this archon is dead
                continue;
            if(((x&0x3000)>>12) == (rc.getRoundNum()+1)%4) { //this archon didn't update for three rounds, so it's dead
                rc.writeSharedArray(INDEX_HQ_SPENDING + i, 0);
                continue;
            }
            if(((x&0xfff)<<4) < totalSpent - 100)
                myTurn = false;
        }
        if(myTurn) {
            if(rc.getTeamLeadAmount(rc.getTeam()) < 1000 && (income>(liveMiners-5)*25 || rc.getRoundNum()<20)) {
                if(build(RobotType.MINER))
                    miners++;
            } else {
                RobotInfo [] robots = rc.senseNearbyRobots(RobotType.ARCHON.visionRadiusSquared, rc.getTeam());
                int numBuilders = 0;
                for (RobotInfo robot : robots) {
                    if (robot.type == RobotType.BUILDER) numBuilders ++;
                }
                Direction preferredBuilder = rc.getLocation().directionTo(new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2));
                if (rc.canBuildRobot(RobotType.BUILDER, preferredBuilder) && rc.getTeamLeadAmount(rc.getTeam()) > MAX_LEAD * (numBuilders + 1)) 
                    rc.buildRobot(RobotType.BUILDER, preferredBuilder);
                build(RobotType.SOLDIER);
            }
        }
        super.removeOldEnemySoldierLocations();
        super.updateEnemySoliderLocations();
        rc.writeSharedArray(myHQIndex + Robot.INDEX_HQ_SPENDING, 0x4000 | ((rc.getRoundNum()%4)<<12) | (totalSpent>>4));
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
                totalSpent += t.buildCostLead;
                return true;
            }
        }
        return false;
    }

}
