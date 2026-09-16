package taxi;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TaxiGui extends JFrame {

    // Liste des taxis présents sur la carte
    private final List<AgentPosition> taxis =
            new ArrayList<>();

    // Liste des clients présents sur la carte
    private final List<AgentPosition> clients =
            new ArrayList<>();

    // Zone graphique représentant la carte
    private final MapPanel mapPanel;

    public TaxiGui() {

        // Titre de la fenêtre
        super("Taxi Simulation");

        /*
         * Lorsque l'utilisateur ferme la fenêtre,
         * on ferme uniquement la GUI.
         *
         * IMPORTANT :
         * cela ne tue PAS les agents JADE.
         */
        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        /*
         * La carte occupe environ la moitié
         * de l'écran grâce à une taille fixe.
         */
        mapPanel = new MapPanel();

        mapPanel.setPreferredSize(
                new Dimension(700, 500)
        );

        getContentPane().add(
                mapPanel,
                BorderLayout.CENTER
        );

        setResizable(false);
    }

    /**
     * Ajoute un taxi à la carte.
     */
    public void addTaxi(
            String name,
            int x,
            int y
    ) {

        taxis.add(
                new AgentPosition(
                        name,
                        x,
                        y
                )
        );

        // Redessine la carte
        mapPanel.repaint();
    }

    /**
     * Ajoute un client à la carte.
     */
    public void addClient(
            String name,
            int x,
            int y
    ) {

        clients.add(
                new AgentPosition(
                        name,
                        x,
                        y
                )
        );

        mapPanel.repaint();
    }

    /**
     * Affiche la fenêtre au centre de l'écran.
     */
    public void showGui() {

        pack();

        Dimension screenSize =
                Toolkit.getDefaultToolkit()
                        .getScreenSize();

        int centerX =
                screenSize.width / 2;

        int centerY =
                screenSize.height / 2;

        setLocation(
                centerX - getWidth() / 2,
                centerY - getHeight() / 2
        );

        setVisible(true);
    }

    /**
     * Représente la position d'un agent.
     */
    private static class AgentPosition {

        String name;
        int x;
        int y;

        AgentPosition(
                String name,
                int x,
                int y
        ) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Panneau qui dessine la carte.
     */
    private class MapPanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);

            /*
             * Fond noir de la carte.
             */
            g.setColor(Color.BLACK);

            g.fillRect(
                    0,
                    0,
                    getWidth(),
                    getHeight()
            );

            /*
             * Dessin des taxis.
             *
             * Les taxis sont représentés en ROUGE.
             */
            for (AgentPosition taxi : taxis) {

                g.setColor(Color.RED);

                g.fillOval(
                        taxi.x,
                        taxi.y,
                        20,
                        20
                );

                /*
                 * Nom du taxi au-dessus
                 * de sa position.
                 */
                g.setColor(Color.WHITE);

                g.drawString(
                        taxi.name,
                        taxi.x,
                        taxi.y - 5
                );
            }

            /*
             * Dessin des clients.
             *
             * Les clients sont représentés en JAUNE.
             */
            for (AgentPosition client : clients) {

                g.setColor(Color.YELLOW);

                g.fillOval(
                        client.x,
                        client.y,
                        15,
                        15
                );

                /*
                 * Nom du client.
                 */
                g.setColor(Color.WHITE);

                g.drawString(
                        client.name,
                        client.x,
                        client.y - 5
                );
            }
        }
    }
}