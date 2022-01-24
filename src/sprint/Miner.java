package sprint;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import static battlecode.common.RobotType.MINER;

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
        if (mine()) return;
        //boolean shouldDoSuitability = false;
        if(rc.isMovementReady() && (rc.getRoundNum() - lastSuitabilityRound < 10)) {
            movement();
        } else {
            lastSuitabilityRound = rc.getRoundNum();
            planMovement();
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
        MapLocation[] golds = rc.senseNearbyLocationsWithGold(
                MINER.visionRadiusSquared);

        if(golds.length > 0) {
            moveToward(golds[0]);
            return;
        }

        boolean[][] hasNearbyMiner = new boolean[11][11];
        RobotInfo[] nearby = rc.senseNearbyRobots(MINER.visionRadiusSquared, rc.getTeam());
        RobotInfo nearest = null;
        int myx=rc.getLocation().x;
        int myy=rc.getLocation().y;
        for(RobotInfo r:nearby) {
            if(r.type== MINER) {
                if(nearest==null || rc.getLocation().distanceSquaredTo(nearest.location) > rc.getLocation().distanceSquaredTo(r.location))
                    nearest=r;
                int x=r.location.x-myx;
                int y=r.location.y-myy;
                /*
                hasNearbyMiner[x+4][y+4] = true;
                hasNearbyMiner[x+6][y+4] = true;
                hasNearbyMiner[x+5][y+4] = true;
                hasNearbyMiner[x+4][y+5] = true;
                hasNearbyMiner[x+6][y+5] = true;
                hasNearbyMiner[x+5][y+5] = true;
                hasNearbyMiner[x+4][y+6] = true;
                hasNearbyMiner[x+6][y+6] = true;
                hasNearbyMiner[x+5][y+6] = true;
                */
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

        for(int i=0;i<9;i++) if(adjacentLead[i]>0) adjacentLead[i]+=1000;
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
            MapLocation[] pbLocs = rc.senseNearbyLocationsWithLead(MINER.visionRadiusSquared,12);
            boolean[] ignorablePb = new boolean[pbLocs.length];
            for(int i=0;i<pbLocs.length;i++) {
                MapLocation pb = pbLocs[i];
                if(rc.getLocation().distanceSquaredTo(pb) < 9) {
                    ignorablePb[i] = true;
                    continue;
                }
                for(RobotInfo r : nearby) {
                    if(r.type == MINER) {
                        if(r.location.distanceSquaredTo(pb) < rc.getLocation().distanceSquaredTo(pb)) {
                            //ignorablePb[i] = false;
                            break;
                        }
                    }
                }
            }
            for(int i=0;i<pbLocs.length;i++) {
                if(!ignorablePb[i]) {
                    moveToward(pbLocs[i]);
                    return;
                }
            }
            MapLocation nearestEnemy = getNearestEnemyChunk();
            if(nearestEnemy != null && rc.getLocation().distanceSquaredTo(nearestEnemy) < 36) {
                Direction d = nearestEnemy.directionTo(rc.getLocation());
                //super.moveInDirection(d);
                //return;
            }
            frustration = 1;
            moveToward(null);
        }
    }
    //sets target to either be a nearby
    private void setMovementCostsFromLocation10(MapLocation m) {
        int myx = rc.getLocation().x,myy = rc.getLocation().y;
        switch((m.x-myx)*13+(m.y-myy)) {
        case -54:c25+=990;c29+=830;c52+=870;c5c+=470;c92+=550;c9c+=150;cc5+=190;cc9+=30;c26+=980;c28+=900;c62+=820;c6c+=420;c82+=660;c8c+=260;cc6+=180;cc8+=100;c27+=950;c34+=990;c3a+=750;c43+=950;c4b+=630;c72+=750;c7c+=350;ca3+=470;cab+=150;cb4+=350;cba+=110;cc7+=150;break;
        case -50:c25+=830;c29+=990;c52+=470;c5c+=870;c92+=150;c9c+=550;cc5+=30;cc9+=190;c26+=900;c28+=980;c62+=420;c6c+=820;c82+=260;c8c+=660;cc6+=100;cc8+=180;c27+=950;c34+=750;c3a+=990;c43+=630;c4b+=950;c72+=350;c7c+=750;ca3+=150;cab+=470;cb4+=110;cba+=350;cc7+=150;break;
        case -30:c25+=870;c29+=550;c52+=990;c5c+=190;c92+=830;c9c+=30;cc5+=470;cc9+=150;c26+=820;c28+=660;c62+=980;c6c+=180;c82+=900;c8c+=100;cc6+=420;cc8+=260;c27+=750;c34+=950;c3a+=470;c43+=990;c4b+=350;c72+=950;c7c+=150;ca3+=750;cab+=110;cb4+=630;cba+=150;cc7+=350;break;
        case -22:c25+=550;c29+=870;c52+=190;c5c+=990;c92+=30;c9c+=830;cc5+=150;cc9+=470;c26+=660;c28+=820;c62+=180;c6c+=980;c82+=100;c8c+=900;cc6+=260;cc8+=420;c27+=750;c34+=470;c3a+=950;c43+=350;c4b+=990;c72+=150;c7c+=950;ca3+=110;cab+=750;cb4+=150;cba+=630;cc7+=350;break;
        case 22:c25+=470;c29+=150;c52+=830;c5c+=30;c92+=990;c9c+=190;cc5+=870;cc9+=550;c26+=420;c28+=260;c62+=900;c6c+=100;c82+=980;c8c+=180;cc6+=820;cc8+=660;c27+=350;c34+=630;c3a+=150;c43+=750;c4b+=110;c72+=950;c7c+=150;ca3+=990;cab+=350;cb4+=950;cba+=470;cc7+=750;break;
        case 30:c25+=150;c29+=470;c52+=30;c5c+=830;c92+=190;c9c+=990;cc5+=550;cc9+=870;c26+=260;c28+=420;c62+=100;c6c+=900;c82+=180;c8c+=980;cc6+=660;cc8+=820;c27+=350;c34+=150;c3a+=630;c43+=110;c4b+=750;c72+=150;c7c+=950;ca3+=350;cab+=990;cb4+=470;cba+=950;cc7+=750;break;
        case 50:c25+=190;c29+=30;c52+=550;c5c+=150;c92+=870;c9c+=470;cc5+=990;cc9+=830;c26+=180;c28+=100;c62+=660;c6c+=260;c82+=820;c8c+=420;cc6+=980;cc8+=900;c27+=150;c34+=350;c3a+=110;c43+=470;c4b+=150;c72+=750;c7c+=350;ca3+=950;cab+=630;cb4+=990;cba+=750;cc7+=950;break;
        case 54:c25+=30;c29+=190;c52+=150;c5c+=550;c92+=470;c9c+=870;cc5+=830;cc9+=990;c26+=100;c28+=180;c62+=260;c6c+=660;c82+=420;c8c+=820;cc6+=900;cc8+=980;c27+=150;c34+=110;c3a+=350;c43+=150;c4b+=470;c72+=350;c7c+=750;ca3+=630;cab+=950;cb4+=750;cba+=990;cc7+=950;break;
        case -42:c25+=950;c29+=710;c52+=950;c5c+=350;c92+=710;c9c+=110;cc5+=350;cc9+=110;c26+=920;c28+=800;c62+=920;c6c+=320;c82+=800;c8c+=200;cc6+=320;cc8+=200;c27+=870;c34+=990;c3a+=630;c43+=990;c4b+=510;c72+=870;c7c+=270;ca3+=630;cab+=150;cb4+=510;cba+=150;cc7+=270;break;
        case -36:c25+=710;c29+=950;c52+=350;c5c+=950;c92+=110;c9c+=710;cc5+=110;cc9+=350;c26+=800;c28+=920;c62+=320;c6c+=920;c82+=200;c8c+=800;cc6+=200;cc8+=320;c27+=870;c34+=630;c3a+=990;c43+=510;c4b+=990;c72+=270;c7c+=870;ca3+=150;cab+=630;cb4+=150;cba+=510;cc7+=270;break;
        case 36:c25+=350;c29+=110;c52+=710;c5c+=110;c92+=950;c9c+=350;cc5+=950;cc9+=710;c26+=320;c28+=200;c62+=800;c6c+=200;c82+=920;c8c+=320;cc6+=920;cc8+=800;c27+=270;c34+=510;c3a+=150;c43+=630;c4b+=150;c72+=870;c7c+=270;ca3+=990;cab+=510;cb4+=990;cba+=630;cc7+=870;break;
        case 42:c25+=110;c29+=350;c52+=110;c5c+=710;c92+=350;c9c+=950;cc5+=710;cc9+=950;c26+=200;c28+=320;c62+=200;c6c+=800;c82+=320;c8c+=920;cc6+=800;cc8+=920;c27+=270;c34+=150;c3a+=510;c43+=150;c4b+=630;c72+=270;c7c+=870;ca3+=510;cab+=990;cb4+=630;cba+=990;cc7+=870;break;
        case -53:c25+=980;c29+=900;c52+=800;c5c+=600;c92+=480;c9c+=280;cc5+=180;cc9+=100;c26+=990;c28+=950;c62+=750;c6c+=550;c82+=590;c8c+=390;cc6+=190;cc8+=150;c27+=980;c34+=960;c3a+=840;c43+=900;c4b+=740;c72+=680;c7c+=480;ca3+=420;cab+=260;cb4+=320;cba+=200;cc7+=180;break;
        case -51:c25+=900;c29+=980;c52+=600;c5c+=800;c92+=280;c9c+=480;cc5+=100;cc9+=180;c26+=950;c28+=990;c62+=550;c6c+=750;c82+=390;c8c+=590;cc6+=150;cc8+=190;c27+=980;c34+=840;c3a+=960;c43+=740;c4b+=900;c72+=480;c7c+=680;ca3+=260;cab+=420;cb4+=200;cba+=320;cc7+=180;break;
        case -17:c25+=800;c29+=480;c52+=980;c5c+=180;c92+=900;c9c+=100;cc5+=600;cc9+=280;c26+=750;c28+=590;c62+=990;c6c+=190;c82+=950;c8c+=150;cc6+=550;cc8+=390;c27+=680;c34+=900;c3a+=420;c43+=960;c4b+=320;c72+=980;c7c+=180;ca3+=840;cab+=200;cb4+=740;cba+=260;cc7+=480;break;
        case -9:c25+=480;c29+=800;c52+=180;c5c+=980;c92+=100;c9c+=900;cc5+=280;cc9+=600;c26+=590;c28+=750;c62+=190;c6c+=990;c82+=150;c8c+=950;cc6+=390;cc8+=550;c27+=680;c34+=420;c3a+=900;c43+=320;c4b+=960;c72+=180;c7c+=980;ca3+=200;cab+=840;cb4+=260;cba+=740;cc7+=480;break;
        case 9:c25+=600;c29+=280;c52+=900;c5c+=100;c92+=980;c9c+=180;cc5+=800;cc9+=480;c26+=550;c28+=390;c62+=950;c6c+=150;c82+=990;c8c+=190;cc6+=750;cc8+=590;c27+=480;c34+=740;c3a+=260;c43+=840;c4b+=200;c72+=980;c7c+=180;ca3+=960;cab+=320;cb4+=900;cba+=420;cc7+=680;break;
        case 17:c25+=280;c29+=600;c52+=100;c5c+=900;c92+=180;c9c+=980;cc5+=480;cc9+=800;c26+=390;c28+=550;c62+=150;c6c+=950;c82+=190;c8c+=990;cc6+=590;cc8+=750;c27+=480;c34+=260;c3a+=740;c43+=200;c4b+=840;c72+=180;c7c+=980;ca3+=320;cab+=960;cb4+=420;cba+=900;cc7+=680;break;
        case 51:c25+=180;c29+=100;c52+=480;c5c+=280;c92+=800;c9c+=600;cc5+=980;cc9+=900;c26+=190;c28+=150;c62+=590;c6c+=390;c82+=750;c8c+=550;cc6+=990;cc8+=950;c27+=180;c34+=320;c3a+=200;c43+=420;c4b+=260;c72+=680;c7c+=480;ca3+=900;cab+=740;cb4+=960;cba+=840;cc7+=980;break;
        case 53:c25+=100;c29+=180;c52+=280;c5c+=480;c92+=600;c9c+=800;cc5+=900;cc9+=980;c26+=150;c28+=190;c62+=390;c6c+=590;c82+=550;c8c+=750;cc6+=950;cc8+=990;c27+=180;c34+=200;c3a+=320;c43+=260;c4b+=420;c72+=480;c7c+=680;ca3+=740;cab+=900;cb4+=840;cba+=960;cc7+=980;break;
        case -52:c25+=950;c29+=950;c52+=710;c5c+=710;c92+=390;c9c+=390;cc5+=150;cc9+=150;c26+=980;c28+=980;c62+=660;c6c+=660;c82+=500;c8c+=500;cc6+=180;cc8+=180;c27+=990;c34+=910;c3a+=910;c43+=830;c4b+=830;c72+=590;c7c+=590;ca3+=350;cab+=350;cb4+=270;cba+=270;cc7+=190;break;
        case -4:c25+=710;c29+=390;c52+=950;c5c+=150;c92+=950;c9c+=150;cc5+=710;cc9+=390;c26+=660;c28+=500;c62+=980;c6c+=180;c82+=980;c8c+=180;cc6+=660;cc8+=500;c27+=590;c34+=830;c3a+=350;c43+=910;c4b+=270;c72+=990;c7c+=190;ca3+=910;cab+=270;cb4+=830;cba+=350;cc7+=590;break;
        case 4:c25+=390;c29+=710;c52+=150;c5c+=950;c92+=150;c9c+=950;cc5+=390;cc9+=710;c26+=500;c28+=660;c62+=180;c6c+=980;c82+=180;c8c+=980;cc6+=500;cc8+=660;c27+=590;c34+=350;c3a+=830;c43+=270;c4b+=910;c72+=190;c7c+=990;ca3+=270;cab+=910;cb4+=350;cba+=830;cc7+=590;break;
        case 52:c25+=150;c29+=150;c52+=390;c5c+=390;c92+=710;c9c+=710;cc5+=950;cc9+=950;c26+=180;c28+=180;c62+=500;c6c+=500;c82+=660;c8c+=660;cc6+=980;cc8+=980;c27+=190;c34+=270;c3a+=270;c43+=350;c4b+=350;c72+=590;c7c+=590;ca3+=830;cab+=830;cb4+=910;cba+=910;cc7+=990;break;
        case -41:c25+=960;c29+=800;c52+=900;c5c+=500;c92+=660;c9c+=260;cc5+=360;cc9+=200;c26+=950;c28+=870;c62+=870;c6c+=470;c82+=750;c8c+=350;cc6+=350;cc8+=270;c27+=920;c34+=980;c3a+=740;c43+=960;c4b+=640;c72+=820;c7c+=420;ca3+=600;cab+=280;cb4+=500;cba+=260;cc7+=320;break;
        case -37:c25+=800;c29+=960;c52+=500;c5c+=900;c92+=260;c9c+=660;cc5+=200;cc9+=360;c26+=870;c28+=950;c62+=470;c6c+=870;c82+=350;c8c+=750;cc6+=270;cc8+=350;c27+=920;c34+=740;c3a+=980;c43+=640;c4b+=960;c72+=420;c7c+=820;ca3+=280;cab+=600;cb4+=260;cba+=500;cc7+=320;break;
        case -29:c25+=900;c29+=660;c52+=960;c5c+=360;c92+=800;c9c+=200;cc5+=500;cc9+=260;c26+=870;c28+=750;c62+=950;c6c+=350;c82+=870;c8c+=270;cc6+=470;cc8+=350;c27+=820;c34+=960;c3a+=600;c43+=980;c4b+=500;c72+=920;c7c+=320;ca3+=740;cab+=260;cb4+=640;cba+=280;cc7+=420;break;
        case -23:c25+=660;c29+=900;c52+=360;c5c+=960;c92+=200;c9c+=800;cc5+=260;cc9+=500;c26+=750;c28+=870;c62+=350;c6c+=950;c82+=270;c8c+=870;cc6+=350;cc8+=470;c27+=820;c34+=600;c3a+=960;c43+=500;c4b+=980;c72+=320;c7c+=920;ca3+=260;cab+=740;cb4+=280;cba+=640;cc7+=420;break;
        case 23:c25+=500;c29+=260;c52+=800;c5c+=200;c92+=960;c9c+=360;cc5+=900;cc9+=660;c26+=470;c28+=350;c62+=870;c6c+=270;c82+=950;c8c+=350;cc6+=870;cc8+=750;c27+=420;c34+=640;c3a+=280;c43+=740;c4b+=260;c72+=920;c7c+=320;ca3+=980;cab+=500;cb4+=960;cba+=600;cc7+=820;break;
        case 29:c25+=260;c29+=500;c52+=200;c5c+=800;c92+=360;c9c+=960;cc5+=660;cc9+=900;c26+=350;c28+=470;c62+=270;c6c+=870;c82+=350;c8c+=950;cc6+=750;cc8+=870;c27+=420;c34+=280;c3a+=640;c43+=260;c4b+=740;c72+=320;c7c+=920;ca3+=500;cab+=980;cb4+=600;cba+=960;cc7+=820;break;
        case 37:c25+=360;c29+=200;c52+=660;c5c+=260;c92+=900;c9c+=500;cc5+=960;cc9+=800;c26+=350;c28+=270;c62+=750;c6c+=350;c82+=870;c8c+=470;cc6+=950;cc8+=870;c27+=320;c34+=500;c3a+=260;c43+=600;c4b+=280;c72+=820;c7c+=420;ca3+=960;cab+=640;cb4+=980;cba+=740;cc7+=920;break;
        case 41:c25+=200;c29+=360;c52+=260;c5c+=660;c92+=500;c9c+=900;cc5+=800;cc9+=960;c26+=270;c28+=350;c62+=350;c6c+=750;c82+=470;c8c+=870;cc6+=870;cc8+=950;c27+=320;c34+=260;c3a+=500;c43+=280;c4b+=600;c72+=420;c7c+=820;ca3+=640;cab+=960;cb4+=740;cba+=980;cc7+=920;break;
        case -40:c25+=950;c29+=870;c52+=830;c5c+=630;c92+=590;c9c+=390;cc5+=350;cc9+=270;c26+=960;c28+=920;c62+=800;c6c+=600;c82+=680;c8c+=480;cc6+=360;cc8+=320;c27+=950;c34+=950;c3a+=830;c43+=910;c4b+=750;c72+=750;c7c+=550;ca3+=550;cab+=390;cb4+=470;cba+=350;cc7+=350;break;
        case -38:c25+=870;c29+=950;c52+=630;c5c+=830;c92+=390;c9c+=590;cc5+=270;cc9+=350;c26+=920;c28+=960;c62+=600;c6c+=800;c82+=480;c8c+=680;cc6+=320;cc8+=360;c27+=950;c34+=830;c3a+=950;c43+=750;c4b+=910;c72+=550;c7c+=750;ca3+=390;cab+=550;cb4+=350;cba+=470;cc7+=350;break;
        case -16:c25+=830;c29+=590;c52+=950;c5c+=350;c92+=870;c9c+=270;cc5+=630;cc9+=390;c26+=800;c28+=680;c62+=960;c6c+=360;c82+=920;c8c+=320;cc6+=600;cc8+=480;c27+=750;c34+=910;c3a+=550;c43+=950;c4b+=470;c72+=950;c7c+=350;ca3+=830;cab+=350;cb4+=750;cba+=390;cc7+=550;break;
        case -10:c25+=590;c29+=830;c52+=350;c5c+=950;c92+=270;c9c+=870;cc5+=390;cc9+=630;c26+=680;c28+=800;c62+=360;c6c+=960;c82+=320;c8c+=920;cc6+=480;cc8+=600;c27+=750;c34+=550;c3a+=910;c43+=470;c4b+=950;c72+=350;c7c+=950;ca3+=350;cab+=830;cb4+=390;cba+=750;cc7+=550;break;
        case 10:c25+=630;c29+=390;c52+=870;c5c+=270;c92+=950;c9c+=350;cc5+=830;cc9+=590;c26+=600;c28+=480;c62+=920;c6c+=320;c82+=960;c8c+=360;cc6+=800;cc8+=680;c27+=550;c34+=750;c3a+=390;c43+=830;c4b+=350;c72+=950;c7c+=350;ca3+=950;cab+=470;cb4+=910;cba+=550;cc7+=750;break;
        case 16:c25+=390;c29+=630;c52+=270;c5c+=870;c92+=350;c9c+=950;cc5+=590;cc9+=830;c26+=480;c28+=600;c62+=320;c6c+=920;c82+=360;c8c+=960;cc6+=680;cc8+=800;c27+=550;c34+=390;c3a+=750;c43+=350;c4b+=830;c72+=350;c7c+=950;ca3+=470;cab+=950;cb4+=550;cba+=910;cc7+=750;break;
        case 38:c25+=350;c29+=270;c52+=590;c5c+=390;c92+=830;c9c+=630;cc5+=950;cc9+=870;c26+=360;c28+=320;c62+=680;c6c+=480;c82+=800;c8c+=600;cc6+=960;cc8+=920;c27+=350;c34+=470;c3a+=350;c43+=550;c4b+=390;c72+=750;c7c+=550;ca3+=910;cab+=750;cb4+=950;cba+=830;cc7+=950;break;
        case 40:c25+=270;c29+=350;c52+=390;c5c+=590;c92+=630;c9c+=830;cc5+=870;cc9+=950;c26+=320;c28+=360;c62+=480;c6c+=680;c82+=600;c8c+=800;cc6+=920;cc8+=960;c27+=350;c34+=350;c3a+=470;c43+=390;c4b+=550;c72+=550;c7c+=750;ca3+=750;cab+=910;cb4+=830;cba+=950;cc7+=950;break;
        case -39:c25+=920;c29+=920;c52+=740;c5c+=740;c92+=500;c9c+=500;cc5+=320;cc9+=320;c26+=950;c28+=950;c62+=710;c6c+=710;c82+=590;c8c+=590;cc6+=350;cc8+=350;c27+=960;c34+=900;c3a+=900;c43+=840;c4b+=840;c72+=660;c7c+=660;ca3+=480;cab+=480;cb4+=420;cba+=420;cc7+=360;break;
        case -3:c25+=740;c29+=500;c52+=920;c5c+=320;c92+=920;c9c+=320;cc5+=740;cc9+=500;c26+=710;c28+=590;c62+=950;c6c+=350;c82+=950;c8c+=350;cc6+=710;cc8+=590;c27+=660;c34+=840;c3a+=480;c43+=900;c4b+=420;c72+=960;c7c+=360;ca3+=900;cab+=420;cb4+=840;cba+=480;cc7+=660;break;
        case 3:c25+=500;c29+=740;c52+=320;c5c+=920;c92+=320;c9c+=920;cc5+=500;cc9+=740;c26+=590;c28+=710;c62+=350;c6c+=950;c82+=350;c8c+=950;cc6+=590;cc8+=710;c27+=660;c34+=480;c3a+=840;c43+=420;c4b+=900;c72+=360;c7c+=960;ca3+=420;cab+=900;cb4+=480;cba+=840;cc7+=660;break;
        case 39:c25+=320;c29+=320;c52+=500;c5c+=500;c92+=740;c9c+=740;cc5+=920;cc9+=920;c26+=350;c28+=350;c62+=590;c6c+=590;c82+=710;c8c+=710;cc6+=950;cc8+=950;c27+=360;c34+=420;c3a+=420;c43+=480;c4b+=480;c72+=660;c7c+=660;ca3+=840;cab+=840;cb4+=900;cba+=900;cc7+=960;break;
        case -28:c25+=910;c29+=750;c52+=910;c5c+=510;c92+=750;c9c+=350;cc5+=510;cc9+=350;c26+=900;c28+=820;c62+=900;c6c+=500;c82+=820;c8c+=420;cc6+=500;cc8+=420;c27+=870;c34+=950;c3a+=710;c43+=950;c4b+=630;c72+=870;c7c+=470;ca3+=710;cab+=390;cb4+=630;cba+=390;cc7+=470;break;
        case -24:c25+=750;c29+=910;c52+=510;c5c+=910;c92+=350;c9c+=750;cc5+=350;cc9+=510;c26+=820;c28+=900;c62+=500;c6c+=900;c82+=420;c8c+=820;cc6+=420;cc8+=500;c27+=870;c34+=710;c3a+=950;c43+=630;c4b+=950;c72+=470;c7c+=870;ca3+=390;cab+=710;cb4+=390;cba+=630;cc7+=470;break;
        case 24:c25+=510;c29+=350;c52+=750;c5c+=350;c92+=910;c9c+=510;cc5+=910;cc9+=750;c26+=500;c28+=420;c62+=820;c6c+=420;c82+=900;c8c+=500;cc6+=900;cc8+=820;c27+=470;c34+=630;c3a+=390;c43+=710;c4b+=390;c72+=870;c7c+=470;ca3+=950;cab+=630;cb4+=950;cba+=710;cc7+=870;break;
        case 28:c25+=350;c29+=510;c52+=350;c5c+=750;c92+=510;c9c+=910;cc5+=750;cc9+=910;c26+=420;c28+=500;c62+=420;c6c+=820;c82+=500;c8c+=900;cc6+=820;cc8+=900;c27+=470;c34+=390;c3a+=630;c43+=390;c4b+=710;c72+=470;c7c+=870;ca3+=630;cab+=950;cb4+=710;cba+=950;cc7+=870;break;
        case -27:c25+=900;c29+=820;c52+=840;c5c+=640;c92+=680;c9c+=480;cc5+=500;cc9+=420;c26+=910;c28+=870;c62+=830;c6c+=630;c82+=750;c8c+=550;cc6+=510;cc8+=470;c27+=900;c34+=920;c3a+=800;c43+=900;c4b+=740;c72+=800;c7c+=600;ca3+=660;cab+=500;cb4+=600;cba+=480;cc7+=500;break;
        case -25:c25+=820;c29+=900;c52+=640;c5c+=840;c92+=480;c9c+=680;cc5+=420;cc9+=500;c26+=870;c28+=910;c62+=630;c6c+=830;c82+=550;c8c+=750;cc6+=470;cc8+=510;c27+=900;c34+=800;c3a+=920;c43+=740;c4b+=900;c72+=600;c7c+=800;ca3+=500;cab+=660;cb4+=480;cba+=600;cc7+=500;break;
        case -15:c25+=840;c29+=680;c52+=900;c5c+=500;c92+=820;c9c+=420;cc5+=640;cc9+=480;c26+=830;c28+=750;c62+=910;c6c+=510;c82+=870;c8c+=470;cc6+=630;cc8+=550;c27+=800;c34+=900;c3a+=660;c43+=920;c4b+=600;c72+=900;c7c+=500;ca3+=800;cab+=480;cb4+=740;cba+=500;cc7+=600;break;
        case -11:c25+=680;c29+=840;c52+=500;c5c+=900;c92+=420;c9c+=820;cc5+=480;cc9+=640;c26+=750;c28+=830;c62+=510;c6c+=910;c82+=470;c8c+=870;cc6+=550;cc8+=630;c27+=800;c34+=660;c3a+=900;c43+=600;c4b+=920;c72+=500;c7c+=900;ca3+=480;cab+=800;cb4+=500;cba+=740;cc7+=600;break;
        case 11:c25+=640;c29+=480;c52+=820;c5c+=420;c92+=900;c9c+=500;cc5+=840;cc9+=680;c26+=630;c28+=550;c62+=870;c6c+=470;c82+=910;c8c+=510;cc6+=830;cc8+=750;c27+=600;c34+=740;c3a+=500;c43+=800;c4b+=480;c72+=900;c7c+=500;ca3+=920;cab+=600;cb4+=900;cba+=660;cc7+=800;break;
        case 15:c25+=480;c29+=640;c52+=420;c5c+=820;c92+=500;c9c+=900;cc5+=680;cc9+=840;c26+=550;c28+=630;c62+=470;c6c+=870;c82+=510;c8c+=910;cc6+=750;cc8+=830;c27+=600;c34+=500;c3a+=740;c43+=480;c4b+=800;c72+=500;c7c+=900;ca3+=600;cab+=920;cb4+=660;cba+=900;cc7+=800;break;
        case 25:c25+=500;c29+=420;c52+=680;c5c+=480;c92+=840;c9c+=640;cc5+=900;cc9+=820;c26+=510;c28+=470;c62+=750;c6c+=550;c82+=830;c8c+=630;cc6+=910;cc8+=870;c27+=500;c34+=600;c3a+=480;c43+=660;c4b+=500;c72+=800;c7c+=600;ca3+=900;cab+=740;cb4+=920;cba+=800;cc7+=900;break;
        case 27:c25+=420;c29+=500;c52+=480;c5c+=680;c92+=640;c9c+=840;cc5+=820;cc9+=900;c26+=470;c28+=510;c62+=550;c6c+=750;c82+=630;c8c+=830;cc6+=870;cc8+=910;c27+=500;c34+=480;c3a+=600;c43+=500;c4b+=660;c72+=600;c7c+=800;ca3+=740;cab+=900;cb4+=800;cba+=920;cc7+=900;break;
        case -26:c25+=870;c29+=870;c52+=750;c5c+=750;c92+=590;c9c+=590;cc5+=470;cc9+=470;c26+=900;c28+=900;c62+=740;c6c+=740;c82+=660;c8c+=660;cc6+=500;cc8+=500;c27+=910;c34+=870;c3a+=870;c43+=830;c4b+=830;c72+=710;c7c+=710;ca3+=590;cab+=590;cb4+=550;cba+=550;cc7+=510;break;
        case -2:c25+=750;c29+=590;c52+=870;c5c+=470;c92+=870;c9c+=470;cc5+=750;cc9+=590;c26+=740;c28+=660;c62+=900;c6c+=500;c82+=900;c8c+=500;cc6+=740;cc8+=660;c27+=710;c34+=830;c3a+=590;c43+=870;c4b+=550;c72+=910;c7c+=510;ca3+=870;cab+=550;cb4+=830;cba+=590;cc7+=710;break;
        case 2:c25+=590;c29+=750;c52+=470;c5c+=870;c92+=470;c9c+=870;cc5+=590;cc9+=750;c26+=660;c28+=740;c62+=500;c6c+=900;c82+=500;c8c+=900;cc6+=660;cc8+=740;c27+=710;c34+=590;c3a+=830;c43+=550;c4b+=870;c72+=510;c7c+=910;ca3+=550;cab+=870;cb4+=590;cba+=830;cc7+=710;break;
        case 26:c25+=470;c29+=470;c52+=590;c5c+=590;c92+=750;c9c+=750;cc5+=870;cc9+=870;c26+=500;c28+=500;c62+=660;c6c+=660;c82+=740;c8c+=740;cc6+=900;cc8+=900;c27+=510;c34+=550;c3a+=550;c43+=590;c4b+=590;c72+=710;c7c+=710;ca3+=830;cab+=830;cb4+=870;cba+=870;cc7+=910;break;
        case -14:c25+=830;c29+=750;c52+=830;c5c+=630;c92+=750;c9c+=550;cc5+=630;cc9+=550;c26+=840;c28+=800;c62+=840;c6c+=640;c82+=800;c8c+=600;cc6+=640;cc8+=600;c27+=830;c34+=870;c3a+=750;c43+=870;c4b+=710;c72+=830;c7c+=630;ca3+=750;cab+=590;cb4+=710;cba+=590;cc7+=630;break;
        case -12:c25+=750;c29+=830;c52+=630;c5c+=830;c92+=550;c9c+=750;cc5+=550;cc9+=630;c26+=800;c28+=840;c62+=640;c6c+=840;c82+=600;c8c+=800;cc6+=600;cc8+=640;c27+=830;c34+=750;c3a+=870;c43+=710;c4b+=870;c72+=630;c7c+=830;ca3+=590;cab+=750;cb4+=590;cba+=710;cc7+=630;break;
        case 12:c25+=630;c29+=550;c52+=750;c5c+=550;c92+=830;c9c+=630;cc5+=830;cc9+=750;c26+=640;c28+=600;c62+=800;c6c+=600;c82+=840;c8c+=640;cc6+=840;cc8+=800;c27+=630;c34+=710;c3a+=590;c43+=750;c4b+=590;c72+=830;c7c+=630;ca3+=870;cab+=710;cb4+=870;cba+=750;cc7+=830;break;
        case 14:c25+=550;c29+=630;c52+=550;c5c+=750;c92+=630;c9c+=830;cc5+=750;cc9+=830;c26+=600;c28+=640;c62+=600;c6c+=800;c82+=640;c8c+=840;cc6+=800;cc8+=840;c27+=630;c34+=590;c3a+=710;c43+=590;c4b+=750;c72+=630;c7c+=830;ca3+=710;cab+=870;cb4+=750;cba+=870;cc7+=830;break;
        case -13:c25+=800;c29+=800;c52+=740;c5c+=740;c92+=660;c9c+=660;cc5+=600;cc9+=600;c26+=830;c28+=830;c62+=750;c6c+=750;c82+=710;c8c+=710;cc6+=630;cc8+=630;c27+=840;c34+=820;c3a+=820;c43+=800;c4b+=800;c72+=740;c7c+=740;ca3+=680;cab+=680;cb4+=660;cba+=660;cc7+=640;break;
        case -1:c25+=740;c29+=660;c52+=800;c5c+=600;c92+=800;c9c+=600;cc5+=740;cc9+=660;c26+=750;c28+=710;c62+=830;c6c+=630;c82+=830;c8c+=630;cc6+=750;cc8+=710;c27+=740;c34+=800;c3a+=680;c43+=820;c4b+=660;c72+=840;c7c+=640;ca3+=820;cab+=660;cb4+=800;cba+=680;cc7+=740;break;
        case 1:c25+=660;c29+=740;c52+=600;c5c+=800;c92+=600;c9c+=800;cc5+=660;cc9+=740;c26+=710;c28+=750;c62+=630;c6c+=830;c82+=630;c8c+=830;cc6+=710;cc8+=750;c27+=740;c34+=680;c3a+=800;c43+=660;c4b+=820;c72+=640;c7c+=840;ca3+=660;cab+=820;cb4+=680;cba+=800;cc7+=740;break;
        case 13:c25+=600;c29+=600;c52+=660;c5c+=660;c92+=740;c9c+=740;cc5+=800;cc9+=800;c26+=630;c28+=630;c62+=710;c6c+=710;c82+=750;c8c+=750;cc6+=830;cc8+=830;c27+=640;c34+=660;c3a+=660;c43+=680;c4b+=680;c72+=740;c7c+=740;ca3+=800;cab+=800;cb4+=820;cba+=820;cc7+=840;break;
        case 0:c25+=710;c29+=710;c52+=710;c5c+=710;c92+=710;c9c+=710;cc5+=710;cc9+=710;c26+=740;c28+=740;c62+=740;c6c+=740;c82+=740;c8c+=740;cc6+=740;cc8+=740;c27+=750;c34+=750;c3a+=750;c43+=750;c4b+=750;c72+=750;c7c+=750;ca3+=750;cab+=750;cb4+=750;cba+=750;cc7+=750;break;
        }   
    }
    private void setMovementCostsFromLocation2(MapLocation m) {
        int myx = rc.getLocation().x,myy = rc.getLocation().y;
        switch((m.x-myx)*13+(m.y-myy)) {
        case -54:c25+=39;c29+=33;c52+=34;c5c+=18;c92+=22;c9c+=6;cc5+=7;cc9+=1;c26+=39;c28+=36;c62+=32;c6c+=16;c82+=26;c8c+=10;cc6+=7;cc8+=4;c27+=38;c34+=39;c3a+=30;c43+=38;c4b+=25;c72+=30;c7c+=14;ca3+=18;cab+=6;cb4+=14;cba+=4;cc7+=6;break;
        case -50:c25+=33;c29+=39;c52+=18;c5c+=34;c92+=6;c9c+=22;cc5+=1;cc9+=7;c26+=36;c28+=39;c62+=16;c6c+=32;c82+=10;c8c+=26;cc6+=4;cc8+=7;c27+=38;c34+=30;c3a+=39;c43+=25;c4b+=38;c72+=14;c7c+=30;ca3+=6;cab+=18;cb4+=4;cba+=14;cc7+=6;break;
        case -30:c25+=34;c29+=22;c52+=39;c5c+=7;c92+=33;c9c+=1;cc5+=18;cc9+=6;c26+=32;c28+=26;c62+=39;c6c+=7;c82+=36;c8c+=4;cc6+=16;cc8+=10;c27+=30;c34+=38;c3a+=18;c43+=39;c4b+=14;c72+=38;c7c+=6;ca3+=30;cab+=4;cb4+=25;cba+=6;cc7+=14;break;
        case -22:c25+=22;c29+=34;c52+=7;c5c+=39;c92+=1;c9c+=33;cc5+=6;cc9+=18;c26+=26;c28+=32;c62+=7;c6c+=39;c82+=4;c8c+=36;cc6+=10;cc8+=16;c27+=30;c34+=18;c3a+=38;c43+=14;c4b+=39;c72+=6;c7c+=38;ca3+=4;cab+=30;cb4+=6;cba+=25;cc7+=14;break;
        case 22:c25+=18;c29+=6;c52+=33;c5c+=1;c92+=39;c9c+=7;cc5+=34;cc9+=22;c26+=16;c28+=10;c62+=36;c6c+=4;c82+=39;c8c+=7;cc6+=32;cc8+=26;c27+=14;c34+=25;c3a+=6;c43+=30;c4b+=4;c72+=38;c7c+=6;ca3+=39;cab+=14;cb4+=38;cba+=18;cc7+=30;break;
        case 30:c25+=6;c29+=18;c52+=1;c5c+=33;c92+=7;c9c+=39;cc5+=22;cc9+=34;c26+=10;c28+=16;c62+=4;c6c+=36;c82+=7;c8c+=39;cc6+=26;cc8+=32;c27+=14;c34+=6;c3a+=25;c43+=4;c4b+=30;c72+=6;c7c+=38;ca3+=14;cab+=39;cb4+=18;cba+=38;cc7+=30;break;
        case 50:c25+=7;c29+=1;c52+=22;c5c+=6;c92+=34;c9c+=18;cc5+=39;cc9+=33;c26+=7;c28+=4;c62+=26;c6c+=10;c82+=32;c8c+=16;cc6+=39;cc8+=36;c27+=6;c34+=14;c3a+=4;c43+=18;c4b+=6;c72+=30;c7c+=14;ca3+=38;cab+=25;cb4+=39;cba+=30;cc7+=38;break;
        case 54:c25+=1;c29+=7;c52+=6;c5c+=22;c92+=18;c9c+=34;cc5+=33;cc9+=39;c26+=4;c28+=7;c62+=10;c6c+=26;c82+=16;c8c+=32;cc6+=36;cc8+=39;c27+=6;c34+=4;c3a+=14;c43+=6;c4b+=18;c72+=14;c7c+=30;ca3+=25;cab+=38;cb4+=30;cba+=39;cc7+=38;break;
        case -42:c25+=38;c29+=28;c52+=38;c5c+=14;c92+=28;c9c+=4;cc5+=14;cc9+=4;c26+=36;c28+=32;c62+=36;c6c+=12;c82+=32;c8c+=8;cc6+=12;cc8+=8;c27+=34;c34+=39;c3a+=25;c43+=39;c4b+=20;c72+=34;c7c+=10;ca3+=25;cab+=6;cb4+=20;cba+=6;cc7+=10;break;
        case -36:c25+=28;c29+=38;c52+=14;c5c+=38;c92+=4;c9c+=28;cc5+=4;cc9+=14;c26+=32;c28+=36;c62+=12;c6c+=36;c82+=8;c8c+=32;cc6+=8;cc8+=12;c27+=34;c34+=25;c3a+=39;c43+=20;c4b+=39;c72+=10;c7c+=34;ca3+=6;cab+=25;cb4+=6;cba+=20;cc7+=10;break;
        case 36:c25+=14;c29+=4;c52+=28;c5c+=4;c92+=38;c9c+=14;cc5+=38;cc9+=28;c26+=12;c28+=8;c62+=32;c6c+=8;c82+=36;c8c+=12;cc6+=36;cc8+=32;c27+=10;c34+=20;c3a+=6;c43+=25;c4b+=6;c72+=34;c7c+=10;ca3+=39;cab+=20;cb4+=39;cba+=25;cc7+=34;break;
        case 42:c25+=4;c29+=14;c52+=4;c5c+=28;c92+=14;c9c+=38;cc5+=28;cc9+=38;c26+=8;c28+=12;c62+=8;c6c+=32;c82+=12;c8c+=36;cc6+=32;cc8+=36;c27+=10;c34+=6;c3a+=20;c43+=6;c4b+=25;c72+=10;c7c+=34;ca3+=20;cab+=39;cb4+=25;cba+=39;cc7+=34;break;
        case -53:c25+=39;c29+=36;c52+=32;c5c+=24;c92+=19;c9c+=11;cc5+=7;cc9+=4;c26+=39;c28+=38;c62+=30;c6c+=22;c82+=23;c8c+=15;cc6+=7;cc8+=6;c27+=39;c34+=38;c3a+=33;c43+=36;c4b+=29;c72+=27;c7c+=19;ca3+=16;cab+=10;cb4+=12;cba+=8;cc7+=7;break;
        case -51:c25+=36;c29+=39;c52+=24;c5c+=32;c92+=11;c9c+=19;cc5+=4;cc9+=7;c26+=38;c28+=39;c62+=22;c6c+=30;c82+=15;c8c+=23;cc6+=6;cc8+=7;c27+=39;c34+=33;c3a+=38;c43+=29;c4b+=36;c72+=19;c7c+=27;ca3+=10;cab+=16;cb4+=8;cba+=12;cc7+=7;break;
        case -17:c25+=32;c29+=19;c52+=39;c5c+=7;c92+=36;c9c+=4;cc5+=24;cc9+=11;c26+=30;c28+=23;c62+=39;c6c+=7;c82+=38;c8c+=6;cc6+=22;cc8+=15;c27+=27;c34+=36;c3a+=16;c43+=38;c4b+=12;c72+=39;c7c+=7;ca3+=33;cab+=8;cb4+=29;cba+=10;cc7+=19;break;
        case -9:c25+=19;c29+=32;c52+=7;c5c+=39;c92+=4;c9c+=36;cc5+=11;cc9+=24;c26+=23;c28+=30;c62+=7;c6c+=39;c82+=6;c8c+=38;cc6+=15;cc8+=22;c27+=27;c34+=16;c3a+=36;c43+=12;c4b+=38;c72+=7;c7c+=39;ca3+=8;cab+=33;cb4+=10;cba+=29;cc7+=19;break;
        case 9:c25+=24;c29+=11;c52+=36;c5c+=4;c92+=39;c9c+=7;cc5+=32;cc9+=19;c26+=22;c28+=15;c62+=38;c6c+=6;c82+=39;c8c+=7;cc6+=30;cc8+=23;c27+=19;c34+=29;c3a+=10;c43+=33;c4b+=8;c72+=39;c7c+=7;ca3+=38;cab+=12;cb4+=36;cba+=16;cc7+=27;break;
        case 17:c25+=11;c29+=24;c52+=4;c5c+=36;c92+=7;c9c+=39;cc5+=19;cc9+=32;c26+=15;c28+=22;c62+=6;c6c+=38;c82+=7;c8c+=39;cc6+=23;cc8+=30;c27+=19;c34+=10;c3a+=29;c43+=8;c4b+=33;c72+=7;c7c+=39;ca3+=12;cab+=38;cb4+=16;cba+=36;cc7+=27;break;
        case 51:c25+=7;c29+=4;c52+=19;c5c+=11;c92+=32;c9c+=24;cc5+=39;cc9+=36;c26+=7;c28+=6;c62+=23;c6c+=15;c82+=30;c8c+=22;cc6+=39;cc8+=38;c27+=7;c34+=12;c3a+=8;c43+=16;c4b+=10;c72+=27;c7c+=19;ca3+=36;cab+=29;cb4+=38;cba+=33;cc7+=39;break;
        case 53:c25+=4;c29+=7;c52+=11;c5c+=19;c92+=24;c9c+=32;cc5+=36;cc9+=39;c26+=6;c28+=7;c62+=15;c6c+=23;c82+=22;c8c+=30;cc6+=38;cc8+=39;c27+=7;c34+=8;c3a+=12;c43+=10;c4b+=16;c72+=19;c7c+=27;ca3+=29;cab+=36;cb4+=33;cba+=38;cc7+=39;break;
        case -52:c25+=38;c29+=38;c52+=28;c5c+=28;c92+=15;c9c+=15;cc5+=6;cc9+=6;c26+=39;c28+=39;c62+=26;c6c+=26;c82+=20;c8c+=20;cc6+=7;cc8+=7;c27+=39;c34+=36;c3a+=36;c43+=33;c4b+=33;c72+=23;c7c+=23;ca3+=14;cab+=14;cb4+=10;cba+=10;cc7+=7;break;
        case -4:c25+=28;c29+=15;c52+=38;c5c+=6;c92+=38;c9c+=6;cc5+=28;cc9+=15;c26+=26;c28+=20;c62+=39;c6c+=7;c82+=39;c8c+=7;cc6+=26;cc8+=20;c27+=23;c34+=33;c3a+=14;c43+=36;c4b+=10;c72+=39;c7c+=7;ca3+=36;cab+=10;cb4+=33;cba+=14;cc7+=23;break;
        case 4:c25+=15;c29+=28;c52+=6;c5c+=38;c92+=6;c9c+=38;cc5+=15;cc9+=28;c26+=20;c28+=26;c62+=7;c6c+=39;c82+=7;c8c+=39;cc6+=20;cc8+=26;c27+=23;c34+=14;c3a+=33;c43+=10;c4b+=36;c72+=7;c7c+=39;ca3+=10;cab+=36;cb4+=14;cba+=33;cc7+=23;break;
        case 52:c25+=6;c29+=6;c52+=15;c5c+=15;c92+=28;c9c+=28;cc5+=38;cc9+=38;c26+=7;c28+=7;c62+=20;c6c+=20;c82+=26;c8c+=26;cc6+=39;cc8+=39;c27+=7;c34+=10;c3a+=10;c43+=14;c4b+=14;c72+=23;c7c+=23;ca3+=33;cab+=33;cb4+=36;cba+=36;cc7+=39;break;
        case -41:c25+=38;c29+=32;c52+=36;c5c+=20;c92+=26;c9c+=10;cc5+=14;cc9+=8;c26+=38;c28+=34;c62+=34;c6c+=18;c82+=30;c8c+=14;cc6+=14;cc8+=10;c27+=36;c34+=39;c3a+=29;c43+=38;c4b+=25;c72+=32;c7c+=16;ca3+=24;cab+=11;cb4+=20;cba+=10;cc7+=12;break;
        case -37:c25+=32;c29+=38;c52+=20;c5c+=36;c92+=10;c9c+=26;cc5+=8;cc9+=14;c26+=34;c28+=38;c62+=18;c6c+=34;c82+=14;c8c+=30;cc6+=10;cc8+=14;c27+=36;c34+=29;c3a+=39;c43+=25;c4b+=38;c72+=16;c7c+=32;ca3+=11;cab+=24;cb4+=10;cba+=20;cc7+=12;break;
        case -29:c25+=36;c29+=26;c52+=38;c5c+=14;c92+=32;c9c+=8;cc5+=20;cc9+=10;c26+=34;c28+=30;c62+=38;c6c+=14;c82+=34;c8c+=10;cc6+=18;cc8+=14;c27+=32;c34+=38;c3a+=24;c43+=39;c4b+=20;c72+=36;c7c+=12;ca3+=29;cab+=10;cb4+=25;cba+=11;cc7+=16;break;
        case -23:c25+=26;c29+=36;c52+=14;c5c+=38;c92+=8;c9c+=32;cc5+=10;cc9+=20;c26+=30;c28+=34;c62+=14;c6c+=38;c82+=10;c8c+=34;cc6+=14;cc8+=18;c27+=32;c34+=24;c3a+=38;c43+=20;c4b+=39;c72+=12;c7c+=36;ca3+=10;cab+=29;cb4+=11;cba+=25;cc7+=16;break;
        case 23:c25+=20;c29+=10;c52+=32;c5c+=8;c92+=38;c9c+=14;cc5+=36;cc9+=26;c26+=18;c28+=14;c62+=34;c6c+=10;c82+=38;c8c+=14;cc6+=34;cc8+=30;c27+=16;c34+=25;c3a+=11;c43+=29;c4b+=10;c72+=36;c7c+=12;ca3+=39;cab+=20;cb4+=38;cba+=24;cc7+=32;break;
        case 29:c25+=10;c29+=20;c52+=8;c5c+=32;c92+=14;c9c+=38;cc5+=26;cc9+=36;c26+=14;c28+=18;c62+=10;c6c+=34;c82+=14;c8c+=38;cc6+=30;cc8+=34;c27+=16;c34+=11;c3a+=25;c43+=10;c4b+=29;c72+=12;c7c+=36;ca3+=20;cab+=39;cb4+=24;cba+=38;cc7+=32;break;
        case 37:c25+=14;c29+=8;c52+=26;c5c+=10;c92+=36;c9c+=20;cc5+=38;cc9+=32;c26+=14;c28+=10;c62+=30;c6c+=14;c82+=34;c8c+=18;cc6+=38;cc8+=34;c27+=12;c34+=20;c3a+=10;c43+=24;c4b+=11;c72+=32;c7c+=16;ca3+=38;cab+=25;cb4+=39;cba+=29;cc7+=36;break;
        case 41:c25+=8;c29+=14;c52+=10;c5c+=26;c92+=20;c9c+=36;cc5+=32;cc9+=38;c26+=10;c28+=14;c62+=14;c6c+=30;c82+=18;c8c+=34;cc6+=34;cc8+=38;c27+=12;c34+=10;c3a+=20;c43+=11;c4b+=24;c72+=16;c7c+=32;ca3+=25;cab+=38;cb4+=29;cba+=39;cc7+=36;break;
        case -40:c25+=38;c29+=34;c52+=33;c5c+=25;c92+=23;c9c+=15;cc5+=14;cc9+=10;c26+=38;c28+=36;c62+=32;c6c+=24;c82+=27;c8c+=19;cc6+=14;cc8+=12;c27+=38;c34+=38;c3a+=33;c43+=36;c4b+=30;c72+=30;c7c+=22;ca3+=22;cab+=15;cb4+=18;cba+=14;cc7+=14;break;
        case -38:c25+=34;c29+=38;c52+=25;c5c+=33;c92+=15;c9c+=23;cc5+=10;cc9+=14;c26+=36;c28+=38;c62+=24;c6c+=32;c82+=19;c8c+=27;cc6+=12;cc8+=14;c27+=38;c34+=33;c3a+=38;c43+=30;c4b+=36;c72+=22;c7c+=30;ca3+=15;cab+=22;cb4+=14;cba+=18;cc7+=14;break;
        case -16:c25+=33;c29+=23;c52+=38;c5c+=14;c92+=34;c9c+=10;cc5+=25;cc9+=15;c26+=32;c28+=27;c62+=38;c6c+=14;c82+=36;c8c+=12;cc6+=24;cc8+=19;c27+=30;c34+=36;c3a+=22;c43+=38;c4b+=18;c72+=38;c7c+=14;ca3+=33;cab+=14;cb4+=30;cba+=15;cc7+=22;break;
        case -10:c25+=23;c29+=33;c52+=14;c5c+=38;c92+=10;c9c+=34;cc5+=15;cc9+=25;c26+=27;c28+=32;c62+=14;c6c+=38;c82+=12;c8c+=36;cc6+=19;cc8+=24;c27+=30;c34+=22;c3a+=36;c43+=18;c4b+=38;c72+=14;c7c+=38;ca3+=14;cab+=33;cb4+=15;cba+=30;cc7+=22;break;
        case 10:c25+=25;c29+=15;c52+=34;c5c+=10;c92+=38;c9c+=14;cc5+=33;cc9+=23;c26+=24;c28+=19;c62+=36;c6c+=12;c82+=38;c8c+=14;cc6+=32;cc8+=27;c27+=22;c34+=30;c3a+=15;c43+=33;c4b+=14;c72+=38;c7c+=14;ca3+=38;cab+=18;cb4+=36;cba+=22;cc7+=30;break;
        case 16:c25+=15;c29+=25;c52+=10;c5c+=34;c92+=14;c9c+=38;cc5+=23;cc9+=33;c26+=19;c28+=24;c62+=12;c6c+=36;c82+=14;c8c+=38;cc6+=27;cc8+=32;c27+=22;c34+=15;c3a+=30;c43+=14;c4b+=33;c72+=14;c7c+=38;ca3+=18;cab+=38;cb4+=22;cba+=36;cc7+=30;break;
        case 38:c25+=14;c29+=10;c52+=23;c5c+=15;c92+=33;c9c+=25;cc5+=38;cc9+=34;c26+=14;c28+=12;c62+=27;c6c+=19;c82+=32;c8c+=24;cc6+=38;cc8+=36;c27+=14;c34+=18;c3a+=14;c43+=22;c4b+=15;c72+=30;c7c+=22;ca3+=36;cab+=30;cb4+=38;cba+=33;cc7+=38;break;
        case 40:c25+=10;c29+=14;c52+=15;c5c+=23;c92+=25;c9c+=33;cc5+=34;cc9+=38;c26+=12;c28+=14;c62+=19;c6c+=27;c82+=24;c8c+=32;cc6+=36;cc8+=38;c27+=14;c34+=14;c3a+=18;c43+=15;c4b+=22;c72+=22;c7c+=30;ca3+=30;cab+=36;cb4+=33;cba+=38;cc7+=38;break;
        case -39:c25+=36;c29+=36;c52+=29;c5c+=29;c92+=20;c9c+=20;cc5+=12;cc9+=12;c26+=38;c28+=38;c62+=28;c6c+=28;c82+=23;c8c+=23;cc6+=14;cc8+=14;c27+=38;c34+=36;c3a+=36;c43+=33;c4b+=33;c72+=26;c7c+=26;ca3+=19;cab+=19;cb4+=16;cba+=16;cc7+=14;break;
        case -3:c25+=29;c29+=20;c52+=36;c5c+=12;c92+=36;c9c+=12;cc5+=29;cc9+=20;c26+=28;c28+=23;c62+=38;c6c+=14;c82+=38;c8c+=14;cc6+=28;cc8+=23;c27+=26;c34+=33;c3a+=19;c43+=36;c4b+=16;c72+=38;c7c+=14;ca3+=36;cab+=16;cb4+=33;cba+=19;cc7+=26;break;
        case 3:c25+=20;c29+=29;c52+=12;c5c+=36;c92+=12;c9c+=36;cc5+=20;cc9+=29;c26+=23;c28+=28;c62+=14;c6c+=38;c82+=14;c8c+=38;cc6+=23;cc8+=28;c27+=26;c34+=19;c3a+=33;c43+=16;c4b+=36;c72+=14;c7c+=38;ca3+=16;cab+=36;cb4+=19;cba+=33;cc7+=26;break;
        case 39:c25+=12;c29+=12;c52+=20;c5c+=20;c92+=29;c9c+=29;cc5+=36;cc9+=36;c26+=14;c28+=14;c62+=23;c6c+=23;c82+=28;c8c+=28;cc6+=38;cc8+=38;c27+=14;c34+=16;c3a+=16;c43+=19;c4b+=19;c72+=26;c7c+=26;ca3+=33;cab+=33;cb4+=36;cba+=36;cc7+=38;break;
        case -28:c25+=36;c29+=30;c52+=36;c5c+=20;c92+=30;c9c+=14;cc5+=20;cc9+=14;c26+=36;c28+=32;c62+=36;c6c+=20;c82+=32;c8c+=16;cc6+=20;cc8+=16;c27+=34;c34+=38;c3a+=28;c43+=38;c4b+=25;c72+=34;c7c+=18;ca3+=28;cab+=15;cb4+=25;cba+=15;cc7+=18;break;
        case -24:c25+=30;c29+=36;c52+=20;c5c+=36;c92+=14;c9c+=30;cc5+=14;cc9+=20;c26+=32;c28+=36;c62+=20;c6c+=36;c82+=16;c8c+=32;cc6+=16;cc8+=20;c27+=34;c34+=28;c3a+=38;c43+=25;c4b+=38;c72+=18;c7c+=34;ca3+=15;cab+=28;cb4+=15;cba+=25;cc7+=18;break;
        case 24:c25+=20;c29+=14;c52+=30;c5c+=14;c92+=36;c9c+=20;cc5+=36;cc9+=30;c26+=20;c28+=16;c62+=32;c6c+=16;c82+=36;c8c+=20;cc6+=36;cc8+=32;c27+=18;c34+=25;c3a+=15;c43+=28;c4b+=15;c72+=34;c7c+=18;ca3+=38;cab+=25;cb4+=38;cba+=28;cc7+=34;break;
        case 28:c25+=14;c29+=20;c52+=14;c5c+=30;c92+=20;c9c+=36;cc5+=30;cc9+=36;c26+=16;c28+=20;c62+=16;c6c+=32;c82+=20;c8c+=36;cc6+=32;cc8+=36;c27+=18;c34+=15;c3a+=25;c43+=15;c4b+=28;c72+=18;c7c+=34;ca3+=25;cab+=38;cb4+=28;cba+=38;cc7+=34;break;
        case -27:c25+=36;c29+=32;c52+=33;c5c+=25;c92+=27;c9c+=19;cc5+=20;cc9+=16;c26+=36;c28+=34;c62+=33;c6c+=25;c82+=30;c8c+=22;cc6+=20;cc8+=18;c27+=36;c34+=36;c3a+=32;c43+=36;c4b+=29;c72+=32;c7c+=24;ca3+=26;cab+=20;cb4+=24;cba+=19;cc7+=20;break;
        case -25:c25+=32;c29+=36;c52+=25;c5c+=33;c92+=19;c9c+=27;cc5+=16;cc9+=20;c26+=34;c28+=36;c62+=25;c6c+=33;c82+=22;c8c+=30;cc6+=18;cc8+=20;c27+=36;c34+=32;c3a+=36;c43+=29;c4b+=36;c72+=24;c7c+=32;ca3+=20;cab+=26;cb4+=19;cba+=24;cc7+=20;break;
        case -15:c25+=33;c29+=27;c52+=36;c5c+=20;c92+=32;c9c+=16;cc5+=25;cc9+=19;c26+=33;c28+=30;c62+=36;c6c+=20;c82+=34;c8c+=18;cc6+=25;cc8+=22;c27+=32;c34+=36;c3a+=26;c43+=36;c4b+=24;c72+=36;c7c+=20;ca3+=32;cab+=19;cb4+=29;cba+=20;cc7+=24;break;
        case -11:c25+=27;c29+=33;c52+=20;c5c+=36;c92+=16;c9c+=32;cc5+=19;cc9+=25;c26+=30;c28+=33;c62+=20;c6c+=36;c82+=18;c8c+=34;cc6+=22;cc8+=25;c27+=32;c34+=26;c3a+=36;c43+=24;c4b+=36;c72+=20;c7c+=36;ca3+=19;cab+=32;cb4+=20;cba+=29;cc7+=24;break;
        case 11:c25+=25;c29+=19;c52+=32;c5c+=16;c92+=36;c9c+=20;cc5+=33;cc9+=27;c26+=25;c28+=22;c62+=34;c6c+=18;c82+=36;c8c+=20;cc6+=33;cc8+=30;c27+=24;c34+=29;c3a+=20;c43+=32;c4b+=19;c72+=36;c7c+=20;ca3+=36;cab+=24;cb4+=36;cba+=26;cc7+=32;break;
        case 15:c25+=19;c29+=25;c52+=16;c5c+=32;c92+=20;c9c+=36;cc5+=27;cc9+=33;c26+=22;c28+=25;c62+=18;c6c+=34;c82+=20;c8c+=36;cc6+=30;cc8+=33;c27+=24;c34+=20;c3a+=29;c43+=19;c4b+=32;c72+=20;c7c+=36;ca3+=24;cab+=36;cb4+=26;cba+=36;cc7+=32;break;
        case 25:c25+=20;c29+=16;c52+=27;c5c+=19;c92+=33;c9c+=25;cc5+=36;cc9+=32;c26+=20;c28+=18;c62+=30;c6c+=22;c82+=33;c8c+=25;cc6+=36;cc8+=34;c27+=20;c34+=24;c3a+=19;c43+=26;c4b+=20;c72+=32;c7c+=24;ca3+=36;cab+=29;cb4+=36;cba+=32;cc7+=36;break;
        case 27:c25+=16;c29+=20;c52+=19;c5c+=27;c92+=25;c9c+=33;cc5+=32;cc9+=36;c26+=18;c28+=20;c62+=22;c6c+=30;c82+=25;c8c+=33;cc6+=34;cc8+=36;c27+=20;c34+=19;c3a+=24;c43+=20;c4b+=26;c72+=24;c7c+=32;ca3+=29;cab+=36;cb4+=32;cba+=36;cc7+=36;break;
        case -26:c25+=34;c29+=34;c52+=30;c5c+=30;c92+=23;c9c+=23;cc5+=18;cc9+=18;c26+=36;c28+=36;c62+=29;c6c+=29;c82+=26;c8c+=26;cc6+=20;cc8+=20;c27+=36;c34+=34;c3a+=34;c43+=33;c4b+=33;c72+=28;c7c+=28;ca3+=23;cab+=23;cb4+=22;cba+=22;cc7+=20;break;
        case -2:c25+=30;c29+=23;c52+=34;c5c+=18;c92+=34;c9c+=18;cc5+=30;cc9+=23;c26+=29;c28+=26;c62+=36;c6c+=20;c82+=36;c8c+=20;cc6+=29;cc8+=26;c27+=28;c34+=33;c3a+=23;c43+=34;c4b+=22;c72+=36;c7c+=20;ca3+=34;cab+=22;cb4+=33;cba+=23;cc7+=28;break;
        case 2:c25+=23;c29+=30;c52+=18;c5c+=34;c92+=18;c9c+=34;cc5+=23;cc9+=30;c26+=26;c28+=29;c62+=20;c6c+=36;c82+=20;c8c+=36;cc6+=26;cc8+=29;c27+=28;c34+=23;c3a+=33;c43+=22;c4b+=34;c72+=20;c7c+=36;ca3+=22;cab+=34;cb4+=23;cba+=33;cc7+=28;break;
        case 26:c25+=18;c29+=18;c52+=23;c5c+=23;c92+=30;c9c+=30;cc5+=34;cc9+=34;c26+=20;c28+=20;c62+=26;c6c+=26;c82+=29;c8c+=29;cc6+=36;cc8+=36;c27+=20;c34+=22;c3a+=22;c43+=23;c4b+=23;c72+=28;c7c+=28;ca3+=33;cab+=33;cb4+=34;cba+=34;cc7+=36;break;
        case -14:c25+=33;c29+=30;c52+=33;c5c+=25;c92+=30;c9c+=22;cc5+=25;cc9+=22;c26+=33;c28+=32;c62+=33;c6c+=25;c82+=32;c8c+=24;cc6+=25;cc8+=24;c27+=33;c34+=34;c3a+=30;c43+=34;c4b+=28;c72+=33;c7c+=25;ca3+=30;cab+=23;cb4+=28;cba+=23;cc7+=25;break;
        case -12:c25+=30;c29+=33;c52+=25;c5c+=33;c92+=22;c9c+=30;cc5+=22;cc9+=25;c26+=32;c28+=33;c62+=25;c6c+=33;c82+=24;c8c+=32;cc6+=24;cc8+=25;c27+=33;c34+=30;c3a+=34;c43+=28;c4b+=34;c72+=25;c7c+=33;ca3+=23;cab+=30;cb4+=23;cba+=28;cc7+=25;break;
        case 12:c25+=25;c29+=22;c52+=30;c5c+=22;c92+=33;c9c+=25;cc5+=33;cc9+=30;c26+=25;c28+=24;c62+=32;c6c+=24;c82+=33;c8c+=25;cc6+=33;cc8+=32;c27+=25;c34+=28;c3a+=23;c43+=30;c4b+=23;c72+=33;c7c+=25;ca3+=34;cab+=28;cb4+=34;cba+=30;cc7+=33;break;
        case 14:c25+=22;c29+=25;c52+=22;c5c+=30;c92+=25;c9c+=33;cc5+=30;cc9+=33;c26+=24;c28+=25;c62+=24;c6c+=32;c82+=25;c8c+=33;cc6+=32;cc8+=33;c27+=25;c34+=23;c3a+=28;c43+=23;c4b+=30;c72+=25;c7c+=33;ca3+=28;cab+=34;cb4+=30;cba+=34;cc7+=33;break;
        case -13:c25+=32;c29+=32;c52+=29;c5c+=29;c92+=26;c9c+=26;cc5+=24;cc9+=24;c26+=33;c28+=33;c62+=30;c6c+=30;c82+=28;c8c+=28;cc6+=25;cc8+=25;c27+=33;c34+=32;c3a+=32;c43+=32;c4b+=32;c72+=29;c7c+=29;ca3+=27;cab+=27;cb4+=26;cba+=26;cc7+=25;break;
        case -1:c25+=29;c29+=26;c52+=32;c5c+=24;c92+=32;c9c+=24;cc5+=29;cc9+=26;c26+=30;c28+=28;c62+=33;c6c+=25;c82+=33;c8c+=25;cc6+=30;cc8+=28;c27+=29;c34+=32;c3a+=27;c43+=32;c4b+=26;c72+=33;c7c+=25;ca3+=32;cab+=26;cb4+=32;cba+=27;cc7+=29;break;
        case 1:c25+=26;c29+=29;c52+=24;c5c+=32;c92+=24;c9c+=32;cc5+=26;cc9+=29;c26+=28;c28+=30;c62+=25;c6c+=33;c82+=25;c8c+=33;cc6+=28;cc8+=30;c27+=29;c34+=27;c3a+=32;c43+=26;c4b+=32;c72+=25;c7c+=33;ca3+=26;cab+=32;cb4+=27;cba+=32;cc7+=29;break;
        case 13:c25+=24;c29+=24;c52+=26;c5c+=26;c92+=29;c9c+=29;cc5+=32;cc9+=32;c26+=25;c28+=25;c62+=28;c6c+=28;c82+=30;c8c+=30;cc6+=33;cc8+=33;c27+=25;c34+=26;c3a+=26;c43+=27;c4b+=27;c72+=29;c7c+=29;ca3+=32;cab+=32;cb4+=32;cba+=32;cc7+=33;break;
        case 0:c25+=28;c29+=28;c52+=28;c5c+=28;c92+=28;c9c+=28;cc5+=28;cc9+=28;c26+=29;c28+=29;c62+=29;c6c+=29;c82+=29;c8c+=29;cc6+=29;cc8+=29;c27+=30;c34+=30;c3a+=30;c43+=30;c4b+=30;c72+=30;c7c+=30;ca3+=30;cab+=30;cb4+=30;cba+=30;cc7+=30;break;
        }
        }


    MapLocation mu1,mu2,mu3,mu4;
    int planMovementCounter = 0;
    private void planMovement() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(MINER.visionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] nearby = rc.senseNearbyRobots(MINER.visionRadiusSquared, rc.getTeam());
        MapLocation me = rc.getLocation();
        c25=0;c29=0;c52=0;c5c=0;c92=0;c9c=0;cc5=0;cc9=0;c26=0;c28=0;c62=0;c6c=0;c82=0;c8c=0;cc6=0;cc8=0;c27=0;c34=0;c3a=0;c43=0;c4b=0;c72=0;c7c=0;ca3=0;cab=0;cb4=0;cba=0;cc7=0;
        int myx = me.x, myy = me.y;
        for(RobotInfo r : enemies) {
            switch(r.type) {
                case SOLDIER:
                case SAGE:
                case WATCHTOWER:
                    setMovementCostsFromLocation10(r.location);
                default:
            }
        }
        MapLocation mh = rc.getLocation();
        for(int i=0;i<3;i++)
            mh = mh.add(mh.directionTo(home));
        //setMovementCostsFromLocation10(mh);
        //MapLocation recentLoc = recentLocations[(recentLocationsIndex+9)%10];
        for(RobotInfo r : nearby) {
            if(r.type == MINER) {
                setMovementCostsFromLocation2(r.location);
            } else if(r.type == RobotType.ARCHON) {
                setMovementCostsFromLocation2(r.location);
            }
        }
        for(int i=6;i<7;i++) {
            MapLocation m = recentLocations[(recentLocationsIndex+6)%10];
            if(m!=null && me.isWithinDistanceSquared(m, 20))
                setMovementCostsFromLocation2(m);
        }
        final int C = 2;
        MapLocation m;int ux,uy;
        if(planMovementCounter%4==0 || mu1==null) mu1 = super.getNearestUnexploredChunk(rc.getLocation().translate(4,4));m=mu1;
        if(m==null) return;
        ux=m.x-myx;uy=m.y-myy;
        c9c+=C*((ux+-2)*(ux+-2)+(uy+-5)*(uy+-5));
        cc9+=C*((ux+-5)*(ux+-5)+(uy+-2)*(uy+-2));
        c8c+=C*((ux+-1)*(ux+-1)+(uy+-5)*(uy+-5));
        cc8+=C*((ux+-5)*(ux+-5)+(uy+-1)*(uy+-1));
        c7c+=C*((ux+0)*(ux+0)+(uy+-5)*(uy+-5));
        cab+=C*((ux+-3)*(ux+-3)+(uy+-4)*(uy+-4));
        cba+=C*((ux+-4)*(ux+-4)+(uy+-3)*(uy+-3));
        cc7+=C*((ux+-5)*(ux+-5)+(uy+0)*(uy+0));
        if(planMovementCounter%4==1 || mu2==null) mu2 = super.getNearestUnexploredChunk(rc.getLocation().translate(4,-4));m=mu2;
        if(m==null) return;
        ux=m.x-myx;uy=m.y-myy;
        c92+=C*((ux+-2)*(ux+-2)+(uy+5)*(uy+5));
        cc5+=C*((ux+-5)*(ux+-5)+(uy+2)*(uy+2));
        c82+=C*((ux+-1)*(ux+-1)+(uy+5)*(uy+5));
        cc6+=C*((ux+-5)*(ux+-5)+(uy+1)*(uy+1));
        c72+=C*((ux+0)*(ux+0)+(uy+5)*(uy+5));
        ca3+=C*((ux+-3)*(ux+-3)+(uy+4)*(uy+4));
        cb4+=C*((ux+-4)*(ux+-4)+(uy+3)*(uy+3));
        if(planMovementCounter%4==2 || mu3==null) mu3 = super.getNearestUnexploredChunk(rc.getLocation().translate(-4,-4));m=mu3;
        if(m==null) return;
        ux=m.x-myx;uy=m.y-myy;
        c25+=C*((ux+5)*(ux+5)+(uy+2)*(uy+2));
        c52+=C*((ux+2)*(ux+2)+(uy+5)*(uy+5));
        c26+=C*((ux+5)*(ux+5)+(uy+1)*(uy+1));
        c62+=C*((ux+1)*(ux+1)+(uy+5)*(uy+5));
        c34+=C*((ux+4)*(ux+4)+(uy+3)*(uy+3));
        c43+=C*((ux+3)*(ux+3)+(uy+4)*(uy+4));
        if(planMovementCounter%4==3 || mu4==null) mu4 = super.getNearestUnexploredChunk(rc.getLocation().translate(-4,4));m=mu4;
        if(m==null) return;
        ux=m.x-myx;uy=m.y-myy;
        c29+=C*((ux+5)*(ux+5)+(uy+-2)*(uy+-2));
        c5c+=C*((ux+2)*(ux+2)+(uy+-5)*(uy+-5));
        c28+=C*((ux+5)*(ux+5)+(uy+-1)*(uy+-1));
        c6c+=C*((ux+1)*(ux+1)+(uy+-5)*(uy+-5));
        c27+=C*((ux+5)*(ux+5)+(uy+0)*(uy+0));
        c3a+=C*((ux+4)*(ux+4)+(uy+-3)*(uy+-3));
        c4b+=C*((ux+3)*(ux+3)+(uy+-4)*(uy+-4));
        planMovementCounter++;
    }
    private int[] suitability = new int[8];
    private void determineMovementSuitability() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(MINER.visionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] nearby = rc.senseNearbyRobots(MINER.visionRadiusSquared, rc.getTeam());
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

        MapLocation[] pbLocs = rc.senseNearbyLocationsWithLead(MINER.visionRadiusSquared,2);
        boolean[] ignorablePb = new boolean[pbLocs.length];
        for(int i=0;i<pbLocs.length;i++) {
            MapLocation pb = pbLocs[i];
            if(rc.getLocation().distanceSquaredTo(pb) < 9) {
                ignorablePb[i] = true;
                continue;
            }
            for(RobotInfo r : nearby) {
                if(r.type == MINER) {
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
                if(r.type != MINER)
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
    private boolean mine() throws GameActionException {
        MapLocation l = rc.getLocation();
        MapLocation loc;
        boolean minedGold = false;
        while(rc.isActionReady() && rc.senseGold(l)>0) {
            rc.mineGold(l);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 0)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, -1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(0, 1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 0)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, -1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(-1, 1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, -1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }
        while(rc.isActionReady() && rc.canSenseLocation(loc=l.translate(1, 1)) && rc.senseGold(loc)>0) {
            rc.mineGold(loc);
            recentlyMined++;
            minedGold = true;
        }

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
        return minedGold;
    }
}
