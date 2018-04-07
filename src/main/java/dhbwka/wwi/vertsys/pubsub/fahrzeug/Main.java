/*
 * Copyright © 2018 Dennis Schulmeister-Zimolong
 * 
 * E-Mail: dhbw@windows3.de
 * Webseite: https://www.wpvs.de/
 * 
 * Dieser Quellcode ist lizenziert unter einer
 * Creative Commons Namensnennung 4.0 International Lizenz.
 */
package dhbwka.wwi.vertsys.pubsub.fahrzeug;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Hauptklasse unseres kleinen Progrämmchens.
 *
 * Mit etwas Google-Maps-Erfahrung lassen sich relativ einfach eigene
 * Wegstrecken definieren. Man muss nur Rechtsklick auf einen Punkt machen und
 * "Was ist hier?" anklicken, um die Koordinaten zu sehen. Allerdings speichert
 * Goolge Maps eine Nachkommastelle mehr, als das ITN-Format erlaubt. :-)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Fahrzeug-ID abfragen
        String vehicleId = Utils.askInput("Fahrzeug-ID", "postauto");
        MemoryPersistence persistence = new MemoryPersistence();

        
        // Zu fahrende Strecke abfragen
        File workdir = new File("./waypoints");
        String[] waypointFiles = workdir.list((File dir, String name) -> {
            return name.toLowerCase().endsWith(".itn");
        });

        System.out.println();
        System.out.println("Aktuelles Verzeichnis: " + workdir.getCanonicalPath());
        System.out.println();
        System.out.println("Verfügbare Wegstrecken");
        System.out.println();

        for (int i = 0; i < waypointFiles.length; i++) {
            System.out.println("  [" + i + "] " + waypointFiles[i]);
        }

        System.out.println();
        int index = Integer.parseInt(Utils.askInput("Zu fahrende Strecke", "0"));
        
        // TODO: Methode parseItnFile() unten ausprogrammieren
        List<WGS84> waypoints = parseItnFile(new File(workdir, waypointFiles[index]));
        try {
            waypoints = parseItnFile(new File(workdir, waypointFiles[index]));
        // Adresse des MQTT-Brokers abfragen
            String Adresse = Utils.askInput("MQTT-Broker", Utils.MQTT_BROKER_ADDRESS);

        // TODO: Sicherstellen, dass bei einem Verbindungsabbruch eine sog.
        // LastWill-Nachricht gesendet wird, die auf den Verbindungsabbruch
        // hinweist. Die Nachricht soll eine "StatusMessage" sein, bei der das
        // Feld "type" auf "StatusType.CONNECTION_LOST" gesetzt ist.
        //
        // Die Nachricht muss dem MqttConnectOptions-Objekt übergeben werden
        // und soll an das Topic Utils.MQTT_TOPIC_NAME gesendet werden.
        
        // TODO: Verbindung zum MQTT-Broker herstellen.

        // TODO: Statusmeldung mit "type" = "StatusType.VEHICLE_READY" senden.
        // Die Nachricht soll soll an das Topic Utils.MQTT_TOPIC_NAME gesendet
        // werden.
        
        // TODO: Thread starten, der jede Sekunde die aktuellen Sensorwerte
        // des Fahrzeugs ermittelt und verschickt. Die Sensordaten sollen
        // an das Topic Utils.MQTT_TOPIC_NAME + "/" + vehicleId gesendet werden.
            
            MqttClient mqttClient = new MqttClient(Adresse,vehicleId,persistence);

            StatusMessage lastWill= new StatusMessage();
            lastWill.type=StatusType.CONNECTION_LOST;
            lastWill.vehicleId=vehicleId;
            lastWill.message="Keine Verbindung mehr";

            MqttConnectOptions mqttCO = new MqttConnectOptions();
            mqttCO.setWill(Utils.MQTT_TOPIC_NAME, lastWill.toJson(), 0, true);
            mqttCO.setCleanSession(true);
            System.out.println("Verbindung zum Broker: "+Adresse);
        
            mqttClient.connect(mqttCO);
            System.out.println("Verbunden");
            
            StatusMessage Status= new StatusMessage();
            Status.type=StatusType.VEHICLE_READY;
            Status.vehicleId=vehicleId;
            Status.message="Fahrzeug bereit";

            MqttMessage Nachricht = new MqttMessage();
            Nachricht.setQos(0);
            Nachricht.setPayload(Status.toJson());
            mqttClient.publish(Utils.MQTT_TOPIC_NAME, Nachricht);
            System.out.println("Bereit!");
            
            Vehicle vehicle = new Vehicle(vehicleId, waypoints);
            vehicle.startVehicle();

        // Warten, bis das Programm beendet werden soll
            java.util.Timer timer= new java.util.Timer();
            timer.schedule(new java.util.TimerTask() {

                @Override
                public void run() {
                    try {
                        mqttClient.publish(Utils.MQTT_TOPIC_NAME + "/" + vehicleId, new MqttMessage(vehicle.getSensorData().toJson()));
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    }
            }, 0,1000);
            
            Utils.fromKeyboard.readLine();
            vehicle.stopVehicle();
            timer.cancel();

            mqttClient.publish(Utils.MQTT_TOPIC_NAME, new MqttMessage(lastWill.toJson()));
            mqttClient.disconnect();
            System.out.println("Verbindung getrennt zu " + Adresse);
        }catch(Exception e){
            e.printStackTrace();
        }

        
        // TODO: Oben vorbereitete LastWill-Nachricht hier manuell versenden,
        // da sie bei einem regulären Verbindungsende nicht automatisch
        // verschickt wird.
        //
        // Anschließend die Verbindung trennen und den oben gestarteten Thread
        // beenden, falls es kein Daemon-Thread ist.
    }

    /**
     * Öffnet die in "filename" übergebene ITN-Datei und extrahiert daraus die
     * Koordinaten für die Wegstrecke des Fahrzeugs. Das Dateiformat ist ganz
     * simpel:
     *
     * <pre>
     * 0845453|4902352|Point 1 |0|
     * 0848501|4900249|Point 2 |0|
     * 0849295|4899460|Point 3 |0|
     * 0849796|4897723|Point 4 |0|
     * </pre>
     *
     * Jede Zeile enthält einen Wegpunkt. Die Datenfelder einer Zeile werden
     * durch | getrennt. Das erste Feld ist die "Longitude", das zweite Feld die
     * "Latitude". Die Zahlen müssen durch 100_000.0 geteilt werden.
     *
     * @param file ITN-Datei
     * @return Liste mit Koordinaten
     * @throws java.io.IOException
     */
    public static List<WGS84> parseItnFile(File file) throws IOException {
        List<WGS84> waypoints = new ArrayList<>();

        // TODO: Übergebene Datei parsen und Liste "waypoints" damit füllen
        String[] werte = {""};
        
        Scanner eingabe = new Scanner(file);
        while (eingabe.hasNext()) {
            String nextLine = eingabe.nextLine();
            werte = nextLine.split("\\|");
            waypoints.add(new WGS84(Double.parseDouble(werte[1])/100000,Double.parseDouble(werte[0])/100000));
        }       
        return waypoints;
    }
}
