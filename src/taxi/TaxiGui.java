/*
package taxi;

import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class TaxiGui extends JFrame {

    private static final int MAX_TAXIS = 6;
    private static final int MAX_CLIENTS = 12;

    private TaxiAgent myAgent;

    private int taxiCount = 1;
    private int clientCount = 0;

    private JSpinner taxiSpinner;
    private JSpinner clientSpinner;

    private JCheckBox snifferCheckBox;
    private JCheckBox introspectorCheckBox;

    private JButton startButton;

    TaxiGui(TaxiAgent a) {
        super(a.getLocalName() + " - Simulation configuration");

        myAgent = a;

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        form.setBorder(
                BorderFactory.createEmptyBorder(
                        15, 15, 15, 15));

        form.add(new JLabel(
                "Number of taxis (1-" + MAX_TAXIS + "):"));

        taxiSpinner = new JSpinner(
                new SpinnerNumberModel(
                        1,
                        1,
                        MAX_TAXIS,
                        1));
        form.add(taxiSpinner);

        form.add(new JLabel(
                "Number of clients (1-" + MAX_CLIENTS + "):"));

        clientSpinner = new JSpinner(
                new SpinnerNumberModel(
                        1,
                        1,
                        MAX_CLIENTS,
                        1));
        form.add(clientSpinner);

        form.add(new JLabel(
                "Launch Sniffer:"));

        snifferCheckBox = new JCheckBox();
        snifferCheckBox.setSelected(true);
        form.add(snifferCheckBox);

        form.add(new JLabel(
                "Launch Introspector:"));

        introspectorCheckBox = new JCheckBox();
        introspectorCheckBox.setSelected(true);
        form.add(introspectorCheckBox);

        getContentPane().add(
                form,
                BorderLayout.CENTER);

        startButton =
                new JButton(
                        "Start simulation");

        startButton.addActionListener(
                ev -> startSimulation());

        JPanel south = new JPanel();
        south.add(startButton);

        getContentPane().add(
                south,
                BorderLayout.SOUTH);

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE);

        setResizable(false);
    }

    private void startSimulation() {

        int nbTaxis =
                (Integer) taxiSpinner.getValue();

        int nbClients =
                (Integer) clientSpinner.getValue();

        int taxisToCreate =
                Math.max(0, nbTaxis - 1);

        try {

            ContainerController container =
                    myAgent.getContainerController();

            */
/*
             * -----------------------------------------
             * Launch debugging agents FIRST
             * -----------------------------------------
             *//*


            if (snifferCheckBox.isSelected()) {

                try {
                    AgentController sniffer =
                            container.createNewAgent(
                                    "sniffer",
                                    "jade.tools.sniffer.Sniffer",
                                    null);

                    sniffer.start();
                }
                catch (Exception ignored) {
                    System.out.println(
                            "Sniffer already running.");
                }
            }

            if (introspectorCheckBox.isSelected()) {

                try {
                    AgentController introspector =
                            container.createNewAgent(
                                    "introspector",
                                    "jade.tools.introspector.Introspector",
                                    null);

                    introspector.start();
                }
                catch (Exception ignored) {
                    System.out.println(
                            "Introspector already running.");
                }
            }

            */
/*
             * -----------------------------------------
             * Create taxis
             * -----------------------------------------
             *//*


            for (int i = 0;
                 i < taxisToCreate;
                 i++) {

                taxiCount++;

                Object[] workerArgs = {
                        "worker"
                };

                AgentController taxi =
                        container.createNewAgent(
                                "taxi" + taxiCount,
                                "taxi.TaxiAgent",
                                workerArgs);

                taxi.start();
            }

            */
/*
             * -----------------------------------------
             * Create clients
             * -----------------------------------------
             *//*


            for (int i = 0;
                 i < nbClients;
                 i++) {

                clientCount++;

                AgentController client =
                        container.createNewAgent(
                                "client" + clientCount,
                                "taxi.ClientAgent",
                                null);

                client.start();
            }
        }
        catch (ControllerException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Error while starting agents:\n"
                            + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);

            return;
        }

        dispose();
    }

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
                centerY - getHeight() / 2);

        setVisible(true);
    }
}*/

package taxi;

