package josh;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class Miner extends Robot {
    int recentlyMined = 0;
    Miner(RobotController r) throws GameActionException {
        super(r);
    }
    public void turn() throws GameActionException {
        movement();
        mine();
        if(rc.getRoundNum()%2 != rc.readSharedArray(INDEX_LIVE_MINERS)%2) {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.getRoundNum()%2);
        } else {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.readSharedArray(INDEX_LIVE_MINERS));
        }
        rc.setIndicatorString("mined="+recentlyMined);
        if(rc.getRoundNum()%20==0) {
            if((rc.getRoundNum()/20)%2 == rc.readSharedArray(Robot.INDEX_INCOME)%2) {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+(rc.getRoundNum()/20+1)%2);
            } else {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+rc.readSharedArray(INDEX_INCOME));
            }
            recentlyMined=0;
        }
    }
    private void movement() throws GameActionException {
        boolean[][] hasNearbyMiner = new boolean[11][11];
        RobotInfo[] nearby = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam());
        RobotInfo nearest = null;
        int myx=rc.getLocation().x;
        int myy=rc.getLocation().y;
        for(RobotInfo r:nearby) {
            if(r.type==RobotType.MINER) {
                if(nearest==null || rc.getLocation().distanceSquaredTo(nearest.location) > rc.getLocation().distanceSquaredTo(r.location))
                    nearest=r;
                int x=r.location.x;
                int y=r.location.y;
                hasNearbyMiner[x+4-myx][y+4-myy] = true;
                hasNearbyMiner[x+6-myx][y+4-myy] = true;
                hasNearbyMiner[x+5-myx][y+4-myy] = true;
                hasNearbyMiner[x+4-myx][y+5-myy] = true;
                hasNearbyMiner[x+6-myx][y+5-myy] = true;
                hasNearbyMiner[x+5-myx][y+5-myy] = true;
                hasNearbyMiner[x+4-myx][y+6-myy] = true;
                hasNearbyMiner[x+6-myx][y+6-myy] = true;
                hasNearbyMiner[x+5-myx][y+6-myy] = true;
            }
        }
        if(nearest!=null) {
            //moveInDirection(nearest.location.directionTo(rc.getLocation()));
            //return;
        }
        MapLocation l = rc.getLocation();
        MapLocation loc;
        if(rc.canSenseLocation(loc=l.translate(-2, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][1]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(0, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][9]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][5]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][1]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-1, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][9]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][1]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(1, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][9]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][4]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][6]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-3, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][2]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(3, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][8]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-4, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][7]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][1]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(-2, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][9]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][1]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(2, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][9]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][3]) {
            moveToward(loc);
        } else if(rc.canSenseLocation(loc=l.translate(4, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][7]) {
            moveToward(loc);
        } else {
            if(rc.isActionReady()) //only wander if you did not mine this turn
                wander();
        }
    }
    private void mine() throws GameActionException {
        MapLocation l = rc.getLocation();
        MapLocation loc;
        
        while(rc.isActionReady() && rc.senseLead(l)>1) {
            rc.mineLead(l);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 0)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, -1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, 1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 0)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, -1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, -1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 1)) && rc.senseLead(loc)>1) {
            rc.mineLead(loc);
            recentlyMined++;
        }
    }
}
