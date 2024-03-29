package streamers;

import java.util.*;

import com.hp.hpl.jena.rdf.model.*;
import eu.larkc.csparql.cep.api.RdfQuadruple;
import eu.larkc.csparql.cep.api.RdfStream;

import helper.MathOperations;
import helper.HelpingVariables;
import model.CareeInfModel;
import model.Sensor;
import model.Space;

public class SpaceSensorsStreamer extends RdfStream implements Runnable{

    private final long timeStep;
    private boolean keepRunning = true;

    public SpaceSensorsStreamer( final String iri, long timeStep) {
        super(iri);
        this.timeStep = timeStep;
    }

    public void stop() {
        keepRunning = false;
    }

    @Override
    public void run() {

        int count = 1;
        List<Sensor> sensorDetailsList = new ArrayList<>();
        Map<String, Space> allSensorsValueAtSpecificLocationList = new HashMap<>();
        List<String> sparqlQueryAllSensorsList;
        String timeNow;

        try {
            sparqlQueryAllSensorsList = CareeInfModel.Instance().getQueryResult("data/Queries/sparql/FindAllSensorsInTheBuildingAlongWithTheirSpace.txt");

            if(!sparqlQueryAllSensorsList.isEmpty()) {

                Sensor sensor;
                timeNow = String.valueOf(System.currentTimeMillis());

                for (int i = 0; i < sparqlQueryAllSensorsList.size() - 2; i += 3) {
                    String locationName = sparqlQueryAllSensorsList.get(i + 1);
                    sensor = new Sensor(sparqlQueryAllSensorsList.get(i), sparqlQueryAllSensorsList.get(i + 2), locationName);
                    sensorDetailsList.add(sensor);

                    if(!allSensorsValueAtSpecificLocationList.containsKey(locationName)){
                        Optional<Space> space = HelpingVariables.spaceInfoList.stream()
                                .filter(x -> x.getName().equals(locationName))
                                .findFirst();

                        if (space.isPresent()) {
                            allSensorsValueAtSpecificLocationList.put(locationName, space.get());
                        } else {
                            throw new Exception("Location not found in the spaceInfoList");
                        }
                    }

                    generateSensorValue(sensor, timeNow, allSensorsValueAtSpecificLocationList.get(locationName));
                }
            }
            Thread.sleep(timeStep);
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (keepRunning){
            System.out.println("Sensors Streamer Time Step No: " +  count);
            timeNow = String.valueOf(System.currentTimeMillis());

            for (Sensor s: sensorDetailsList) {
                generateSensorValue(s, timeNow, allSensorsValueAtSpecificLocationList.get(s.getLocation()));
            }

            /*
            Iterating all the spaces (i.e., nodes & edges)
            */
            RdfQuadruple q;
            for (String key : allSensorsValueAtSpecificLocationList.keySet()) {
                Space s = allSensorsValueAtSpecificLocationList.get(key);
                Resource locationInstance = CareeInfModel.Instance().getResource(s.getName());

                CareeInfModel.Instance().addLiteral(locationInstance, HelpingVariables.safetyValue, s.getSafetyValue());
                q = new RdfQuadruple(
                        s.getName(),
                        HelpingVariables.safetyValue.toString(),
                        String.valueOf(s.getSafetyValue())+"^^http://www.w3.org/2001/XMLSchema#double", System.currentTimeMillis());
                this.put(q);

//                if (!s.isAvailable()) {
//                    CareeInfModel.Instance().add(locationInstance, HelpingVariables.hasAvailabilityStatus, HelpingVariables.unavailableInstance);
//                }
            }
            count ++;
            try {
                Thread.sleep(timeStep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    public void generateSensorValue( Sensor sensor, String time, Space space) {
        double sensorValueNumber;
        boolean sensorValueBoolean;
        double safetyVal;
        RdfQuadruple q;
        String type = sensor.getObservationType();

        switch (type) {

            case HelpingVariables.exPrefix + "Temperature":
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.rdfPrefix + "type",
                        HelpingVariables.sosaPrefix + "Observation", System.currentTimeMillis());
                this.put(q);

                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "observedProperty",
                        HelpingVariables.exPrefix + "Temperature", System.currentTimeMillis());
                    this.put(q);

                if(sensor.getValue() == null) {
                    sensor.setValue(25);
                }
                sensorValueNumber = MathOperations.getRandomNumberInRange((Double) sensor.getValue() + 5, (Double) sensor.getValue() - 2);

                if(sensorValueNumber < 10) sensorValueNumber = 10;
                else if( sensorValueNumber > 100) sensorValueNumber = 100;

                //applying an equation to get safety value of a space using temperature value
                safetyVal = (-0.0131 * sensorValueNumber) + 1.3;
                if(safetyVal < 0 ) space.setSafetyValue(0);
                else if(safetyVal > 1) space.setSafetyValue(1);
                else space.setSafetyValue(safetyVal);

                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        sensorValueNumber  + "^^http://www.w3.org/2001/XMLSchema#double", System.currentTimeMillis());
                    this.put(q);

                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sbeoPrefix + "atTime",
                        ""+ time, System.currentTimeMillis());
                    this.put(q);
                    
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "madeBySensor",
                        ""+ sensor.getSensorName(), System.currentTimeMillis());
                    this.put(q);

                space.setTemperatureSensorValue(sensorValueNumber);
                sensor.setValue(sensorValueNumber);
                break;

            case HelpingVariables.exPrefix + "Smoke":
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.rdfPrefix + "type",
                        HelpingVariables.sosaPrefix + "Observation", System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "observedProperty",
                        HelpingVariables.exPrefix + "Smoke", System.currentTimeMillis());
                    this.put(q);


                if(sensor.getValue() == null) {
                    sensor.setValue(false);
                } else if(space.getTemperatureSensorValue() >= 55){ //check if the temperature of the same location is greater than 50
                    sensor.setValue(true);
                }

                sensorValueBoolean = (boolean) sensor.getValue();
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        String.valueOf(sensorValueBoolean) + "^^http://www.w3.org/2001/XMLSchema#boolean", System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sbeoPrefix + "atTime",
                        ""+ time, System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "madeBySensor",
                        ""+ sensor.getSensorName(), System.currentTimeMillis());
                    this.put(q);


