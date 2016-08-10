import java.util.ArrayList;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    private int greatestProbeCount = 0;
    private int maxProbesAllowed = 12;
    
    private int zealotCount = 0;
    private int zealotAttackThreshold = 4;
    
    private ArrayList<UnitType> buildOrder;
    private UnitType scheduledBuilding = null;
    private int scheduledBuildingCount = 0;
    private int promisedMinerals = 0;
    
    private int pylonCount = 0;

    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit " + unit.getType());
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
        
        game.enableFlag(1);
        game.setLocalSpeed(30);

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) +". Printing location's region polygon:");
        	for(Position position: baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
        
        buildOrder = new ArrayList<UnitType>();
        buildOrder.add(UnitType.Protoss_Pylon);
        buildOrder.add(UnitType.Protoss_Gateway);
        buildOrder.add(UnitType.Protoss_Pylon);
        buildOrder.add(UnitType.Protoss_Gateway);
        buildOrder.add(UnitType.Protoss_Pylon);
    }

    @Override
    public void onFrame() {
    	
    	UnitHousekeeping();
         
        StartNextBuildInOrder();
        AssignNextBuildInOrderToWorker();
        SignOffOnBuild();
        
        MarshalZealots();

        ManageFood();
     
    }

private void UnitHousekeeping(){

    game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

    StringBuilder units = new StringBuilder("My units:\n");
    
    int probeCount = 0;
    pylonCount = 0;
    zealotCount = 0;
    
    
    //iterate through my units
    for (Unit myUnit : self.getUnits()) {
        units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

        //if there's enough minerals, train an SCV
        if (myUnit.getType() == UnitType.Protoss_Nexus && self.minerals() >= UnitType.Protoss_Probe.mineralPrice()-promisedMinerals && greatestProbeCount < maxProbesAllowed) {
            if(myUnit.isIdle() && (scheduledBuilding == null))
            	myUnit.train(UnitType.Protoss_Probe);
        }

        if (myUnit.getType() == UnitType.Protoss_Gateway && self.minerals() >= UnitType.Protoss_Zealot.mineralPrice()) {
            if(myUnit.isIdle())
            	myUnit.train(UnitType.Protoss_Zealot);
        }
        if (myUnit.getType() == UnitType.Protoss_Forge && self.minerals() >= UpgradeType.Protoss_Ground_Weapons.mineralPrice()) {
            if(myUnit.isIdle())
            	myUnit.upgrade(UpgradeType.Protoss_Ground_Weapons);
        }
        
        if (myUnit.getType() == UnitType.Protoss_Probe)
        	probeCount++;
        
        if (myUnit.getType() == UnitType.Protoss_Zealot)
        	if(myUnit.isCompleted())
        		zealotCount++;
        
        if (myUnit.getType() == UnitType.Protoss_Pylon){
        	if(myUnit.isCompleted())
        		pylonCount++;
        }
        
        //if it's a drone and it's idle, send it to the closest mineral patch
        if (myUnit.getType().isWorker() && myUnit.isIdle()) {
            Unit closestMineral = null;

            //find the closest mineral
            for (Unit neutralUnit : game.neutral().getUnits()) {
                if (neutralUnit.getType().isMineralField()) {
                    if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                        closestMineral = neutralUnit;
                    }
                }
            }

            //if a mineral patch was found, send the drone to gather it
            if (closestMineral != null) {
                myUnit.gather(closestMineral, false);
            }
        }
    }
    if (probeCount > greatestProbeCount)
    	greatestProbeCount = probeCount;	
    
    //draw my units on screen
    game.drawTextScreen(10, 25, units.toString());
}

//Identify the enemy position. If we have enough zealots, attack
private void MarshalZealots(){
    Position basePosition = new Position( (game.mapWidth()- self.getStartLocation().getX())* 32, (game.mapHeight()-self.getStartLocation().getY()) * 32);
    if (zealotCount > zealotAttackThreshold){
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Protoss_Zealot){
			//	myUnit.attack(new Position(enemyBase.getX()* 32, enemyBase.getY() * 32));
				if (myUnit.isIdle())
					if (myUnit.getDistance(basePosition) < 100)
						myUnit.attack(randomPosition());
					else
						myUnit.attack(basePosition);		
			}
		}
    }
}

