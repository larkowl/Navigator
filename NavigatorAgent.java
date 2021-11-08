import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class NavigatorAgent extends Agent{
    public static final String START = "start";
    public static final String WAMPUS = "wampus";
    public static final String PIT = "pit";
    public static final String BREEZE = "breeze";
    public static final String STENCH = "stench";
    public static final String SCREAM = "scream";
    public static final String GOLD = "gold";
    public static final String BUMP = "bump";

    public static int TRUE = 1;
    public static int FALSE = 2;
    public static int POSSIBLE = 3;
    public static int NO_STATUS = -1;

    private static final String SERVICE_DESCRIPTION = "NAVIGATOR_AGENT";
    private Position agents_coords;
    Hashtable<Position, ArrayList<Position>> made_steps;
    Hashtable<Position, Integer> okCount;
    Hashtable<Position, Boolean> finish;

    ImaginaryWampusWorld world;

    Boolean gold_found = false, wampus_died = false;

    @Override
    protected void setup() {
        world = new ImaginaryWampusWorld();
        agents_coords = new Position();
        made_steps = new Hashtable<>();
        okCount = new Hashtable<>();
        finish = new Hashtable<>();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SpeleologistAgent.NAVIGATOR_AGENT_TYPE);
        sd.setName(SERVICE_DESCRIPTION);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new LocationRequestsServer());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Navigator-agent "+getAID().getName()+" terminating.");
    }

    private class LocationRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                if (agents_coords == null){
                    agents_coords = new Position();
                }
                String location = msg.getContent();

                location = location.substring(1, location.length()-1);
                String[] room_info = location.split(", ");

                if (location.equals("cream")) {
                    System.out.println("SCREAM! WAMPUS DIED!");
                    wampus_died = true;
                }

                if (gold_found && wampus_died) {
                    System.out.println("SUCCESS!");
                    System.exit(0);
                }

                System.out.println("\n\n");
                System.out.println("Wampus returned current room info: " + Arrays.toString(room_info));
                System.out.println("Current agent coordinates: " + agents_coords.getX() + " " + agents_coords.getY());
                String[] actions = getNextAction(room_info);
                ACLMessage reply = msg.createReply();

                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(Arrays.toString(actions));
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    private String[] getNextAction(String[] room_info) {
        int[] actions;

        ImaginaryRoom checking_room = world.getWorldGrid().get(agents_coords);
        if (checking_room == null)
        {
            checking_room = new ImaginaryRoom();
            world.getWorldGrid().put(agents_coords, checking_room);
        }

        for (String event: room_info){
            checking_room.addEvent(event);
        }
        checking_room.updateFeelingsInfo();

        boolean has_wampus = updateNeighbors(agents_coords);

        if ( checking_room.getGold() == NavigatorAgent.TRUE && !gold_found ) {
            gold_found = true;
            actions = new int[] {SpeleologistAgent.TAKE_GOLD};
        }
        else if (has_wampus && !wampus_died) {
            actions = new int[0];
            Position[] okNeighbors = getNeighborsPosition();
            for (Position position: okNeighbors){
                if (!position.isValid() )
                    continue;

                if ( ( this.world.getWorldGrid().get(position).getWampus() == NavigatorAgent.TRUE ) ) {
                    System.out.println("Arrow shoot!");
                    actions = getNextRoomAction(position, SpeleologistAgent.SHOOT_ARROW);
                    break;
                }
            }
        }
        else {
            Position[] nextOkRooms = getOkNeighbors();
            if ( nextOkRooms.length == 0 )
                System.exit(0);

            Position best_candidate = nextOkRooms[0];
            System.out.println("Count of possible moves: " + nextOkRooms.length);
            System.out.println("Step coordinate: " + best_candidate.getX() + " | " + best_candidate.getY());
            actions = getNextRoomAction(best_candidate, SpeleologistAgent.MOVE);
        }

        String[] language_actions = new String[actions.length];
        for (int i = 0; i < actions.length; ++i){
            language_actions[i] = SpeleologistAgent.actionCodes.get(actions[i]);
        }
        return language_actions;
    }

    private int[] getNextRoomAction(Position nextOkRoom, int action) {
        int look;
        if (agents_coords.getY() < nextOkRoom.getY()) {
            look = SpeleologistAgent.LOOK_UP;
        } else if (agents_coords.getY() > nextOkRoom.getY()) {
            look = SpeleologistAgent.LOOK_DOWN;
        } else if (agents_coords.getX() < nextOkRoom.getX()) {
            look = SpeleologistAgent.LOOK_RIGHT;
        } else {
            look = SpeleologistAgent.LOOK_LEFT;
        }

        if (action == SpeleologistAgent.MOVE) {
            Position temp = new Position(agents_coords.getX(), agents_coords.getY());
            made_steps.computeIfAbsent(temp, k -> new ArrayList<>());
            made_steps.get(temp).add( nextOkRoom );

            if ( made_steps.get(temp).size() == okCount.get(temp) ) // если количество сделанных переходов == количество ОК соседей
                finish.put(temp, true);

            agents_coords.setY( nextOkRoom.getY() );
            agents_coords.setX( nextOkRoom.getX() );
        }
        return new int[] {look, action};
    }

    private Position[] getOkNeighbors() {
        Position[] okNeighbors = getNeighborsPosition();
        ArrayList<Position> okPositions = new ArrayList<>();

        for (Position position: okNeighbors){
            if (!position.isValid() || finish.get(position) != null ||
                    (made_steps.get(agents_coords) != null && made_steps.get(agents_coords).contains(position)) )
                continue;

            if ( ( this.world.getWorldGrid().get(position).getOk() == NavigatorAgent.TRUE ) )
                okPositions.add(position);
        }

        okCount.computeIfAbsent(agents_coords, k -> okPositions.size());
        return okPositions.toArray(new Position[0]);
    }

    private ImaginaryRoom getNeighborImaginaryRoom(Position pos) {
        ImaginaryRoom room = world.getWorldGrid().get(pos);
        if (room == null && pos.isValid()){
            room = new ImaginaryRoom();
            world.getWorldGrid().put(pos, room);
        }
        return room;
    }

    private ArrayList<ImaginaryRoom> getNeighborsImaginaryRoom(){
        ArrayList<ImaginaryRoom> rooms = new ArrayList<>();
        int[][] steps = new int[][] { {0, 1}, {0, -1}, {1, 0}, {-1, 0} };

        for (int[] step : steps) {
            ImaginaryRoom room = getNeighborImaginaryRoom( new Position(agents_coords.getX() + step[0],
                                                                        agents_coords.getY() + step[1] ) );
            if (room != null)
                rooms.add(room);
        }
        return rooms;
    }

    private Position[] getNeighborsPosition(){
        Position rightNeighbor = new Position(agents_coords.getX() + 1, agents_coords.getY());
        Position upNeighbor = new Position(agents_coords.getX(), agents_coords.getY() + 1);
        Position leftNeighbor = new Position(agents_coords.getX() - 1, agents_coords.getY());
        Position bottomNeighbor = new Position(agents_coords.getX(), agents_coords.getY() - 1);
        return new Position[]{ rightNeighbor, upNeighbor, leftNeighbor, bottomNeighbor };
    }

    private boolean updateNeighbors(Position request_agent_position) {
        ImaginaryRoom currentRoom = world.getWorldGrid().get(request_agent_position);
        ArrayList<ImaginaryRoom> roomList = getNeighborsImaginaryRoom();

        int ok = 0;
        for (ImaginaryRoom room: roomList) {
            if ( room.getOk() == NavigatorAgent.TRUE || room.getWampus() == NavigatorAgent.FALSE)
                ok++;
            if ( room.getOk() == NavigatorAgent.TRUE)
                continue;

            if (    currentRoom.getOk()     == NavigatorAgent.TRUE &&
                    currentRoom.getBreeze() != NavigatorAgent.TRUE &&
                    currentRoom.getStench() != NavigatorAgent.TRUE ) {
                room.setOk(NavigatorAgent.TRUE);
            }

            if ( currentRoom.getBreeze() == NavigatorAgent.FALSE && currentRoom.getStench() == NavigatorAgent.FALSE ) {
                room.setOk(NavigatorAgent.TRUE);
            }

            if (currentRoom.getBreeze() == NavigatorAgent.FALSE) {
                room.setPit(NavigatorAgent.FALSE);
            }
            if (currentRoom.getStench() == NavigatorAgent.FALSE) {
                room.setWampus(NavigatorAgent.FALSE);
            }

            if (currentRoom.getStench() == NavigatorAgent.TRUE) {
                if (room.getWampus() == NavigatorAgent.NO_STATUS){
                    room.setOk(NavigatorAgent.POSSIBLE);
                    room.setWampus(NavigatorAgent.POSSIBLE);
                }
            }
            if (currentRoom.getBreeze() == NavigatorAgent.TRUE){
                if (room.getPit() == NavigatorAgent.NO_STATUS){
                    room.setOk(NavigatorAgent.POSSIBLE);
                    room.setPit(NavigatorAgent.POSSIBLE);
                }
            }

            room.updateStatusInfo();
        }

        if (currentRoom.getStench() == NavigatorAgent.TRUE && ok + 1 == roomList.size()) {
            for (ImaginaryRoom room: roomList) {
                if ( room.getOk() != NavigatorAgent.TRUE && room.getWampus() != FALSE ) {
                    room.setWampus( NavigatorAgent.TRUE );
                }
            }
            return true;
        }

        return false;
    }
}