                space.setSmokeExists(sensorValueBoolean);
                sensor.setValue(sensorValueBoolean);
                break;

            case HelpingVariables.exPrefix + "Humidity":
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.rdfPrefix + "type",
                        HelpingVariables.sosaPrefix + "Observation", System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "observedProperty",
                        HelpingVariables.exPrefix + "Humidity", System.currentTimeMillis());
                    this.put(q);

                if(sensor.getValue() == null) {
                    sensor.setValue(0.4);
                }

                //applying an equation to get safety value of a space using temperature value
                sensorValueNumber =  ( -0.402 * Math.log(space.getTemperatureSensorValue()) ) + 1.8078;
                if(sensorValueNumber < 0) sensorValueNumber = 0;

                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        sensorValueNumber + "^^http://www.w3.org/2001/XMLSchema#double", System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sbeoPrefix + "atTime",
                        ""+ time, System.currentTimeMillis());
                    this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "madeBySensor",
                        ""+ sensor.getSensorName(), System.currentTimeMillis());
                    this.put(q);


                space.setHumiditySensorValue(sensorValueNumber);
                sensor.setValue(sensorValueNumber);
                break;

            case HelpingVariables.exPrefix + "SpaceAccessibility":
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.rdfPrefix + "type",
                        HelpingVariables.sosaPrefix + "Observation", System.currentTimeMillis());
                this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "observedProperty",
                        HelpingVariables.exPrefix + "SpaceAccessibility", System.currentTimeMillis());
                this.put(q);


                if(sensor.getValue() == null) {
                    sensor.setValue(true);
                } else if (space.getTemperatureSensorValue() >=60 && space.isSmokeExists() && space.getHumiditySensorValue() <= 0.2){ //making a space inaccessible using a specific constraint
                    sensor.setValue(false);
                }
                sensorValueBoolean = (boolean) sensor.getValue();
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        String.valueOf(sensorValueBoolean) + "^^http://www.w3.org/2001/XMLSchema#boolean", System.currentTimeMillis());
                this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sbeoPrefix + "atTime",
                        ""+ time, System.currentTimeMillis());
                this.put(q);


                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "madeBySensor",
                        ""+ sensor.getSensorName(), System.currentTimeMillis());
                this.put(q);

                space.setAvailable(sensorValueBoolean);
                break;

            case HelpingVariables.exPrefix + "HumanDetection":
                break;

            default:
                System.out.println("Observable Property (e.g., Temperature, Smoke) not found");
        }
    }


}
