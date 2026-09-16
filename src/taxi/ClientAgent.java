package taxi;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

public class ClientAgent extends Agent {
    private Integer[] startPoint;
    private Integer[] endPoint;
    private String travelPoints;
    private AID[] taxiAgents; // The list of known seller agents

    protected void setup() {
        addBehaviour(new TickerBehaviour(this, 60000) {
            protected void onTick() {
                System.out.println("Trying to call taxi from " + startPoint);
                // Update the list of taxi agents (access to directory facilitator)
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("book-selling");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println("Found the following seller agents:");
                    taxiAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        taxiAgents[i] = result[i].getName();
                        System.out.println(taxiAgents[i].getName());
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }

                // Perform the request
                myAgent.addBehaviour(new CallTaxi());
            }
        });
    }

    private class CallTaxi extends Behaviour {
        private AID bestSeller; // The agent who provides the best offer
        private int bestPrice;  // The best offered price
        private int repliesCnt = 0; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;   // State of the agent

        public void action() {
            switch (step) {
                // Seeking for a taxi state -> propose (PR)
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);    // Send the cfp to all taxis
                    for (int i = 0; i < taxiAgents.length; ++i) {
                        cfp.addReceiver(taxiAgents[i]);
                    }
                    cfp.setContent(travelPoints);
                    cfp.setConversationId("taxi-course");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("taxi-course"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                    step = 1;
                    break;

                // Seeking for a taxi state -> read the answers (LR)
                case 1:
                    // Receive all proposals/refusals from seller agents
                    // We received all replies -> compare quickly (on the fly) and conclude the offer
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            TaxiOffer offer = null;
                            try {
                                offer = (TaxiOffer) reply.getContentObject();
                            } catch (UnreadableException e) {
                                throw new RuntimeException(e);
                            }

                            int price = offer.getPrice();
                            int eta = offer.getEta();

                            if (bestSeller == null || price < bestPrice) {
                                // This is the best offer at present
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                    }
                    else {
                        // Create new offer if none has been answered
                        step = 0;
                        break;
                    }


                case 2:
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(travelPoints);
                    order.setConversationId("taxi-course");
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);
                    // Prepare the template to get the purchase order reply
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("taxi-course"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;

                // Transition waiting for the taxi to arrive (T)
                case 3:
                    step = 4;
                    break;

                // On board (B)
                case 4:
                    // Receive the purchase order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Purchase successful. We can terminate
                            System.out.println(" successfully purchased from agent "+reply.getSender().getName());
                            System.out.println("Price = "+bestPrice);
                            myAgent.doDelete();
                        }
                        else {
                            System.out.println("Attempt failed: requested book already sold.");
                        }

                        step = 5;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("Attempt failed: not available for sale");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }
}
