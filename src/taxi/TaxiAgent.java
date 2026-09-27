package taxi;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

/**
 * Agent taxi.
 * - Repond aux demandes de course (CFP) des clients avec sa distance.
 * - Accepte les clients tant qu'il reste de la place.
 * - Demarre un compte a rebours de 60s des le premier client monte a bord
 *   le taxi part soit quand il est plein (4 places), soit au bout des 60s,
 *   selon ce qui arrive en premier.
 * - Simule le trajet puis informe les passagers de leur arrivee.
 */
public class TaxiAgent extends Agent {
    // Constants of the environment
    private static final int CAPACITY = 4;
    private static final long WAIT_TIME = 60000;    // constant max delay after first client confirmed
    private static final int MAX_AUTONOMY = 4;

    // Taxi statistics for recap
    private int numTrips = 0;
    private int numClients = 0;
    private double moneyMade = 0.0;
    private double distanceMade = 0.0;

    /**
     *  Believes B:
     * - posX, posY
     * - price, speed, autonomy
     * - isTripOngoing, isReloadOngoing
     * - proposedClients, confirmedClients, onboardClients, arrivedClients (all integers)
    */
    private double posX, posY;                      // variable (random) initial believes
    private double price, speed, autonomy;          // static (random) initial believes
    private boolean isTripOngoing, isReloadOngoing; // variable state

    private int proposedClients, confirmedClients, onboardClients, arrivedClients;

    /**
     *  Intentions I:
     * - tripDepartureWaypoints, tripDestinationWaypoints, tripClients
     */
    private List<Point2D> tripDepartureWaypoints;
    private List<Point2D> tripDestinationWaypoints;
    private List<AID> tripClients;

    // Reference vers le compte a rebours en cours, pour pouvoir l'annuler
    // si le taxi se remplit avant l'expiration des 60 secondes
    private DepartureWaker departureWaker;

    private void initializeBeliefs() {
        posX = Math.random() * 100;
        posY = Math.random() * 100;

        price = 20 + Math.random() * 30;
        speed = 20 + Math.random() * 30;
        autonomy = MAX_AUTONOMY;

        isTripOngoing = false;
        isReloadOngoing = false;

        proposedClients = 0;
        confirmedClients = 0;
        onboardClients = 0;
        arrivedClients = 0;
    }

    private void initializeIntentions() {
        tripWaypoints = new ArrayList<>();
        tripClients = new ArrayList<AID>();
    }

