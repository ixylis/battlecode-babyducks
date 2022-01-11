package sprint;

import java.util.Arrays;

import battlecode.common.Clock;
import battlecode.common.Direction;
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
        if(rc.isMovementReady()) {
            movement();
            if(!rc.getLocation().equals(recentLocations[recentLocationsIndex])) {
                recentLocationsIndex = (recentLocationsIndex + 1)%10;
                recentLocations[recentLocationsIndex] = rc.getLocation();
            }
        } else
            super.updateEnemySoliderLocations();
        mine();
        if(rc.getRoundNum()%2 != rc.readSharedArray(INDEX_LIVE_MINERS)%2) {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.getRoundNum()%2);
        } else {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.readSharedArray(INDEX_LIVE_MINERS));
        }
        //rc.setIndicatorString("mined="+recentlyMined);
        if(rc.getRoundNum()%20==0) {
            if((rc.getRoundNum()/20)%2 == rc.readSharedArray(Robot.INDEX_INCOME)%2) {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+(rc.getRoundNum()/20+1)%2);
            } else {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+rc.readSharedArray(INDEX_INCOME));
            }
            recentlyMined=0;
        }
    }
    private MapLocation[] recentLocations=new MapLocation[10];
    private int recentLocationsIndex = 0;
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
                int x=r.location.x-myx;
                int y=r.location.y-myy;
                hasNearbyMiner[x+4][y+4] = true;
                hasNearbyMiner[x+6][y+4] = true;
                hasNearbyMiner[x+5][y+4] = true;
                hasNearbyMiner[x+4][y+5] = true;
                hasNearbyMiner[x+6][y+5] = true;
                hasNearbyMiner[x+5][y+5] = true;
                hasNearbyMiner[x+4][y+6] = true;
                hasNearbyMiner[x+6][y+6] = true;
                hasNearbyMiner[x+5][y+6] = true;
            }
        }
        if(nearest!=null) {
            //moveInDirection(nearest.location.directionTo(rc.getLocation()));
            //return;
        }
        int b0 = Clock.getBytecodeNum();
        MapLocation l = rc.getLocation();
        MapLocation loc;int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();
        boolean onMapX0 = myx + -2 >= 0;
        boolean onMapY0 = myy + -2 >= 0;
        boolean onMapX3 = myx + 1 < mapWidth;
        boolean onMapY3 = myy + 1 < mapHeight;
        boolean onMapX1 = myx + -1 >= 0;
        boolean onMapY1 = myy + -1 >= 0;
        boolean onMapX4 = myx + 2 < mapWidth;
        boolean onMapY4 = myy + 2 < mapHeight;
        boolean onMapX2 = true;
        boolean onMapY2 = true;
        int pb00 = (onMapX0 && onMapY0 && !hasNearbyMiner[3][3])?rc.senseLead(l.translate(-2, -2)):0;
        int pb01 = (onMapX0 && onMapY1 && !hasNearbyMiner[3][4])?rc.senseLead(l.translate(-2, -1)):0;
        int pb02 = (onMapX0 && onMapY2 && !hasNearbyMiner[3][5])?rc.senseLead(l.translate(-2, 0)):0;
        int pb03 = (onMapX0 && onMapY3 && !hasNearbyMiner[3][6])?rc.senseLead(l.translate(-2, 1)):0;
        int pb04 = (onMapX0 && onMapY4 && !hasNearbyMiner[3][7])?rc.senseLead(l.translate(-2, 2)):0;
        int pb10 = (onMapX1 && onMapY0 && !hasNearbyMiner[4][3])?rc.senseLead(l.translate(-1, -2)):0;
        int pb11 = (onMapX1 && onMapY1 && !hasNearbyMiner[4][4])?rc.senseLead(l.translate(-1, -1)):0;
        int pb12 = (onMapX1 && onMapY2 && !hasNearbyMiner[4][5])?rc.senseLead(l.translate(-1, 0)):0;
        int pb13 = (onMapX1 && onMapY3 && !hasNearbyMiner[4][6])?rc.senseLead(l.translate(-1, 1)):0;
        int pb14 = (onMapX1 && onMapY4 && !hasNearbyMiner[4][7])?rc.senseLead(l.translate(-1, 2)):0;
        int pb20 = (onMapX2 && onMapY0 && !hasNearbyMiner[5][3])?rc.senseLead(l.translate(0, -2)):0;
        int pb21 = (onMapX2 && onMapY1 && !hasNearbyMiner[5][4])?rc.senseLead(l.translate(0, -1)):0;
        int pb22 = (onMapX2 && onMapY2 && !hasNearbyMiner[5][5])?rc.senseLead(l.translate(0, 0)):0;
        int pb23 = (onMapX2 && onMapY3 && !hasNearbyMiner[5][6])?rc.senseLead(l.translate(0, 1)):0;
        int pb24 = (onMapX2 && onMapY4 && !hasNearbyMiner[5][7])?rc.senseLead(l.translate(0, 2)):0;
        int pb30 = (onMapX3 && onMapY0 && !hasNearbyMiner[6][3])?rc.senseLead(l.translate(1, -2)):0;
        int pb31 = (onMapX3 && onMapY1 && !hasNearbyMiner[6][4])?rc.senseLead(l.translate(1, -1)):0;
        int pb32 = (onMapX3 && onMapY2 && !hasNearbyMiner[6][5])?rc.senseLead(l.translate(1, 0)):0;
        int pb33 = (onMapX3 && onMapY3 && !hasNearbyMiner[6][6])?rc.senseLead(l.translate(1, 1)):0;
        int pb34 = (onMapX3 && onMapY4 && !hasNearbyMiner[6][7])?rc.senseLead(l.translate(1, 2)):0;
        int pb40 = (onMapX4 && onMapY0 && !hasNearbyMiner[7][3])?rc.senseLead(l.translate(2, -2)):0;
        int pb41 = (onMapX4 && onMapY1 && !hasNearbyMiner[7][4])?rc.senseLead(l.translate(2, -1)):0;
        int pb42 = (onMapX4 && onMapY2 && !hasNearbyMiner[7][5])?rc.senseLead(l.translate(2, 0)):0;
        int pb43 = (onMapX4 && onMapY3 && !hasNearbyMiner[7][6])?rc.senseLead(l.translate(2, 1)):0;
        int pb44 = (onMapX4 && onMapY4 && !hasNearbyMiner[7][7])?rc.senseLead(l.translate(2, 2)):0;
        int[] adjacentLead = {
        pb24+pb34+pb33+pb32+pb22+pb12+pb13+pb14+pb23,
        pb34+pb44+pb43+pb42+pb32+pb22+pb23+pb24+pb33,
        pb33+pb43+pb42+pb41+pb31+pb21+pb22+pb23+pb32,
        pb32+pb42+pb41+pb40+pb30+pb20+pb21+pb22+pb31,
        pb22+pb32+pb31+pb30+pb20+pb10+pb11+pb12+pb21,
        pb12+pb22+pb21+pb20+pb10+pb00+pb01+pb02+pb11,
        pb13+pb23+pb22+pb21+pb11+pb01+pb02+pb03+pb12,
        pb14+pb24+pb23+pb22+pb12+pb02+pb03+pb04+pb13,
        pb23+pb33+pb32+pb31+pb21+pb11+pb12+pb13+pb22
        };
        int bestDir = 8;
        int best = adjacentLead[8]*100/(10+rc.senseRubble(l));
        for(int i=0;i<8;i++) {
            if(!rc.canMove(Direction.allDirections()[i]))
                continue;
            int x =adjacentLead[i]*100/(10+rc.senseRubble(l.add(Direction.allDirections()[i]))); 
            if(x > best) {
                bestDir = i;
                best = x;
            }
        }
        int b1 = Clock.getBytecodeNum();
        MapLocation recentLoc = recentLocations[(recentLocationsIndex+6)%10];
        int dx = recentLoc==null?0:recentLoc.x - rc.getLocation().x;
        int dy = recentLoc==null?0:recentLoc.y - rc.getLocation().y;
        rc.setIndicatorString(bestDir + " adj lead "+adjacentLead[bestDir]+" dx "+dx+" dy "+dy+ " bc "+(b1-b0));
        if(bestDir<8) { //exclude CENTER
            rc.move(Robot.directions[bestDir]);
            return;
        } else if(adjacentLead[bestDir] > 0) {
            return;
        }
        if(dx*-3+dy*0<=0 && rc.canSenseLocation(loc=l.translate(-3, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][5]) {
            moveToward(loc);
        } else if(dx*0+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(0, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][2]) {
            moveToward(loc);
        } else if(dx*0+dy*3<=0 && rc.canSenseLocation(loc=l.translate(0, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][8]) {
            moveToward(loc);
        } else if(dx*3+dy*0<=0 && rc.canSenseLocation(loc=l.translate(3, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][5]) {
            moveToward(loc);
        } else if(dx*-3+dy*-1<=0 && rc.canSenseLocation(loc=l.translate(-3, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][4]) {
            moveToward(loc);
        } else if(dx*-3+dy*1<=0 && rc.canSenseLocation(loc=l.translate(-3, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][6]) {
            moveToward(loc);
        } else if(dx*-1+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(-1, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][2]) {
            moveToward(loc);
        } else if(dx*-1+dy*3<=0 && rc.canSenseLocation(loc=l.translate(-1, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][8]) {
            moveToward(loc);
        } else if(dx*1+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(1, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][2]) {
            moveToward(loc);
        } else if(dx*1+dy*3<=0 && rc.canSenseLocation(loc=l.translate(1, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][8]) {
            moveToward(loc);
        } else if(dx*3+dy*-1<=0 && rc.canSenseLocation(loc=l.translate(3, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][4]) {
            moveToward(loc);
        } else if(dx*3+dy*1<=0 && rc.canSenseLocation(loc=l.translate(3, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][6]) {
            moveToward(loc);
        } else if(dx*-3+dy*-2<=0 && rc.canSenseLocation(loc=l.translate(-3, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][3]) {
            moveToward(loc);
        } else if(dx*-3+dy*2<=0 && rc.canSenseLocation(loc=l.translate(-3, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][7]) {
            moveToward(loc);
        } else if(dx*-2+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(-2, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][2]) {
            moveToward(loc);
        } else if(dx*-2+dy*3<=0 && rc.canSenseLocation(loc=l.translate(-2, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][8]) {
            moveToward(loc);
        } else if(dx*2+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(2, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][2]) {
            moveToward(loc);
        } else if(dx*2+dy*3<=0 && rc.canSenseLocation(loc=l.translate(2, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][8]) {
            moveToward(loc);
        } else if(dx*3+dy*-2<=0 && rc.canSenseLocation(loc=l.translate(3, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][3]) {
            moveToward(loc);
        } else if(dx*3+dy*2<=0 && rc.canSenseLocation(loc=l.translate(3, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][7]) {
            moveToward(loc);
        } else if(dx*-4+dy*0<=0 && rc.canSenseLocation(loc=l.translate(-4, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][5]) {
            moveToward(loc);
        } else if(dx*0+dy*-4<=0 && rc.canSenseLocation(loc=l.translate(0, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][1]) {
            moveToward(loc);
        } else if(dx*0+dy*4<=0 && rc.canSenseLocation(loc=l.translate(0, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[5][9]) {
            moveToward(loc);
        } else if(dx*4+dy*0<=0 && rc.canSenseLocation(loc=l.translate(4, 0)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][5]) {
            moveToward(loc);
        } else if(dx*-4+dy*-1<=0 && rc.canSenseLocation(loc=l.translate(-4, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][4]) {
            moveToward(loc);
        } else if(dx*-4+dy*1<=0 && rc.canSenseLocation(loc=l.translate(-4, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][6]) {
            moveToward(loc);
        } else if(dx*-1+dy*-4<=0 && rc.canSenseLocation(loc=l.translate(-1, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][1]) {
            moveToward(loc);
        } else if(dx*-1+dy*4<=0 && rc.canSenseLocation(loc=l.translate(-1, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[4][9]) {
            moveToward(loc);
        } else if(dx*1+dy*-4<=0 && rc.canSenseLocation(loc=l.translate(1, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][1]) {
            moveToward(loc);
        } else if(dx*1+dy*4<=0 && rc.canSenseLocation(loc=l.translate(1, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[6][9]) {
            moveToward(loc);
        } else if(dx*4+dy*-1<=0 && rc.canSenseLocation(loc=l.translate(4, -1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][4]) {
            moveToward(loc);
        } else if(dx*4+dy*1<=0 && rc.canSenseLocation(loc=l.translate(4, 1)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][6]) {
            moveToward(loc);
        } else if(dx*-3+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(-3, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][2]) {
            moveToward(loc);
        } else if(dx*-3+dy*3<=0 && rc.canSenseLocation(loc=l.translate(-3, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[2][8]) {
            moveToward(loc);
        } else if(dx*3+dy*-3<=0 && rc.canSenseLocation(loc=l.translate(3, -3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][2]) {
            moveToward(loc);
        } else if(dx*3+dy*3<=0 && rc.canSenseLocation(loc=l.translate(3, 3)) && rc.senseLead(loc)>1 && !hasNearbyMiner[8][8]) {
            moveToward(loc);
        } else if(dx*-4+dy*-2<=0 && rc.canSenseLocation(loc=l.translate(-4, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][3]) {
            moveToward(loc);
        } else if(dx*-4+dy*2<=0 && rc.canSenseLocation(loc=l.translate(-4, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[1][7]) {
            moveToward(loc);
        } else if(dx*-2+dy*-4<=0 && rc.canSenseLocation(loc=l.translate(-2, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][1]) {
            moveToward(loc);
        } else if(dx*-2+dy*4<=0 && rc.canSenseLocation(loc=l.translate(-2, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[3][9]) {
            moveToward(loc);
        } else if(dx*2+dy*-4<=0 && rc.canSenseLocation(loc=l.translate(2, -4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][1]) {
            moveToward(loc);
        } else if(dx*2+dy*4<=0 && rc.canSenseLocation(loc=l.translate(2, 4)) && rc.senseLead(loc)>1 && !hasNearbyMiner[7][9]) {
            moveToward(loc);
        } else if(dx*4+dy*-2<=0 && rc.canSenseLocation(loc=l.translate(4, -2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][3]) {
            moveToward(loc);
        } else if(dx*4+dy*2<=0 && rc.canSenseLocation(loc=l.translate(4, 2)) && rc.senseLead(loc)>1 && !hasNearbyMiner[9][7]) {
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
