import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class WampusWorldAgent extends Agent {

    public static String SERVICE_DESCRIPTION = "WAMPUS-WORLD";
    private static int START = -1;
    private static int WAMPUS = 1;
    private static int PIT = 2;
    private static int BREEZE = 3;
    private static int STENCH = 4;
    private static int SCREAM = 5;
    private static int GOLD = 6;
    private static int BUMP = 7;
    public static HashMap<Integer, String> roomCodes = new HashMap<Integer, String>() {{
        put(START, NavigatorAgent.START);
        put(WAMPUS, NavigatorAgent.WAMPUS);
        put(PIT, NavigatorAgent.PIT);
        put(BREEZE, NavigatorAgent.BREEZE);
        put(STENCH, NavigatorAgent.STENCH);
        put(SCREAM, NavigatorAgent.SCREAM);
        put(GOLD, NavigatorAgent.GOLD);
        put(BUMP, NavigatorAgent.BUMP);
    }};

    private static int NUM_OF_ROWS = 4;
    private static int NUM_OF_COLUMNS = 4;

    private Room[][] wampusMap;
    private HashMap<AID, Coords> Speleologists;

    String nickname = "WampusWorld";
    AID id = new AID(nickname, AID.ISLOCALNAME);

    @Override
    protected void setup() {
        System.out.println("Hello! WampusWorld-agent " + getAID().getName() + " is ready.");
        Speleologists = new HashMap<>();
        generateMap();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SpeleologistAgent.WAMPUS_WORLD_TYPE);
        sd.setName(SERVICE_DESCRIPTION);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new SpeleologistConnectPerformer());
        addBehaviour(new SpeleologistArrowPerformer());
        addBehaviour(new SpeleologistGoldPerformer());
        addBehaviour(new SpeleologistMovePerformer());
    }

    private void generateMap() {

        this.wampusMap = new Room[][]
                {       { new Room()              , new Room(BREEZE)              , new Room(PIT)   , new Room(BREEZE) },
                        { new Room(STENCH)        , new Room()                    , new Room(BREEZE), new Room()       },
                        { new Room(WAMPUS, STENCH), new Room(BREEZE, STENCH, GOLD), new Room(PIT)   , new Room(BREEZE) },
                        { new Room(STENCH)        , new Room()                    , new Room(BREEZE), new Room(PIT)    } };

    }

    private class SpeleologistConnectPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String message = msg.getContent();
                if (Objects.equals(message, SpeleologistAgent.GO_INSIDE)){
                    AID current_Speleologist = msg.getSender();
                    Speleologists.put(current_Speleologist, new Coords(0, 0));
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent(wampusMap[0][0].events.toString());
                    myAgent.send(reply);
                }
            }
            else {
                block();
            }
        }
    }

    private class SpeleologistArrowPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.SHOOT_ARROW);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.SHOOT_ARROW);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);

                int row = Speleologist_coords.row;
                int column = Speleologist_coords.column;
                String answer = "[";
                System.out.println("Arrow coordinate: " + column + " | " + row);
                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_DOWN))){
                    for (int i = 0; i < row; ++i) {
                        System.out.println("Arrow coordinate: " + column + " | " + i);
                        if (wampusMap[i][column].events.contains(WampusWorldAgent.roomCodes.get(WAMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            break;
                        }
                    }
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_UP))){
                    for (int i = row+1; i < NUM_OF_ROWS; ++i) {
                        System.out.println("Arrow coordinate: " + column + " | " + i);
                        if (wampusMap[i][column].events.contains(WampusWorldAgent.roomCodes.get(WAMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            break;
                        }
                    }
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_LEFT))){
                    for (int i = 0; i < column; ++i) {
                        System.out.println("Arrow coordinate: " + i + " | " + row);
                        if (wampusMap[row][i].events.contains(WampusWorldAgent.roomCodes.get(WAMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            break;
                        }
                    }
                }
                else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_RIGHT))){
                    for (int i = column+1; i < NUM_OF_COLUMNS; ++i) {
                        System.out.println("Arrow coordinate: " + i + " | " + row);
                        if (wampusMap[row][i].events.contains(WampusWorldAgent.roomCodes.get(WAMPUS))){
                            answer = NavigatorAgent.SCREAM;
                            break;
                        }
                    }
                }
                answer += "]";
                reply.setContent(answer);

                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
    private class SpeleologistMovePerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.MOVE);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.MOVE);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);
                System.out.println("World say: Current agent coords: " + Speleologist_coords.row + " | " + Speleologist_coords.column);
                int row = Speleologist_coords.row;
                int column = Speleologist_coords.column;
                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_DOWN))){
                    row -= 1;
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_UP))){
                    row += 1;
                }
                else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_LEFT))){
                    column -=1;
                }
                else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LOOK_RIGHT))){
                    column += 1;
                }
                if (row > -1 && column > -1 && row < NUM_OF_ROWS && column < NUM_OF_COLUMNS){
                    Speleologist_coords.column = column;
                    Speleologist_coords.row = row;
                    reply.setContent(wampusMap[row][column].events.toString());
                }
                else {
                    reply.setContent(String.valueOf(new ArrayList<String>(){{
                        add(NavigatorAgent.BUMP);
                    }}));
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
    private class SpeleologistGoldPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.TAKE_GOLD);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);
                if (Speleologist_coords == null){
                    Speleologists.put(current_Speleologist, new Coords(0, 0));
                }
                else {
                    if (wampusMap[Speleologist_coords.row][Speleologist_coords.column].events.contains(WampusWorldAgent.roomCodes.get(GOLD))){
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(SpeleologistAgent.TAKE_GOLD);
                        reply.setContent(wampusMap[Speleologist_coords.row][Speleologist_coords.column].events.toString());
                        myAgent.send(reply);
                    }
                }
            }
            else {
                block();
            }
        }
    }
}

class Room {
    ArrayList<String> events = new ArrayList<>();
    Room (int... args){
        for (int i: args){
            events.add(WampusWorldAgent.roomCodes.get(i));
        }
    }
}

class Coords {
    int row;
    int column;
    Coords(int row, int column){
        this.row = row;
        this.column = column;
    }
}