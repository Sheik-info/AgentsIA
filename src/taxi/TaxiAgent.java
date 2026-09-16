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

    // Nombre maximal de places dans le taxi
    private static final int CAPACITY = 4;
    // Delai maximal d'attente avant le depart en ms  60 secondes
    private static final long WAIT_TIME = 60000;
    // Duree simulee du trajet en ms
    private static final long TRIP_DURATION = 10000;

    private double posX, posY;

    // Liste des clients actuellement a bord
    private List<AID> passengers = new ArrayList<AID>();

    private boolean busy = false;

    // Reference vers le compte a rebours en cours, pour pouvoir l'annuler
    // si le taxi se remplit avant l'expiration des 60 secondes
    private DepartureWaker departureWaker;

    protected void setup() {
        // Position aleatoire de depart du taxi
        posX = Math.random() * 100;
        posY = Math.random() * 100;


        // Enregistrement du service "taxi-service" aupres du DF
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

        // Repond aux demandes de course des clients
        addBehaviour(new RequestsServer());
        // Gere l'acceptation d'une course par un client (embarquement)
        addBehaviour(new BoardingServer());

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

    /**
     Repond aux CFP envoyes par les clients avec la distance du taxi,
     sauf si le taxi est complet ou deja en course : dans ce cas REFUSE.
     */
    private class RequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                if (!busy && passengers.size() < CAPACITY) {
                    try {
                        String[] coords = msg.getContent().split(",");
                        double cx = Double.parseDouble(coords[0]);
                        double cy = Double.parseDouble(coords[1]);
                        double dist = distanceTo(cx, cy);
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(dist));
                    }
                    catch (Exception e) {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("bad-request");
                    }
                }
                else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("full");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }  // Fin de RequestsServer

    /**
     Gere l'embarquement des clients qui acceptent la proposition du taxi.
     */
    private class BoardingServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                if (!busy && passengers.size() < CAPACITY) {
                    passengers.add(msg.getSender());
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("boarded");
                    System.out.println("Taxi "+getLocalName()+" : "+msg.getSender().getLocalName()+
                            " est monte a bord ("+passengers.size()+"/"+CAPACITY+")");

                    // Demarre le compte a rebours de 60s au premier client
                    if (passengers.size() == 1) {
                        departureWaker = new DepartureWaker(myAgent, WAIT_TIME);
                        myAgent.addBehaviour(departureWaker);
                    }

                    // Depart immediat si le taxi est complet
                    if (passengers.size() == CAPACITY) {
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