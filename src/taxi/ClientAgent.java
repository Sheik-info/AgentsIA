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

import java.io.IOException;

/**
 * Agent representant un client.
 * Comportement simple : il cherche tous les taxis disponibles, choisit
 * celui qui est le plus proche (distance minimale) et monte a bord.
 * S'il n'y a aucun taxi disponible, ou si la place lui echappe
 * (taxi complet entre-temps), il recommence la recherche un peu plus tard.
 * Une fois a bord, il attend simplement la notification d'arrivee.
 */
public class ClientAgent extends Agent {
    // Constants of the environment
    private static final long RETRY_DELAY = 5000;

    /**
     *  Believes B:
     * - startWaypoint
     * - maxDistanceIncrease
     */
    // static (random) initial believes
    private Point2D startWaypoint;
    private double maxDistanceIncrease;

    /**
     *  Intentions I:
     * - endX, endY
     */
    private Point2D endWaypoint;

    private void initializeBeliefs() {
        startWaypoint = new Point2D(
            Math.random() * 100,
            Math.random() * 100
        );
        maxDistanceIncrease = 1.0 + Math.random();
    }

    private void initializeIntentions() {
        endWaypoint = new Point2D(
            Math.random() * 100,
            Math.random() * 100
        );
    }

    protected void setup() {
        /**
         *  Initial believes B0:
         * - startWaypoint
         * - maxDistanceIncrease
         */
        initializeBeliefs();

        /**
         *  Initial believes I0:
         * - endWaypoint
         */
        initializeIntentions();

        System.out.println("Client "+getLocalName()+" created at position "
            + startWaypoint.toString()
            + " with the intention to go at destination "
            + endWaypoint.toString());

        addBehaviour(new BookingPerformer());
    }

    protected void takeDown() {
        System.out.println("Client "+getLocalName()+" has finished its trip.");
    }

    /**
     Comportement qui recherche le taxi le plus proche, reserve une place
     et attend la notification d'arrivee a destination.
     */
    private class BookingPerformer extends Behaviour {
        private AID[] taxiAgents;
        private ACLMessage bestTaxiReply;
        private double bestScore;
        private int repliesCnt = 0;
        private MessageTemplate mt;
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Discover taxis and send CPF
                    try {
                        requestTaxi();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case 1:
                    // Collect offers and chose the best one (on the fly)
                    try {
                        collectOffers();
                    } catch (UnreadableException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case 2:
                    // Accept the best taxi offer (if at least 1 offer received)
                    try {
                        confirmTaxi();
                    } catch (UnreadableException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case 3:
                    // Enter when the taxi informs of its arrival to client start point
                    enterTaxi();
                    break;

                case 4:
                    // Enter when the taxi informs of its arrival to client destination point
                    exitTaxi();
                    break;
            }
        }

        public boolean done() {
            return step == 5;
        }

        public void requestTaxi() throws IOException {
            // Search the service 'taxi-service' on which the taxis have been registered on creation -> discover the existing taxis
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("taxi-service");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                taxiAgents = new AID[result.length];
                for (int i = 0; i < result.length; ++i) {
                    taxiAgents[i] = result[i].getName();
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
                taxiAgents = new AID[0];
            }

            // No taxi discovered -> retry later
            if (taxiAgents.length == 0) {
                block(RETRY_DELAY);
            }
            else {
                ClientRequest request = new ClientRequest(
                    startWaypoint,
                    endWaypoint,
                    maxDistanceIncrease
                );

                // Send CFP to all discovered taxis
                ACLMessage cfpClient = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < taxiAgents.length; ++i) {
                    cfpClient.addReceiver(taxiAgents[i]);
                }
                cfpClient.setConversationId("taxi-ride");
                cfpClient.setReplyWith("cfp"+System.currentTimeMillis());
                cfpClient.setContentObject(request);
                myAgent.send(cfpClient);

                // Bind for the taxis replies (replies to the exact cfp message)
                mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("taxi-ride"),
                    MessageTemplate.MatchInReplyTo(cfpClient.getReplyWith()));
                bestTaxiReply = null;
                bestScore = Double.MAX_VALUE;
                repliesCnt = 0;
                step = 1;
            }
        }

