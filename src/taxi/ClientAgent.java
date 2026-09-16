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

    // Delai entre deux tentatives si aucun taxi n'est disponible / trouve (ms)
    private static final long RETRY_DELAY = 5000;

    // Position du client sur la meme grille virtuelle que les taxis
    private double posX, posY;

    protected void setup() {
        posX = Math.random() * 100;
        posY = Math.random() * 100;

        System.out.println("Client "+getLocalName()+" pret. Position ("+
                String.format("%.1f", posX)+","+String.format("%.1f", posY)+")");

        addBehaviour(new BookingPerformer());
    }

    protected void takeDown() {
        System.out.println("Client "+getLocalName()+" a termine son trajet.");
    }

    /**
     Comportement qui recherche le taxi le plus proche, reserve une place
     et attend la notification d'arrivee a destination.
     */
    private class BookingPerformer extends Behaviour {
        private AID[] taxiAgents;
        private AID bestTaxi;
        private double bestDistance;
        private int repliesCnt = 0;
        private MessageTemplate mt;
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Recherche des agents taxi actuellement enregistres
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

                    if (taxiAgents.length == 0) {
                        // Aucun taxi enregistre pour le moment : nouvelle tentative plus tard
                        block(RETRY_DELAY);
                    }
                    else {
                        // Demande de course envoyee a tous les taxis connus
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        for (int i = 0; i < taxiAgents.length; ++i) {
                            cfp.addReceiver(taxiAgents[i]);
                        }
                        cfp.setContent(posX+","+posY);
                        cfp.setConversationId("taxi-ride");
                        cfp.setReplyWith("cfp"+System.currentTimeMillis());
                        myAgent.send(cfp);

                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("taxi-ride"),
                                MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                        bestTaxi = null;
                        repliesCnt = 0;
                        step = 1;
                    }
                    break;

                case 1:
                    // Reception des propositions (distance) ou refus des taxis
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            double dist = Double.parseDouble(reply.getContent());
                            if (bestTaxi == null || dist < bestDistance) {
                                bestDistance = dist;
                                bestTaxi = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= taxiAgents.length) {
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;

                case 2:
                    if (bestTaxi == null) {
                        // Tous les taxis sont occupes ou complets : nouvelle tentative plus tard
                        step = 0;
                        block(RETRY_DELAY);
                    }
                    else {
                        // Reservation d'une place dans le taxi le plus proche
                        ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        order.addReceiver(bestTaxi);
                        order.setContent(posX+","+posY);
                        order.setConversationId("taxi-ride");
                        order.setReplyWith("order"+System.currentTimeMillis());
                        myAgent.send(order);

                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("taxi-ride"),
                                MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                        step = 3;
                    }
                    break;

                case 3:
                    // Reponse du taxi a la demande d'embarquement
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println("Client "+getLocalName()+" est monte dans le taxi "+
                                    reply.getSender().getLocalName());
                            // On attend desormais le message d'arrivee de ce taxi
                            mt = MessageTemplate.and(
                                    MessageTemplate.MatchSender(reply.getSender()),
                                    MessageTemplate.MatchPerformative(ACLMessage.INFORM));
                            step = 4;
                        }
                        else {
                            // Le taxi n'a plus de place disponible : on recommence la recherche
                            System.out.println("Client "+getLocalName()+
                                    " : place perdue, nouvelle recherche de taxi");
                            step = 0;
                        }
                    }
                    else {
                        block();
                    }
                    break;

                case 4:
                    // Attente de la notification d'arrivee a destination
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        System.out.println("Client "+getLocalName()+" est arrive a destination");
                        myAgent.doDelete();
                        step = 5;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            return step == 5;
        }
    }  // Fin de BookingPerformer
}