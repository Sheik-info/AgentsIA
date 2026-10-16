package taxi;

import jade.core.Agent;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.*;

/**
 * Fenetre de configuration de la simulation (nombre de taxis et de clients).
 * Au demarrage, ouvre un ecran noir ou l'on voit les taxis (rouge)
 * et les clients jaune bouger en direct.
 */
class TaxiGui extends JFrame {

    private static final int MAX_TAXIS = 6;
    private static final int MAX_CLIENTS = 12;

    private TaxiAgent myAgent;

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

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
    }

    /**
     Cree les taxis et clients manquants (ce taxi-ci compte deja pour 1)
     puis ouvre l'ecran noir de la simulation.
     Les agents sont instancies ici, acceptNewAgent, pour que l'ecran
     puisse lire leur position.
     */
    private void startSimulation() {
        int nbTaxis = (Integer) taxiSpinner.getValue();
        int nbClients = (Integer) clientSpinner.getValue();

        ArrayList<TaxiAgent> taxis = new ArrayList<TaxiAgent>();
        ArrayList<ClientAgent> clients = new ArrayList<ClientAgent>();
        taxis.add(myAgent);

        try {
            ContainerController container = myAgent.getContainerController();

            for (int i = 2; i <= nbTaxis; i++) {
                TaxiAgent taxi = new TaxiAgent();
                taxi.setArguments(new Object[] { "worker" });
                container.acceptNewAgent("taxi"+i, taxi).start();
                taxis.add(taxi);
            }
            for (int i = 1; i <= nbClients; i++) {
                ClientAgent client = new ClientAgent();
                container.acceptNewAgent("client"+i, client).start();
                clients.add(client);
            }
        }
        catch (ControllerException ex) {
            JOptionPane.showMessageDialog(this, "Erreur au lancement des agents : "+ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFrame screen = new JFrame("Simulation des taxis");
        screen.getContentPane().add(new SimulationPanel(taxis, clients));
        screen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        screen.setSize(800, 800);
        screen.setLocationRelativeTo(null);
        screen.setVisible(true);

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

    /**
     taxis en rouge, clients en jaune.
     Les positions 0 à 100 sont lues directement dans les agents.
     */
    private static class SimulationPanel extends JPanel {

        private ArrayList<TaxiAgent> taxis;
        private ArrayList<ClientAgent> clients;
        // position affichee de chaque taxi (il glisse vers sa position reelle)
        private HashMap<TaxiAgent, double[]> shown = new HashMap<TaxiAgent, double[]>();

        SimulationPanel(ArrayList<TaxiAgent> taxis, ArrayList<ClientAgent> clients) {
            this.taxis = taxis;
            this.clients = clients;
            setBackground(Color.BLACK);

            new javax.swing.Timer(30, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    repaint();
                }
            }).start();
        }

        // Lit un attribut prive d'un agent (null si pas encore initialise)
        private Object read(Object agent, String name) {
            try {
                Field f = agent.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(agent);
            } catch (Exception e) {
                return null;
            }
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            double scale = Math.min(getWidth(), getHeight()) / 110.0;

            // Clients en jaune a leur point de depart, jusqu'a la fin de leur course
            g.setColor(Color.YELLOW);
            for (ClientAgent c : clients) {
                Point2D p = (Point2D) read(c, "startWaypoint");
                if (p == null || c.getAgentState().getValue() == Agent.AP_DELETED) continue;
                int x = (int) (p.getX() * scale) + 20;
                int y = (int) (p.getY() * scale) + 20;
                g.fillOval(x - 6, y - 6, 12, 12);
                g.drawString(c.getLocalName(), x + 9, y + 4);
            }

            // Taxis (rouge) : en course ils visent nextWaypoint, sinon lastWaypoint
            g.setColor(Color.RED);
            for (TaxiAgent t : taxis) {
                Point2D last = (Point2D) read(t, "lastWaypoint");
                Point2D next = (Point2D) read(t, "nextWaypoint");
                if (last == null) continue;

                boolean moving = Boolean.TRUE.equals(read(t, "isTripOngoing"))
                        || Boolean.TRUE.equals(read(t, "isReloadOngoing"));
                Point2D target = (moving && next != null) ? next : last;

                double[] pos = shown.get(t);
                if (pos == null) {
                    pos = new double[] { target.getX(), target.getY() };
                    shown.put(t, pos);
                }
                // glisse vers la cible
                double dx = target.getX() - pos[0];
                double dy = target.getY() - pos[1];
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 1.2) {
                    pos[0] = target.getX();
                    pos[1] = target.getY();
                } else {
                    pos[0] += dx / d * 1.2;
                    pos[1] += dy / d * 1.2;
                }

                int x = (int) (pos[0] * scale) + 20;
                int y = (int) (pos[1] * scale) + 20;
                g.fillRect(x - 8, y - 8, 16, 16);
                g.drawString(t.getLocalName(), x + 11, y + 4);
            }
        }
    }
}