class ImaginaryWampusWorld {

    private final Hashtable<Position, ImaginaryRoom> worldGrid;

    ImaginaryWampusWorld(){
        worldGrid = new Hashtable<>();
        ImaginaryRoom start = new ImaginaryRoom();
        start.setOk(NavigatorAgent.TRUE);
        worldGrid.put( new Position(0, 0), start);
    }

    public Hashtable<Position, ImaginaryRoom> getWorldGrid() {
        return worldGrid;
    }
}

class ImaginaryRoom {
    private int stench;
    private int breeze;
    private int pit;
    private int wampus;
    private int ok;
    private int gold;

    public ImaginaryRoom() {
        this.stench = NavigatorAgent.NO_STATUS;
        this.breeze = NavigatorAgent.NO_STATUS;
        this.pit = NavigatorAgent.NO_STATUS;
        this.wampus = NavigatorAgent.NO_STATUS;
        this.ok = NavigatorAgent.NO_STATUS;
        this.gold = NavigatorAgent.NO_STATUS;
    }
    public void addEvent(String event_name){
        switch (event_name){
            case NavigatorAgent.START:
            case NavigatorAgent.SCREAM:
            case NavigatorAgent.BUMP:
                break;
            case NavigatorAgent.WAMPUS:
                this.setWampus(NavigatorAgent.TRUE);
                break;
            case NavigatorAgent.PIT:
                this.setPit(NavigatorAgent.TRUE);
                break;
            case NavigatorAgent.BREEZE:
                this.setBreeze(NavigatorAgent.TRUE);
                break;
            case NavigatorAgent.STENCH:
                this.setStench(NavigatorAgent.TRUE);
                break;
            case NavigatorAgent.GOLD:
                this.setGold(NavigatorAgent.TRUE);
                break;
        }
    }

