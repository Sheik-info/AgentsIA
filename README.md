# AgentsIA

Application de simulation multi-agents développée en Java avec **JADE**. Elle met en scène des **clients** et des **taxis** qui négocient automatiquement les courses via les services FIPA du framework JADE.

## Fonctionnalités

- Création dynamique de taxis et de clients
- Découverte des taxis avec le **Directory Facilitator (DF)**
- Négociation des courses par messages JADE
- Sélection du taxi selon le prix horaire, la durée et la distance
- Capacité maximale de quatre passagers par taxi
- Départ immédiat lorsque le taxi est plein
- Départ après un délai d'attente sinon
- Gestion et recharge automatique de l'autonomie des taxis
- Interface graphique Swing pour configurer et visualiser la simulation
- Affichage des statistiques de trajets dans la console

## Interface de simulation

L'application permet de choisir :

- entre 1 et 6 taxis ;
- entre 1 et 12 clients.

Les agents sont représentés sur une carte bidimensionnelle :

- taxis rouges ;
- clients jaunes ;
- espace compris entre les coordonnées 0 et 100.

## Architecture

```text
src
├── Main.java
├── firstAgent
│   └── HelloAgent.java
└── taxi
    ├── TaxiAgent.java
    ├── ClientAgent.java
    ├── TaxiGui.java
    ├── TaxiOffer.java
    ├── ClientRequest.java
    ├── ClientAcceptance.java
    └── Point2D.java