    private void registerDFService(){
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("taxi-service");
        sd.setName("JADE-taxi-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    protected void setup() {
        /**
         *  Initial believes B0:
         * - posX, posY
         * - price, speed, autonomy
         * - isTripOngoing, isReloadOngoing
         * - CAPACITY, WAIT_TIME
         * - proposedClients, confirmedClients, onboardClients, arrivedClients (all integers)
         */
        initializeBeliefs();

        /**
         *  Initial intentions I0:
         * - tripWaypoints, tripClients
         */
        initializeIntentions();

        // Register agent to the service 'taxi-service' (with the DF) on which the clients are communicating with taxis
        registerDFService();

        // Cyclic behaviors (persistant plans)
        addBehaviour(new RequestsServer()); // Receive client request
        addBehaviour(new ConfirmServer()); // Confirm client boarding

        // Seul le taxi lance "manuellement" (sans argument) ouvre la fenetre de
        // configuration TaxiGui, qui permet ensuite de creer le reste des taxis
        // et des clients. Les taxis crees depuis cette fenetre recoivent
        // l'argument "worker" et ne doivent pas rouvrir de fenetre.
        Object[] args = getArguments();
        boolean isWorker = (args != null && args.length > 0 && "worker".equals(args[0]));
        if (!isWorker) {
            TaxiGui gui = new TaxiGui(this);
            gui.showGui();
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Taxi "+getLocalName()+" termine.");
    }

    // Distance euclidienne entre le taxi et une position donnee
    private double distanceTo(double x, double y) {
        double dx = posX - x;
        double dy = posY - y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    private boolean canProposeClient(){
        boolean isFull = proposedClients<CAPACITY;
        return ((!isTripOngoing) && (!isFull) && (!isReloadOngoing));
    }

    private TaxiOffer createTaxiOffer(ClientRequest clientRequest){


    }

    private void addConfirmedClient(ACLMessage acceptClientReply){
        confirmedClients += 1;
        tripClients.add(acceptClientReply.getSender());
        ClientAcceptance acceptance = acceptClientReply.getContentObject();
        tripDepartureWaypoints.add(acceptance.getDeparturePoint());
        tripDestinationWaypoints.add(acceptance.getDestinationPoint());
    }

    /**
     Repond aux CFP envoyes par les clients avec la distance du taxi,
     sauf si le taxi est complet ou deja en course : dans ce cas REFUSE.
     */
    private class RequestsServer extends CyclicBehaviour {
        public void action() {
            // Receives the client CFP
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId("taxi-ride"),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));
            ACLMessage cfpClient = myAgent.receive(mt);

            if (cfpClient != null) {
                ACLMessage proposeTaxiReply = cfpClient.createReply();
                if (canProposeClient()) {
                    try {
                        ClientRequest clientRequest = cfpClient.getContentObject();
                        proposeTaxiReply.setPerformative(ACLMessage.PROPOSE);
                        proposeTaxiReply.setContentObject(createTaxiOffer(clientRequest));
                        proposeTaxiReply.setReplyWith("propose"+System.currentTimeMillis());
                    }
                    catch (Exception e) {
                        proposeTaxiReply.setPerformative(ACLMessage.REFUSE);
                        proposeTaxiReply.setContent("bad-request");
                    }
                }
                else {
                    proposeTaxiReply.setPerformative(ACLMessage.REFUSE);
                    proposeTaxiReply.setContent("full");
                }
                myAgent.send(proposeTaxiReply);
                proposedClients += 1;
            }
            else {
                block();
            }
        }
    }  // Fin de RequestsServer

    /**
     Gere l'embarquement des clients qui acceptent la proposition du taxi.
     */
    private class ConfirmServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.and(
                    MessageTemplate.MatchConversationId("taxi-ride"),
                    MessageTemplate.MatchInReplyTo(proposeTaxiReply.getReplyWith())),
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            ACLMessage acceptClientReply = myAgent.receive(mt);

            if (acceptClientReply != null) {
                addConfirmedClient(acceptClientReply);
                System.out.println("Taxi "+getLocalName()+": "+acceptClientReply.getSender().getLocalName()+
                        " confirmed reservation. Confirmed clients: ("+tripClients.size()+"/"+CAPACITY+")");

                // Demarre le compte a rebours de 60s au premier client
                if (tripClients.size() == 1) {
                    departureWaker = new DepartureWaker(myAgent, WAIT_TIME);
                    myAgent.addBehaviour(departureWaker);
                }

                // Depart immediat si le taxi est complet
                if (tripClients.size() == CAPACITY) {
                    if (departureWaker != null) {
                        myAgent.removeBehaviour(departureWaker);
                    }
                    depart();
                }
                }
                else {
                    // Le taxi s'est rempli ou est parti entre-temps
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("full");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }  // Fin de BoardingServer

    /**
     Comportement de type "reveil" qui declenche le depart du taxi
     si celui-ci n'est pas complet au bout de 60 secondes.
     */
    private class DepartureWaker extends WakerBehaviour {
        public DepartureWaker(Agent a, long timeout) {
            super(a, timeout);
        }
        protected void onWake() {
            if (!passengers.isEmpty() && !busy) {
                System.out.println("Taxi "+getLocalName()+" : delai de 60s ecoule, depart avec "+
                        passengers.size()+" passager(s)");
                depart();
            }
        }
    }

    // Demarre la course : le taxi n'accepte plus de nouveaux clients pendant le trajet
    private void depart() {
        busy = true;
        System.out.println("Taxi "+getLocalName()+" en route avec "+passengers.size()+" passager(s)");
        addBehaviour(new TripBehaviour(this, TRIP_DURATION));
    }

    /**
     Simule la duree du trajet puis notifie les passagers de leur arrivee.
     Le taxi redevient ensuite disponible pour de nouvelles courses.
     */
    private class TripBehaviour extends WakerBehaviour {
        public TripBehaviour(Agent a, long timeout) {
            super(a, timeout);
        }
        protected void onWake() {
            ACLMessage arrived = new ACLMessage(ACLMessage.INFORM);
            for (AID client : passengers) {
                arrived.addReceiver(client);
            }
            arrived.setContent("arrived");
            myAgent.send(arrived);
            System.out.println("Taxi "+getLocalName()+" : "+passengers.size()+
                    " passager(s) arrive(s) a destination");

            // Le taxi se vide, se repositionne et redevient disponible
            passengers.clear();
            busy = false;
            posX = Math.random() * 100;
            posY = Math.random() * 100;
        }
    }
}