    public void updateFeelingsInfo() {
        if (stench == NavigatorAgent.NO_STATUS)
            stench = NavigatorAgent.FALSE;
        if (breeze == NavigatorAgent.NO_STATUS)
            breeze = NavigatorAgent.FALSE;
    }

    public void updateStatusInfo() {
        if (wampus == NavigatorAgent.FALSE && pit == NavigatorAgent.FALSE)
            ok = NavigatorAgent.TRUE;
    }

    public int getStench() {
        return stench;
    }

    public void setStench(int stench) {
        this.stench = stench;
    }

    public int getBreeze() {
        return breeze;
    }

    public void setBreeze(int breeze) {
        this.breeze = breeze;
    }

    public int getPit() {
        return pit;
    }

    public void setPit(int pit) {
        this.pit = pit;
    }

    public int getWampus() {
        return wampus;
    }

    public void setWampus(int wampus) {
        this.wampus = wampus;
    }

    public int getOk() {
        return ok;
    }

    public void setOk(int ok) {
        this.ok = ok;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

}
class Position {
    private int x;
    private int y;
    Position(){
        this.x = 0;
        this.y = 0;
    }
    Position(int x, int y){
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        Position position = (Position)obj;
        return this.x == position.getX() && this.y == position.getY();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    public boolean isValid() { return x >= 0 && x < 4 && y >= 0 && y < 4; }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}