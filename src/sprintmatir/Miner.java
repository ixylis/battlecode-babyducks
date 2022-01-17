package sprintmatir;

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
    MapLocation home;
    Miner(RobotController r) throws GameActionException {
        super(r);
        for(RobotInfo r1 : rc.senseNearbyRobots(2, rc.getTeam())) {
            if(r1.type == RobotType.ARCHON)
                home = r1.location;
        }
        if(home == null)
            home = rc.getLocation();
    }
    int lastSuitabilityRound = 0;
    MapLocation target;
    public void turn() throws GameActionException {
        //boolean shouldDoSuitability = false;
        if(rc.isMovementReady() && (rc.getRoundNum() - lastSuitabilityRound < 10)) {
            movement();
        } else {
            lastSuitabilityRound = rc.getRoundNum();
            target = this.determineTarget();
            super.updateEnemySoliderLocations();
        }
        //if(rc.getID() != 13202)
        //rc.setIndicatorString(Arrays.toString(suitability));

        mine();
        if(rc.getRoundNum()%2 != rc.readSharedArray(INDEX_LIVE_MINERS)%2) {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.getRoundNum()%2);
        } else {
            rc.writeSharedArray(INDEX_LIVE_MINERS, 2+rc.readSharedArray(INDEX_LIVE_MINERS));
        }
        //rc.setIndicatorString("mined="+recentlyMined);
        if(rc.getRoundNum()%40==0) {
            if((rc.getRoundNum()/40)%2 == rc.readSharedArray(Robot.INDEX_INCOME)%2) {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+(rc.getRoundNum()/40+1)%2);
            } else {
                rc.writeSharedArray(INDEX_INCOME, recentlyMined*2+rc.readSharedArray(INDEX_INCOME));
            }
            recentlyMined=0;
        }
        if(!rc.isMovementReady()) {
            super.writeUnexploredChunk();
        }
        /*
        if(shouldDoSuitability && rc.getRoundNum() > 1+lastSuitabilityRound) {
            lastSuitabilityRound = rc.getRoundNum();
            this.determineMovementSuitability();
        }
        */

    }

    /*
     * new mining plan:
     * if there's stuff within two tiles that's not adjacent to other miners, move to the space that maximizes your mining.
     *
     * otherwise, move based on a heuristic:
     * move away from friendly miners
     * move toward deposits
     * move toward low rubble
     * move away from where you just moved
     * move away from enemy units
     * move away from home
     * for each of the 8 possible moves, use the following weights: (d is distance sq to the relevant thing)
     * - 5/d for friendly miners
     * + log_10(deposit)/d for deposits
     * multiply positive things by 10/(10+r) for rubble and negative things by (10+r)/10
     * -10 per tile adjacent to a tile you were on in the last 4 turns
     * -10/d for enemy fighters
     * -5/d for your home
     */
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

        int[] nearbyRubble = new int[9];
        for(int i=0;i<9;i++) {
            MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
            nearbyRubble[i] = rc.onTheMap(m)?rc.senseRubble(m):0;
        }

        int b0 = Clock.getBytecodeNum();
        MapLocation l = rc.getLocation();
        int mapWidth = rc.getMapWidth();
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
        int pb00 = (onMapX0 && onMapY0 && !hasNearbyMiner[3][3])?rc.senseLead(l.translate(-2, -2)):0; if(pb00==1) pb00=0;
        int pb01 = (onMapX0 && onMapY1 && !hasNearbyMiner[3][4])?rc.senseLead(l.translate(-2, -1)):0; if(pb01==1) pb01=0;
        int pb02 = (onMapX0 && onMapY2 && !hasNearbyMiner[3][5])?rc.senseLead(l.translate(-2, 0)):0; if(pb02==1) pb02=0;
        int pb03 = (onMapX0 && onMapY3 && !hasNearbyMiner[3][6])?rc.senseLead(l.translate(-2, 1)):0; if(pb03==1) pb03=0;
        int pb04 = (onMapX0 && onMapY4 && !hasNearbyMiner[3][7])?rc.senseLead(l.translate(-2, 2)):0; if(pb04==1) pb04=0;
        int pb10 = (onMapX1 && onMapY0 && !hasNearbyMiner[4][3])?rc.senseLead(l.translate(-1, -2)):0; if(pb10==1) pb10=0;
        int pb11 = (onMapX1 && onMapY1 && !hasNearbyMiner[4][4])?rc.senseLead(l.translate(-1, -1)):0; if(pb11==1) pb11=0;
        int pb12 = (onMapX1 && onMapY2 && !hasNearbyMiner[4][5])?rc.senseLead(l.translate(-1, 0)):0; if(pb12==1) pb12=0;
        int pb13 = (onMapX1 && onMapY3 && !hasNearbyMiner[4][6])?rc.senseLead(l.translate(-1, 1)):0; if(pb13==1) pb13=0;
        int pb14 = (onMapX1 && onMapY4 && !hasNearbyMiner[4][7])?rc.senseLead(l.translate(-1, 2)):0; if(pb14==1) pb14=0;
        int pb20 = (onMapX2 && onMapY0 && !hasNearbyMiner[5][3])?rc.senseLead(l.translate(0, -2)):0; if(pb20==1) pb20=0;
        int pb21 = (onMapX2 && onMapY1 && !hasNearbyMiner[5][4])?rc.senseLead(l.translate(0, -1)):0; if(pb21==1) pb21=0;
        int pb22 = (onMapX2 && onMapY2 && !hasNearbyMiner[5][5])?rc.senseLead(l.translate(0, 0)):0; if(pb22==1) pb22=0;
        int pb23 = (onMapX2 && onMapY3 && !hasNearbyMiner[5][6])?rc.senseLead(l.translate(0, 1)):0; if(pb23==1) pb23=0;
        int pb24 = (onMapX2 && onMapY4 && !hasNearbyMiner[5][7])?rc.senseLead(l.translate(0, 2)):0; if(pb24==1) pb24=0;
        int pb30 = (onMapX3 && onMapY0 && !hasNearbyMiner[6][3])?rc.senseLead(l.translate(1, -2)):0; if(pb30==1) pb30=0;
        int pb31 = (onMapX3 && onMapY1 && !hasNearbyMiner[6][4])?rc.senseLead(l.translate(1, -1)):0; if(pb31==1) pb31=0;
        int pb32 = (onMapX3 && onMapY2 && !hasNearbyMiner[6][5])?rc.senseLead(l.translate(1, 0)):0; if(pb32==1) pb32=0;
        int pb33 = (onMapX3 && onMapY3 && !hasNearbyMiner[6][6])?rc.senseLead(l.translate(1, 1)):0; if(pb33==1) pb33=0;
        int pb34 = (onMapX3 && onMapY4 && !hasNearbyMiner[6][7])?rc.senseLead(l.translate(1, 2)):0; if(pb34==1) pb34=0;
        int pb40 = (onMapX4 && onMapY0 && !hasNearbyMiner[7][3])?rc.senseLead(l.translate(2, -2)):0; if(pb40==1) pb40=0;
        int pb41 = (onMapX4 && onMapY1 && !hasNearbyMiner[7][4])?rc.senseLead(l.translate(2, -1)):0; if(pb41==1) pb41=0;
        int pb42 = (onMapX4 && onMapY2 && !hasNearbyMiner[7][5])?rc.senseLead(l.translate(2, 0)):0; if(pb42==1) pb42=0;
        int pb43 = (onMapX4 && onMapY3 && !hasNearbyMiner[7][6])?rc.senseLead(l.translate(2, 1)):0; if(pb43==1) pb43=0;
        int pb44 = (onMapX4 && onMapY4 && !hasNearbyMiner[7][7])?rc.senseLead(l.translate(2, 2)):0; if(pb44==1) pb44=0;
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
            int x =adjacentLead[i]*100/(10+nearbyRubble[i]);
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
        } else {
            /*
            int bestSuitability = -9999;
            int bestSuitabilityDir = 0;
            for(int i=0;i<8;i++) {
                if(suitability[i] > bestSuitability && rc.canMove(Robot.directions[i])) {
                    bestSuitability = suitability[i];
                    bestSuitabilityDir = i;
                }
            }
            rc.move(Robot.directions[bestSuitabilityDir]);
            return;
            */
            if(target == null)
                target = determineTarget();
            moveToward(target);
        }
    }
    private MapLocation determineTarget() throws GameActionException {
        if(target != null && rc.getLocation().distanceSquaredTo(target) > 50 && rng.nextDouble() < .9)
            return target;
        RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] nearby = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam());
        MapLocation[] pbLocs = rc.senseNearbyLocationsWithLead(RobotType.MINER.visionRadiusSquared,2);
        boolean[] ignorablePb = new boolean[pbLocs.length];
        for(int i=0;i<pbLocs.length;i++) {
            MapLocation pb = pbLocs[i];
            if(rc.getLocation().distanceSquaredTo(pb) < 9) {
                ignorablePb[i] = true;
                continue;
            }
            for(RobotInfo r : nearby) {
                if(r.type == RobotType.MINER) {
                    if(r.location.distanceSquaredTo(pb) < rc.getLocation().distanceSquaredTo(pb)) {
                        ignorablePb[i] = true;
                        break;
                    }
                }
            }
        }
        for(int i=0;i<pbLocs.length;i++) {
            if(!ignorablePb[i]) {
                return pbLocs[i];
            }
        }
        MapLocation m1 = rc.getLocation();
        for(RobotInfo r : enemies) {
            switch(r.type) {
                case SOLDIER:
                case SAGE:
                case WATCHTOWER:
                    Direction d = r.location.directionTo(rc.getLocation());
                    m1 = m1.add(d).add(d).add(d);
                    //s += " " + (-100.0/m.distanceSquaredTo(r.location));
                    break;
                default:
            }
        }
        MapLocation nearestEnemy = getNearestEnemyChunk();
        if(nearestEnemy != null && rc.getLocation().distanceSquaredTo(nearestEnemy) < 36) {
            Direction d = nearestEnemy.directionTo(rc.getLocation());
            m1 = m1.add(d).add(d).add(d);
        }
        if(!m1.equals(rc.getLocation())) {
            return m1;
        }
        MapLocation recentLoc = recentLocations[(recentLocationsIndex+9)%10];
        MapLocation m = recentLoc==null?rc.getLocation():rc.getLocation().add(recentLoc.directionTo(rc.getLocation()));
        /*
        if(m.x < rc.getMapWidth()/2) {
            m = m.translate(4, 0);
        } else {
            m = m.translate(-4, 0);
        }
        if(m.y < rc.getMapHeight()/2) {
            m = m.translate(0, 4);
        } else {
            m = m.translate(0, -4);
        }
        */
        for(RobotInfo r : nearby) {
            if(r.type == RobotType.MINER) {
                int dx = rc.getLocation().x - r.location.x, dy = rc.getLocation().y - r.location.y;
                m = m.translate(dx>0?4-dx:-4-dx,dy>0?4-dy:-4-dy);
            }
        }
        //rc.setIndicatorString(m+"");
        rc.setIndicatorDot(m, 255, 0, 0);
        MapLocation result = super.getNearestUnexploredChunk(m);
        if(result==null) {
            super.clearUnexploredChunks();
            return super.getNearestUnexploredChunk(m);
        }
        return result;
    }
    private int[] suitability = new int[8];
    private void determineMovementSuitability() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] nearby = rc.senseNearbyRobots(RobotType.MINER.visionRadiusSquared, rc.getTeam());
        int[] nearbyRubble = new int[9];
        for(int i=0;i<9;i++) {
            MapLocation m = rc.getLocation().add(Direction.allDirections()[i]);
            nearbyRubble[i] = rc.onTheMap(m)?rc.senseRubble(m):0;
        }
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();

        MapLocation recentLoc = recentLocations[(recentLocationsIndex+6)%10];
        if(recentLoc ==null) recentLoc = rc.getLocation();

        MapLocation unexplored = getNearestUnexploredChunk(rc.getLocation().add(recentLoc.directionTo(rc.getLocation())));
        MapLocation nearestEnemy = getNearestEnemyChunk();
        if(nearestEnemy!=null && rc.getLocation().distanceSquaredTo(nearestEnemy) > 100)
            nearestEnemy = null;
        rc.setIndicatorLine(rc.getLocation(), unexplored, 0, 255, 0);

        MapLocation[] pbLocs = rc.senseNearbyLocationsWithLead(RobotType.MINER.visionRadiusSquared,2);
        boolean[] ignorablePb = new boolean[pbLocs.length];
        for(int i=0;i<pbLocs.length;i++) {
            MapLocation pb = pbLocs[i];
            if(rc.getLocation().distanceSquaredTo(pb) < 9) {
                ignorablePb[i] = true;
                continue;
            }
            for(RobotInfo r : nearby) {
                if(r.type == RobotType.MINER) {
                    if(r.location.distanceSquaredTo(pb) < rc.getLocation().distanceSquaredTo(pb)) {
                        ignorablePb[i] = true;
                        break;
                    }
                }
            }
        }
        for(int i=0;i<8;i++) {
            suitability[i] = 0;
            //String s = "pb";
            MapLocation m = rc.getLocation().add(Robot.directions[i]);
            if(!rc.onTheMap(m))
                continue;
            if(rc.isLocationOccupied(m))
                continue;
            double y = 0, x = 0;
            double rubbleMult = 10.0/(10 + nearbyRubble[i]);
            for(int j=0;j<pbLocs.length;j++) {
                MapLocation pbLoc = pbLocs[j];
                if(ignorablePb[j]) continue;
                y += rc.senseLead(pbLoc)/m.distanceSquaredTo(pbLoc);
                //s += " "+ rc.senseLead(pbLoc)/m.distanceSquaredTo(pbLoc);
            }
            if(unexplored!=null)
                x -= 10 * Math.sqrt(m.distanceSquaredTo(unexplored));
            if(nearestEnemy!=null)
                x += 100 * Math.sqrt(m.distanceSquaredTo(nearestEnemy));
            //s += " f";
            for(RobotInfo r : nearby) {
                if(r.type != RobotType.MINER)
                    continue;
                x -= 25.0/m.distanceSquaredTo(r.location);
                //s += " " + (-25.0/m.distanceSquaredTo(r.location));
            }
            x -= 1.0/Math.sqrt(m.distanceSquaredTo(home));
            //s += " h " + (-50.0/Math.sqrt(m.distanceSquaredTo(home)))+" e";
            for(RobotInfo r : enemies) {
                switch(r.type) {
                    case SOLDIER:
                    case SAGE:
                    case WATCHTOWER:
                        x -= 100.0/m.distanceSquaredTo(r.location);
                        //s += " " + (-100.0/m.distanceSquaredTo(r.location));
                        break;
                    default:
                }
            }
            //s += " a";
            for(int j=0;j<8;j++) {
                MapLocation m2 = recentLocations[(recentLocationsIndex+10-j)%10];
                if(m2 == null) continue;
                if(m2!= null && (m2.isAdjacentTo(m) || m2.equals(m))) {
                    x -= 10;
                    //s += " -10";
                }
            }
            /*
            boolean left = m.x < 10;
            boolean right = m.x + 10 > mapWidth;
            boolean bottom = m.y < 10;
            boolean top = m.y + 10 > mapHeight;
            if(m.x < 4) {
                x -= .2 * (4 - m.x);
            } else if(m.x + 4 > mapWidth) {
                x -= .2 * (m.x + 4 - mapWidth);
            }
            if(m.y < 4) {
                x -= .2 * (4);
            } else if(m.y + 4 > mapHeight) {
                x -= .2 * (m.y + 4 - mapHeight);
            }
            if(m.x < 4 && m.y < 4) {
                x -= 10 * (10 - m.x);
                x -= 10 * (10 - m.y);
            }
            if(m.x < 4 && m.y + 4 > mapHeight) {
                x -= 10 * (10 - m.x);
                x -= 10 * (m.y + 10 - mapHeight);
            }
            if(m.x + 4 > mapWidth && m.y < 4) {
                x -= 10 * (m.x + 10 - mapWidth);
                x -= 10 * (10 - m.y);
            }
            if(m.x + 4 > mapWidth && m.y + 4 > mapHeight) {
                x -= 10 * (m.x + 10 - mapWidth);
                x -= 10 * (m.y + 10 - mapHeight);
            }
            */
            //if(i==3 && rc.getID() == 13202) rc.setIndicatorString(s);
            //if(rc.getRoundNum()==70) rc.resign();
            suitability[i] = (int)(10 * (100 + y + x - 2.0/rubbleMult));
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