//Get a valid random position in pixel space
public Position randomPosition(){
	int x = 32*(int)(Math.random() * (float)game.mapWidth());
	int y = 32*(int)(Math.random() * (float)game.mapHeight());
	
	return new Position(x, y);
}
//Build the next object in the build order, if possible
private void StartNextBuildInOrder(){
	//If the build order is not complete
	if (buildOrder.size() > 0){
		//If we are not already building something
		if (scheduledBuilding == null){
			UnitType unitToBuild = buildOrder.get(0);
			
			//If we can currently afford the next building in the order
			if (self.minerals() >= unitToBuild.mineralPrice()){
				//Only build a non-pylon building if we have pylons
				if (unitToBuild == UnitType.Protoss_Pylon || pylonCount > 0){
				scheduledBuilding = unitToBuild;
				promisedMinerals = unitToBuild.mineralPrice();
				//Count the number of buildings of this type currently in existence, so that we can
				//detect when construction starts on this building
				scheduledBuildingCount = 0;
				for (Unit myUnit : self.getUnits()) {
					if (myUnit.getType() == scheduledBuilding){
						scheduledBuildingCount++;
					}
				}
				}
			}
		}
		
	}
}

//If a building is planned, assign to a worker
private void AssignNextBuildInOrderToWorker(){
	if (scheduledBuilding != null)
	{
		for (Unit myUnit : self.getUnits()) {
			if (myUnit.getType() == UnitType.Protoss_Probe){
    			TilePosition buildTile = 
        				getBuildTile(myUnit, scheduledBuilding, self.getStartLocation());
        			//and, if found, send the worker to build it (and leave others alone - break;)
        			if (buildTile != null) {
        				myUnit.build(scheduledBuilding, buildTile);
        				break;
        			}
			}
		}
	}
}

//Detect when construction has started on next building, and set scheduledBuilding to null
private void SignOffOnBuild(){
	if (scheduledBuilding!= null){
	int newCountOfScheduledBuildingType = 0;
	for (Unit myUnit : self.getUnits()) {
		if (myUnit.getType() == scheduledBuilding){
			newCountOfScheduledBuildingType++;
		}
	}
	
	if (newCountOfScheduledBuildingType > scheduledBuildingCount ){
		buildOrder.remove(0);
		scheduledBuilding = null;
		promisedMinerals = 0;
	}
	}
}
 // Returns a suitable TilePosition to build a given building type near 
 // specified TilePosition aroundTile, or null if not found. (builder parameter is our worker)
 public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
 	TilePosition ret = null;
 	int maxDist = buildingType.tileWidth();
 	int stopDist = 40;
 	
 	// Refinery, Assimilator, Extractor
 	if (buildingType.isRefinery()) {
 		for (Unit n : game.neutral().getUnits()) {
 			if ((n.getType() == UnitType.Resource_Vespene_Geyser) && 
 					( Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist ) &&
 					( Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist )
 					) return n.getTilePosition();
 		}
 	}
 	
 	while ((maxDist < stopDist) && (ret == null)) {
 		for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; i++) {
 			for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; j++) {
 				if (game.canBuildHere(new TilePosition(i,j), buildingType)) {
 					// units that are blocking the tile
 					boolean unitsInWay = false;
 					for (Unit u : game.getAllUnits()) {
 						if (u.getID() == builder.getID()) continue;
 						if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) unitsInWay = true;
 					}
 					if (!unitsInWay) {
 						return new TilePosition(i, j);
 					}
 					// creep for Zerg
 					if (buildingType.requiresCreep()) {
 						boolean creepMissing = false;
 						for (int k=i; k<=i+buildingType.tileWidth(); k++) {
 							for (int l=j; l<=j+buildingType.tileHeight(); l++) {
 								if (!game.hasCreep(k, l)) creepMissing = true;
 								break;
 							}
 						}
 						if (creepMissing) continue; 
 					}
 				}
 			}
 		}
 		maxDist += 2;
 	}
 	
 	if (ret == null) game.printf("Unable to find suitable build position for "+buildingType.toString());
 	return ret;
 }
 
 private void ManageFood(){

     if ((self.supplyTotal() - self.supplyUsed() < 4) && (self.minerals() >= 400) && scheduledBuilding == null) {
     	
     	//iterate over units to find a worker
     	for (Unit myUnit : self.getUnits()) {
     		if (myUnit.getType() == UnitType.Protoss_Probe) {
     			//get a nice place to build a supply depot 
     			TilePosition buildTile = 
     				getBuildTile(myUnit, UnitType.Protoss_Pylon, self.getStartLocation());
     			//and, if found, send the worker to build it (and leave others alone - break;)
     			if (buildTile != null) {
     				myUnit.build(UnitType.Protoss_Pylon, buildTile);
     				break;
     			}
     		}
     	}
     }
 }
 
    public static void main(String[] args) {
        new TestBot1().run();
    }
}