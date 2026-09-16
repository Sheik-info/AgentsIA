package taxi;

import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Fenetre de configuration de la simulation.
 * Ouverte par le premier TaxiAgent lance (celui demarre "a la main" avec JADE,
 * exactement comme BookSellerAgent ouvre BookSellerGui dans son setup()).
 * Permet de choisir le nombre total de taxis (max 6) et de clients (max 12),
 * puis cree les agents manquants via le conteneur de l'agent qui l'a ouverte.
 * Les taxis crees ici recoivent l'argument "worker" pour ne pas rouvrir
 * une seconde fenetre de configuration.
 */
class TaxiGui extends JFrame {

    private static final int MAX_TAXIS = 6;
    private static final int MAX_CLIENTS = 12;

    private TaxiAgent myAgent;

    // Le taxi qui a ouvert cette fenetre compte deja pour 1 taxi
    private int taxiCount = 1;
    private int clientCount = 0;

    private JSpinner taxiSpinner;
    private JSpinner clientSpinner;
    private JButton startButton;

    TaxiGui(TaxiAgent a) {
        super(a.getLocalName()+" - Configuration de la simulation");
        myAgent = a;

        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));

        form.add(new JLabel("Nombre de taxis (1 a "+MAX_TAXIS+") :"));
        taxiSpinner = new JSpinner(new SpinnerNumberModel(1, 1, MAX_TAXIS, 1));
        form.add(taxiSpinner);

        form.add(new JLabel("Nombre de clients (1 a "+MAX_CLIENTS+") :"));
        clientSpinner = new JSpinner(new SpinnerNumberModel(1, 1, MAX_CLIENTS, 1));
        form.add(clientSpinner);

        getContentPane().add(form, BorderLayout.CENTER);

        startButton = new JButton("Demarrer la simulation");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                startSimulation();
            }
        });
        JPanel south = new JPanel();
        south.add(startButton);
        getContentPane().add(south, BorderLayout.SOUTH);

        // Ne fait que fermer cette fenetre de configuration, pas la plateforme JADE
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
    }

    /**
     Cree les taxis et clients manquants (ce taxi-ci compte deja pour 1)
     via le conteneur JADE de l'agent qui a ouvert cette fenetre.
     */
    private void startSimulation() {
        int nbTaxis = (Integer) taxiSpinner.getValue();
        int nbClients = (Integer) clientSpinner.getValue();
        int taxisToCreate = Math.max(0, nbTaxis - 1);

        try {
            ContainerController container = myAgent.getContainerController();

            for (int i = 0; i < taxisToCreate; i++) {
                taxiCount++;
                Object[] workerArgs = { "worker" };
                AgentController taxi = container.createNewAgent(
                        "taxi"+taxiCount, "taxi.TaxiAgent", workerArgs);
                taxi.start();
            }
            for (int i = 0; i < nbClients; i++) {
                clientCount++;
                AgentController client = container.createNewAgent(
                        "client"+clientCount, "taxi.ClientAgent", null);
                client.start();
            }
        }
        catch (ControllerException ex) {
            JOptionPane.showMessageDialog(this, "Erreur au lancement des agents : "+ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                nbTaxis+" taxi(s) et "+nbClients+" client(s) au total.\nVoir la console pour suivre la simulation.",
                "Simulation demarree", JOptionPane.INFORMATION_MESSAGE);

        dispose();
    }

    public void showGui() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int centerX = (int) screenSize.getWidth() / 2;
        int centerY = (int) screenSize.getHeight() / 2;
        setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
        setVisible(true);
    }
}