        public void collectOffers() throws UnreadableException {
            ACLMessage proposeTaxiReply = myAgent.receive(mt);
            if (proposeTaxiReply != null) {
                if (proposeTaxiReply.getPerformative() == ACLMessage.PROPOSE) {
                    TaxiOffer offer = (TaxiOffer) proposeTaxiReply.getContentObject();
                    double score = taxiOfferHeuristic(offer);
                    if (bestTaxiReply == null || score < bestScore) {   // lowest is best
                        if (bestTaxiReply != null){
                            // Refuse the former offer that is no longer the best
                            ACLMessage rejectFormerBestClientReply = bestTaxiReply.createReply();
                            rejectFormerBestClientReply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                            rejectFormerBestClientReply.setContent("no-longer-best-offer ("
                                + String.format("%.1f", score)
                                + " [new: " + proposeTaxiReply.getSender().getLocalName() + "] < [former] "
                                + String.format("%.1f", bestScore) + ")");
                            myAgent.send(rejectFormerBestClientReply);
                        }
                        bestScore = score;
                        bestTaxiReply = proposeTaxiReply;
                    }
                    else {
                        // Refuse the offers that are not the best
                        ACLMessage rejectClientReply = proposeTaxiReply.createReply();
                        rejectClientReply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        rejectClientReply.setContent("not-best-offer ("
                            + String.format("%.1f", bestScore)
                            + " [former: " + bestTaxiReply.getSender().getLocalName() + "] < [new] "
                            + String.format("%.1f", score) + ")");
                        myAgent.send(rejectClientReply);
                    }
                }
                else if (proposeTaxiReply.getPerformative() == ACLMessage.REFUSE) {
                    System.out.println("Taxi "+proposeTaxiReply.getSender().getLocalName()
                        + " refused CFP from client " + getLocalName()
                        +" for reason: "+ proposeTaxiReply.getContent());
                    // Taxi refused the offer
                }
                repliesCnt++;
                System.out.println("Client " + getLocalName() + " received " + repliesCnt + "/" + taxiAgents.length + " replies to its CFP");

                if (repliesCnt >= taxiAgents.length) {  // Condition for stoping to look for new offers (all received or out-of-time fallback)
                    step = 2;
                }
            }
            else {
                block();  // Continue looking for the rest of the offers
            }
        }

        public void confirmTaxi() throws UnreadableException, IOException {
            if (bestTaxiReply == null) {
                // No taxi available -> retry later
                step = 0;
                block(RETRY_DELAY);
            }
            else {
                // Remind the trip waypoints the to taxi and the estimated duration
                // (avoids memory bottleneck: if each taxi had to store the waypoints
                // of each client at CFP time, that would cause a O(T*C) memory complexity)
                TaxiOffer offer = (TaxiOffer) bestTaxiReply.getContentObject();
                ClientAcceptance acceptance = new ClientAcceptance(
                    startWaypoint,
                    endWaypoint,
                    offer.getEstimatedDuration()
                );

                // Confirm offer acceptance to the best found taxi
                ACLMessage acceptClientReply = bestTaxiReply.createReply();
                acceptClientReply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                acceptClientReply.setContentObject(acceptance);
                myAgent.send(acceptClientReply);

                // Wait for the taxi to inform of its arrival at departure (and then later of its arrival at destination)
                // The ride is already confirmed -> just identify the taxi
                mt = MessageTemplate.and(
                        MessageTemplate.and(
                            MessageTemplate.MatchConversationId("taxi-ride"),
                            MessageTemplate.MatchSender(bestTaxiReply.getSender())),
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM));
                step = 3;
            }
        }

        public void enterTaxi(){
            ACLMessage entrance = myAgent.receive(mt);
            if ((entrance != null) && entrance.getContent().equals("entrance")){
                System.out.println("Client "+getLocalName()+" entered the taxi "+ entrance.getSender().getLocalName());

                // After entering the taxi, wait for the taxi to inform of its arrival at destination (same message template)
                step = 4;
            }
            else {
                block();  // Taxi not arrived yet at client start point
            }
        }

        public void exitTaxi(){
            ACLMessage exit = myAgent.receive(mt);
            if ((exit != null) && exit.getContent().equals("exit")){
                System.out.println("Client "+getLocalName()+" exit the taxi "+ exit.getSender().getLocalName());

                // Remove the client agent after its final arrival at destination
                myAgent.doDelete();
                step = 5;
            }
            else {
                block();  // Taxi not arrived yet at client destination
            }
        }

    }

    public double taxiOfferHeuristic(TaxiOffer offer){
        double hourlyPrice = offer.getHourlyPrice();
        double distance = offer.getEstimatedDistance();
        double duration = offer.getEstimatedDuration();

        return hourlyPrice * Math.pow(duration, 1.5);
    }
}