import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.ControllerException;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class TaxiGui extends JFrame {

    private static final int MAX_TAXIS = 6;
    private static final int MAX_CLIENTS = 12;

    private TaxiAgent myAgent;

    private int taxiCount = 1;
    private int clientCount = 0;

    private JSpinner taxiSpinner;
    private JSpinner clientSpinner;

    private JCheckBox snifferCheckBox;
    private JCheckBox introspectorCheckBox;

    private JButton startButton;

    TaxiGui(TaxiAgent a) {
        super(a.getLocalName() + " - Simulation configuration");

        myAgent = a;

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        form.setBorder(
                BorderFactory.createEmptyBorder(
                        15, 15, 15, 15));

        form.add(new JLabel(
                "Number of taxis (1-" + MAX_TAXIS + "):"));

        taxiSpinner = new JSpinner(
                new SpinnerNumberModel(
                        1,
                        1,
                        MAX_TAXIS,
                        1));
        form.add(taxiSpinner);

        form.add(new JLabel(
                "Number of clients (1-" + MAX_CLIENTS + "):"));

        clientSpinner = new JSpinner(
                new SpinnerNumberModel(
                        1,
                        1,
                        MAX_CLIENTS,
                        1));
        form.add(clientSpinner);

        form.add(new JLabel(
                "Launch Sniffer:"));

        snifferCheckBox = new JCheckBox();
        snifferCheckBox.setSelected(true);
        form.add(snifferCheckBox);

        form.add(new JLabel(
                "Launch Introspector:"));

        introspectorCheckBox = new JCheckBox();
        introspectorCheckBox.setSelected(true);
        form.add(introspectorCheckBox);

        getContentPane().add(
                form,
                BorderLayout.CENTER);

        startButton =
                new JButton(
                        "Start simulation");

        startButton.addActionListener(
                ev -> startSimulation());

        JPanel south = new JPanel();
        south.add(startButton);

        getContentPane().add(
                south,
                BorderLayout.SOUTH);

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE);

        setResizable(false);
    }

    private static final long STARTUP_DELAY_MS = 4000;

    private void startSimulation() {

        int nbTaxis = (Integer) taxiSpinner.getValue();
        int nbClients = (Integer) clientSpinner.getValue();

        int taxisToCreate = Math.max(0, nbTaxis - 1);

        try {

            ContainerController container =
                    myAgent.getContainerController();

            /*
             * List of all agents that will participate
             */
            java.util.ArrayList<String> agentNames =
                    new java.util.ArrayList<>();

            agentNames.add(myAgent.getLocalName());

            for (int i = 2; i <= nbTaxis; i++) {
                agentNames.add("taxi" + i);
            }

            for (int i = 1; i <= nbClients; i++) {
                agentNames.add("client" + i);
            }

            /*
             * Build monitoring string
             */
            String monitoredAgents =
                    String.join(" ", agentNames);

            /*
             * ----------------------------------
             * Launch Sniffer
             * ----------------------------------
             */
            if (snifferCheckBox.isSelected()) {

                try {

                    AgentController sniffer =
                            container.createNewAgent(
                                    "sniffer",
                                    "jade.tools.sniffer.Sniffer",
                                    new Object[]{
                                            monitoredAgents
                                    });

                    sniffer.start();

                } catch (Exception ignored) {
                    System.out.println(
                            "Sniffer already running.");
                }
            }

            /*
             * ----------------------------------
             * Launch Introspector
             * ----------------------------------
             */
            if (introspectorCheckBox.isSelected()) {

                try {

                    AgentController introspector =
                            container.createNewAgent(
                                    "introspector",
                                    "jade.tools.introspector.Introspector",
                                    null);

                    introspector.start();

                } catch (Exception ignored) {
                    System.out.println(
                            "Introspector already running.");
                }
            }

            /*
             * Give monitoring agents time to start
             */
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException ignored) {
            }

            /*
             * ----------------------------------
             * Create taxis
             * ----------------------------------
             */
            for (int i = 0; i < taxisToCreate; i++) {

                taxiCount++;

                Object[] workerArgs = {
                        "worker",
                        STARTUP_DELAY_MS
                };

                AgentController taxi =
                        container.createNewAgent(
                                "taxi" + taxiCount,
                                "taxi.TaxiAgent",
                                workerArgs);

                taxi.start();
            }

            /*
             * ----------------------------------
             * Create clients
             * ----------------------------------
             */
            for (int i = 0; i < nbClients; i++) {

                clientCount++;

                Object[] clientArgs = {
                        STARTUP_DELAY_MS
                };

                AgentController client =
                        container.createNewAgent(
                                "client" + clientCount,
                                "taxi.ClientAgent",
                                clientArgs);

                client.start();
            }

        }
        catch (ControllerException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Error while starting agents:\n"
                            + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);

            return;
        }

        dispose();
    }

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
                centerY - getHeight() / 2);

        setVisible(true);
    }
}