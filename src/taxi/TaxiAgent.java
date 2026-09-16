package taxi;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class TaxiAgent extends Agent {

    // Nombre de places disponibles dans le taxi
    private int nbPlace;

    // Prix d'une place
    private int pricePlace;

    // Position du taxi sur la carte
    private int x;
    private int y;

    // Interface graphique globale
    private static TaxiGui gui;

    protected void setup() {

        /*
         * Récupération des paramètres donnés lors du lancement
         *
         * Arguments :
         * args[0] = prix
         * args[1] = position X
         * args[2] = position Y
         */

        Object[] args = getArguments();

        if (args != null && args.length >= 3) {
            pricePlace = Integer.parseInt(args[0].toString());
            x = Integer.parseInt(args[1].toString());
            y = Integer.parseInt(args[2].toString());
        }
        else {
            // Valeurs par défaut
            pricePlace = 10;
            x = 100;
            y = 100;
        }

        // Chaque taxi possède initialement 4 places
        nbPlace = 4;

        /*
         * Création de la GUI uniquement pour le premier taxi.
         *
         * On ne veut PAS une fenêtre par taxi.
         */
        if (gui == null) {
            gui = new TaxiGui();
            gui.showGui();
        }

        // Ajout du taxi à la carte
        gui.addTaxi(
                getLocalName(),
                x,
                y
        );

        // Création de la description du service
        DFAgentDescription dfd = new DFAgentDescription();

        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();

        sd.setType("Taxi");
        sd.setName("JADE-taxi-course");

        dfd.addServices(sd);

        try {
            // Enregistrement du taxi dans le DF
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Behaviour qui répond aux demandes de prix
        addBehaviour(new OfferRequestsServer());

        // Behaviour qui traite les réservations
        addBehaviour(new PurchaseOrdersServer());

        System.out.println(
                "Taxi " + getLocalName()
                        + " created at (" + x + ", " + y + ")"
                        + " - price: " + pricePlace
        );
    }

    /**
     * Behaviour qui répond aux CFP.
     *
     * Le client demande :
     *
     * "Quel est le prix d'une place ?"
     */
    private class OfferRequestsServer extends CyclicBehaviour {

        public void action() {

            MessageTemplate mt =
                    MessageTemplate.MatchPerformative(
                            ACLMessage.CFP
                    );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {

                ACLMessage reply = msg.createReply();

                /*
                 * On vérifie seulement s'il reste des places.
                 *
                 * IMPORTANT :
                 * On ne retire PAS encore de place.
                 */
                if (nbPlace > 0) {

                    reply.setPerformative(
                            ACLMessage.PROPOSE
                    );

                    reply.setContent(
                            String.valueOf(pricePlace)
                    );

                    System.out.println(
                            getLocalName()
                                    + " proposes "
                                    + pricePlace
                                    + " to "
                                    + msg.getSender().getLocalName()
                    );

                }
                else {

                    reply.setPerformative(
                            ACLMessage.REFUSE
                    );

                    reply.setContent(
                            "not-available"
                    );
                }

                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    /**
     * Behaviour qui traite les réservations.
     *
     * La place est retirée uniquement lorsqu'un client
     * accepte réellement la proposition.
     */
    private class PurchaseOrdersServer extends CyclicBehaviour {

        public void action() {

            MessageTemplate mt =
                    MessageTemplate.MatchPerformative(
                            ACLMessage.ACCEPT_PROPOSAL
                    );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {

                ACLMessage reply = msg.createReply();

                if (nbPlace > 0) {

                    // Une vraie réservation est effectuée
                    nbPlace--;

                    reply.setPerformative(
                            ACLMessage.INFORM
                    );

                    reply.setContent(
                            "reservation-successful"
                    );

                    System.out.println(
                            getLocalName()
                                    + " reserved a place for "
                                    + msg.getSender().getLocalName()
                                    + " - remaining places: "
                                    + nbPlace
                    );

                }
                else {

                    reply.setPerformative(
                            ACLMessage.FAILURE
                    );

                    reply.setContent(
                            "not-available"
                    );
                }

                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    /**
     * Suppression de l'agent.
     *
     * On désenregistre le taxi du DF.
     */
    protected void takeDown() {

        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        System.out.println(
                "Taxi-agent "
                        + getLocalName()
                        + " terminating."
        );
    }
}