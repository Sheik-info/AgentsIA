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
     * - startX, startY
     * - maxDistanceIncrease
     */
    private double startX, startY, maxDistanceIncrease;   // static (random) initial believes

    /**
     *  Intentions I:
     * - endX, endY
     */
    private double endX, endY;

    private void initializeBeliefs() {
        startX = Math.random() * 100;
        startY = Math.random() * 100;

        maxDistanceIncrease = 1.0 + Math.random();
    }

    private void initializeIntentions() {
        endX = Math.random() * 100;
        endY = Math.random() * 100;

        maxDistanceIncrease = 1.0 + Math.random();
    }

    protected void setup() {
        /**
         *  Initial believes B0:
         * - startX, startY
         * - maxDistanceIncrease
         */
        initializeBeliefs();

        /**
         *  Initial believes I0:
         * - endX, endY
         */
        initializeIntentions();

        System.out.println("Client "+getLocalName()+" created at position ("+
                String.format("%.1f", startX)+","+String.format("%.1f", startY)+
                ") with the intention to go at destination ("+
                String.format("%.1f", endX)+","+String.format("%.1f", endY)+")");

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
                    requestTaxi();
                    break;

                case 1:
                    // Collect offers and chose the best one (on the fly)
                    collectOffers();
                    break;

                case 2:
                    // Accept the best taxi offer (if at least 1 offer received)
                    confirmTaxi();
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

        public void requestTaxi(){
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
                    new Point2D(startX, startY),
                    new Point2D(endX, endY),
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
                repliesCnt = 0;
                step = 1;
            }
        }

        public void collectOffers(){
            ACLMessage proposeTaxiReply = myAgent.receive(mt);
            if (proposeTaxiReply != null) {
                if (proposeTaxiReply.getPerformative() == ACLMessage.PROPOSE) {
                    TaxiOffer offer = proposeTaxiReply.getContentObject();
                    double score = taxiOfferHeuristic(offer);
                    if (bestTaxiReply == null || score < bestScore) {
                        bestScore = score;
                        bestTaxiReply = proposeTaxiReply;
                    }
                }
                else if (proposeTaxiReply.getPerformative() == ACLMessage.REFUSE) {} // Taxi refused the offer
                repliesCnt++;

                if (repliesCnt >= taxiAgents.length) {  // Condition for stoping to look for new offers (all received or out-of-time fallback)
                    step = 2;
                }
            }
            else {
                block();  // Continue looking for the rest of the offers
            }
        }

        public void confirmTaxi(){
            if (bestTaxiReply == null) {
                // No taxi available -> retry later
                step = 0;
                block(RETRY_DELAY);
            }
            else {
                ClientAcceptance acceptance = new ClientAcceptance(
                    new Point2D(startX, startY),
                    new Point2D(endX, endY)
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
            ACLMessage ... = myAgent.receive(mt);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.INFORM) {
                    System.out.println("Client "+getLocalName()+" entered the taxi "+ reply.getSender().getLocalName());

                    // After entering the taxi, wait for the taxi to inform of its arrival at destination (same message template)
                    step = 4;
                }
            }
            else {
                block();  // Taxi not arrived yet at client start point
            }
        }

        public void exitTaxi(){
            reply = myAgent.receive(mt);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.INFORM) {
                    System.out.println("Client " + getLocalName() + " arrived at destination");

                    // Remove the client agent after its final arrival at destination
                    myAgent.doDelete();
                    step = 5;
                }
            }
            else {
                block();  // Taxi not arrived yet at client destination
            }
        }

    }  // Fin de BookingPerformer

    public double taxiOfferHeuristic(TaxiOffer offer){
        double price = offer.getPrice();
        double distance = offer.getEstimatedDistance();
        double duration = offer.getEstimatedDuration();

        return price * Math.pow(duration, 1.5);
    }
}
