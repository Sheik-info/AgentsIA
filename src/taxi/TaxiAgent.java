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
import jade.lang.acl.UnreadableException;

import java.util.*;
import java.util.List;

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
    private static final int STARTUP_DELAY = 2000;
    private static final int CAPACITY = 4;
    private static final long WAKER_TIME = 5000;    // constant max delay after first client confirmed
    private static final int MAX_AUTONOMY = 4;

    // Taxi statistics for recap
    private int numTrips = 0;
    private int numClients = 0;
    private double moneyMade = 0.0;
    private double distanceMade = 0.0;
    private List<Double> billOfClients = new ArrayList<Double>();

    /**
     *  Believes B:
     * - lastWaypoint
     * - hourlyPrice, speed, autonomy
     * - isTripOngoing, isReloadOngoing
     * - proposedClients, confirmedClients, onboardClients, arrivedClients (all integers)
    */
    private Point2D lastWaypoint;                   // variable (random) initial believes
    private double hourlyPrice, speed, autonomy;          // static (random) initial believes
    private boolean isTripOngoing, isReloadOngoing; // variable state

    private int proposedClients, confirmedClients, onboardClients, arrivedClients;

    /**
     *  Intentions I:
     * - tripDepartureWaypoints, tripDestinationWaypoints, tripClients
     * - nextWaypoint
     */
    private List<Point2D> tripDepartureWaypoints, tripDestinationWaypoints;
    private Point2D nextWaypoint;
    private List<AID> tripClients;

    // Reference vers le compte a rebours en cours, pour pouvoir l'annuler
    // si le taxi se remplit avant l'expiration des 60 secondes
    private DepartureWaker departureWaker;

    private Set<String> activeProposals = new HashSet<>();

    private void initializeBeliefs() {
        lastWaypoint = new Point2D(
            Math.random() * 100,
            Math.random() * 100
        );

        hourlyPrice = 20 + Math.random() * 30;
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
        tripDepartureWaypoints = new ArrayList<Point2D>();
        tripDestinationWaypoints = new ArrayList<Point2D>();
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
         * - hourlyPrice, speed, autonomy
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

        System.out.println("----- Taxi "+getLocalName()+" created at position " + lastWaypoint.toString());

        // Register agent to the service 'taxi-service' (with the DF) on which the clients are communicating with taxis
        registerDFService();

        // Cyclic behaviors (persistant plans)
        // (add a tiny delay before making the agent act behaviors -> let sniffer/introspector agents monitor it)
        addBehaviour(new WakerBehaviour(this, STARTUP_DELAY) {
            @Override
            protected void onWake() {
                addBehaviour(new RequestsServer()); // Receive client request
                addBehaviour(new ConfirmServer()); // Confirm client boarding
            }
        });

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

    private double distance2Points2D(Point2D s, Point2D t){
        double xs = s.getX();
        double ys = s.getY();
        double xt = t.getX();
        double yt = t.getY();
        return Math.sqrt(Math.pow(xs-xt, 2) + Math.pow(ys-yt, 2));
    }

    private double distancePath(List<Point2D> path) {
        double d = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            d += distance2Points2D(path.get(i), path.get(i + 1));
        }
        return d;
    }

    private boolean canProposeClient(){
        boolean isPotentiallyFull = proposedClients>=CAPACITY;
        return ((!isTripOngoing) && (!isPotentiallyFull) && (!isReloadOngoing));
    }

    private long nextDuration(){
        return (int) Math.ceil(distance2Points2D(lastWaypoint, nextWaypoint) / speed);
    }

    public record OfferResult(TaxiOffer taxiOffer, boolean distanceCondition) {}

    private OfferResult createTaxiOffer(ClientRequest clientRequest){
        // Propose the ride to the client only if the trip doesn't extend too much the
        // distance from client departure and arrival point
        // There is a rick that the trip gets extended by another client booking meanwhile
        // but that's the taxi issue since the offer doesn't change
        double estimatedDistance;
        boolean distanceCondition;

        Point2D newClientDeparture = clientRequest.getDeparturePoint();
        Point2D newClientDestination = clientRequest.getDestinationPoint();

        if (confirmedClients == 0){
            estimatedDistance = distance2Points2D(newClientDeparture, newClientDestination);
            distanceCondition = true;
        }
        else {
            Point2D firstClientDestination = tripDestinationWaypoints.getFirst();
            Point2D lastClientDestination = tripDestinationWaypoints.getLast();

            estimatedDistance = distance2Points2D(newClientDeparture, firstClientDestination)
                + distancePath(tripDestinationWaypoints)
                + distance2Points2D(lastClientDestination, newClientDestination);

            double minimalDistance = distance2Points2D(newClientDeparture, newClientDestination);
            distanceCondition = estimatedDistance <= minimalDistance * clientRequest.getMaxDistanceIncrease();
        }

        double estimatedDuration = estimatedDistance / speed;
        TaxiOffer taxiOffer = new TaxiOffer(
                hourlyPrice,
            estimatedDuration,
            estimatedDistance
        );

        return new OfferResult(taxiOffer, distanceCondition);
    }

    private void addConfirmedClient(ACLMessage acceptClientReply) throws UnreadableException {
        confirmedClients += 1;
        tripClients.add(acceptClientReply.getSender());

        ClientAcceptance acceptance = (ClientAcceptance) acceptClientReply.getContentObject();
        tripDepartureWaypoints.add(acceptance.getDeparturePoint());
        tripDestinationWaypoints.add(acceptance.getDestinationPoint());
        billOfClients.add(hourlyPrice*acceptance.getEstimatedDuration());
    }

    /**
     Repond aux CFP envoyes par les clients avec la distance du taxi,
     sauf si le taxi est complet ou deja en course : dans ce cas REFUSE.
     */
    private class RequestsServer extends CyclicBehaviour {
        public void action() {
            // Receive the client CFP
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId("taxi-ride"),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));
            ACLMessage cfpClient = myAgent.receive(mt);

            if (cfpClient != null) {
                ACLMessage proposeTaxiReply = cfpClient.createReply();
                if (canProposeClient()) {
                    try {
                        ClientRequest clientRequest = (ClientRequest) cfpClient.getContentObject();
                        OfferResult offerResult = createTaxiOffer(clientRequest);
                        if (offerResult.distanceCondition()) {
                            proposeTaxiReply.setPerformative(ACLMessage.PROPOSE);
                            proposeTaxiReply.setContentObject(offerResult.taxiOffer());
                            String proposeTaxiReplyWith = "propose"+System.currentTimeMillis();
                            proposeTaxiReply.setReplyWith(proposeTaxiReplyWith);
                            activeProposals.add(proposeTaxiReplyWith);
                            proposedClients += 1;
                        }
                        else {
                            // Path too extended: following the path of the previous clients makes the trip too long for new client
                            proposeTaxiReply.setPerformative(ACLMessage.REFUSE);
                            proposeTaxiReply.setContent("path-too-extended");
                        }
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
            }
            else {
                block();
            }
        }
    }

    /**
     Gere l'embarquement des clients qui acceptent la proposition du taxi.
     */
    private class ConfirmServer extends CyclicBehaviour {
        public void action() {
            // Receive the client Acceptance (or Refusal) of the proposal
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchConversationId("taxi-ride"),
                MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
                ));

            // Check that the message is in response to one of the proposals sent by the taxi
            MessageTemplate mtProposals = null;
            for (String proposalId : activeProposals) {
                if (mtProposals == null){
                    mtProposals = MessageTemplate.MatchInReplyTo(proposalId);
                }
                else {
                    mt = MessageTemplate.or(
                        mtProposals,
                        MessageTemplate.MatchInReplyTo(proposalId)
                    );
                }
            }
            if (mtProposals != null) {
                mt = MessageTemplate.and(mt, mtProposals);
            }
            ACLMessage acceptClientReply = myAgent.receive(mt);

            if ((acceptClientReply != null)){
                activeProposals.remove(acceptClientReply.getInReplyTo());

                if (acceptClientReply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    // Register the confirmed client
                    try {
                        addConfirmedClient(acceptClientReply);
                    } catch (UnreadableException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("+++++ Taxi " + getLocalName() + ": " + acceptClientReply.getSender().getLocalName() +
                            " confirmed reservation. Confirmed clients: (" + tripClients.size() + "/" + CAPACITY + ")");

                    // Starts the countdown before departure when the first client confirms
                    if (confirmedClients == 1) {
                        if (departureWaker == null) {
                            departureWaker = new DepartureWaker(myAgent, WAKER_TIME);
                        }
                        myAgent.addBehaviour(departureWaker);
                    }

                    // Anticipated departure as taxi is already full
                    if (confirmedClients == CAPACITY) {
                        if (departureWaker != null) {
                            myAgent.removeBehaviour(departureWaker);
                        }
                        depart();
                    }
                }
                else if (acceptClientReply.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    proposedClients -= 1;
                    System.out.println("Client "+acceptClientReply.getSender().getLocalName()
                        +" refused Proposal from taxi " + getLocalName()
                        + " for reason: "+ acceptClientReply.getContent());
                    // Taxi offer rejected, client found a best offer
                }
            }
            else {
                block();
            }
        }
    }

    /**
     Comportement de type "reveil" qui declenche le depart du taxi
     si celui-ci n'est pas complet au bout de 60 secondes.
     */
    private class DepartureWaker extends WakerBehaviour {
        public DepartureWaker(Agent a, long timeout) {
            super(a, timeout);
        }
        protected void onWake() {
            depart();
        }
    }

    // Start of a trip
    private void depart() {
        System.out.println("Taxi "+getLocalName()+": starts its trip with "+ confirmedClients+" passager(s) confirmed");
        isTripOngoing = true;
        nextWaypoint = tripDepartureWaypoints.getFirst();
        addBehaviour(new OneClientEntranceBehaviour(this, nextDuration()));
    }

    // Behavior for a single client to enter the taxi
    private class OneClientEntranceBehaviour extends WakerBehaviour {
        public OneClientEntranceBehaviour(Agent a, long timeout) {
            super(a, timeout);
        }
        protected void onWake() {
            ACLMessage entrance = new ACLMessage(ACLMessage.INFORM);
            entrance.addReceiver(tripClients.get(onboardClients));
            entrance.setConversationId("taxi-ride");
            entrance.setContent("entrance");
            myAgent.send(entrance);

            distanceMade += distance2Points2D(lastWaypoint, nextWaypoint);
            onboardClients += 1;
            lastWaypoint = nextWaypoint;
            if (onboardClients == confirmedClients) {
                // All clients are on board -> go to destination waypoints
                nextWaypoint = tripDestinationWaypoints.getFirst();
                addBehaviour(new OneClientExitBehaviour(myAgent, nextDuration()));
            }
            else {
                // Still other clients to seek -> go to the next client departure
                nextWaypoint = tripDepartureWaypoints.get(onboardClients);
                addBehaviour(new OneClientEntranceBehaviour(myAgent, nextDuration()));
            }
        }
    }

    // Behavior for a single client to enter the taxi
    private class OneClientExitBehaviour extends WakerBehaviour {
        public OneClientExitBehaviour(Agent a, long timeout) {
            super(a, timeout);
        }
        protected void onWake() {
            ACLMessage exit = new ACLMessage(ACLMessage.INFORM);
            exit.addReceiver(tripClients.get(arrivedClients));
            exit.setConversationId("taxi-ride");
            exit.setContent("exit");
            myAgent.send(exit);

            moneyMade += billOfClients.get(arrivedClients);
            distanceMade += distance2Points2D(lastWaypoint, nextWaypoint);
            onboardClients -= 1;
            arrivedClients += 1;

            lastWaypoint = nextWaypoint;
            if (arrivedClients == confirmedClients) {
                // All clients are arrived -> end of the trip -> ready for next trip (or fuel reload)
                arrival();
            }
            else {
                // Still other clients to lift -> go to the next clients destination
                nextWaypoint = tripDestinationWaypoints.get(arrivedClients);
                addBehaviour(new OneClientExitBehaviour(myAgent, nextDuration()));
            }
        }
    }

    // End of a trip -> update and possibly reload before next trip
    private void arrival() {
        numTrips += 1;
        numClients += arrivedClients;

        System.out.println("Taxi "+getLocalName()+": finished its trip. New position: " + lastWaypoint.toString() + "\n" +
            "Stats of taxi "+getLocalName()+":\n"
            + "  - Num trips: "+numTrips+"\n"
            + "  - Num total clients: "+numClients+"\n"
            + "  - Total distance made: "+String.format("%.1f", distanceMade)+"\n"
            + "  - Total money made: "+String.format("%.1f", moneyMade));

        proposedClients = 0;
        confirmedClients = 0;
        onboardClients = 0;
        arrivedClients = 0;
        billOfClients = new ArrayList<Double>();
        tripDepartureWaypoints = new ArrayList<Point2D>();
        tripDestinationWaypoints = new ArrayList<Point2D>();
        tripClients = new ArrayList<AID>();

        departureWaker = null;  // Reset the departureWaker to allow creating a new one for the next trip
        isTripOngoing = false;
    }
}