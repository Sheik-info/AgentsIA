package firstAgent;

// Classe de base de tous les agents JADE
import jade.core.Agent;

// Classe représentant un message ACL échangé entre les agents
import jade.lang.acl.ACLMessage;


public class HelloAgent extends Agent {

    // Méthode appelée automatiquement par JADE lorsque l'agent démarre
    protected void setup() {

        // Création d'un message ACL.
        // INFORM est le performatif du message :
        // il signifie que l'agent souhaite transmettre une information.
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);

        // On définit le contenu du message.
        // Ici, le message contient simplement le texte "contenu".
        inform.setContent("contenu");

        // On définit le protocole utilisé pour cette communication.
        // Ici, on utilise simplement le nom "information".
        inform.setProtocol("information");

        // Affichage du message dans la console pour vérifier
        // que l'agent a bien été lancé et que le message a été créé.
        System.out.println("HelloAgent démarré !");
        System.out.println("Performative : INFORM");
        System.out.println("Contenu : " + inform.getContent());
        System.out.println("Protocole : " + inform.getProtocol());
    }
}
