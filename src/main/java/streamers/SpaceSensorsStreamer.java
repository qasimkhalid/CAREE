package streamers;

import java.util.*;

import entities.PersonController;
import eu.larkc.csparql.cep.api.RdfQuadruple;
import eu.larkc.csparql.cep.api.RdfStream;

import entities.EvacuationController;
import operations.AutomatedOperations;
import operations.MathOperations;
import helper.HelpingVariables;
import model.*;

public class SpaceSensorsStreamer extends RdfStream implements Runnable{

    private final long timeStep;
    private static boolean keepRunning = true;
    private final double evacuationTriggerSafetyValue;
    private boolean hazardDetectionFlag;
    private static String streamIRI;
    private static Map<String, Space> allSensorsValueAtSpecificLocationList;
    private final int manualIncidentReferenceTime;
    private final String manualIncidentLocation
            ;

    public SpaceSensorsStreamer( final String iri, long timeStep, double evacuationTriggerSafetyValue, int manualIncidentReferenceTime, String manualIncidentLocation) {
        super(iri);
        streamIRI = iri;
        this.timeStep = timeStep;
        this.evacuationTriggerSafetyValue = evacuationTriggerSafetyValue;
        this.manualIncidentReferenceTime = manualIncidentReferenceTime;
        this.manualIncidentLocation= manualIncidentLocation;
    }
    public static String getStreamIRI(){
        return streamIRI;
    }

    public synchronized static Map<String, Space> getSpacesInfo(){
        return allSensorsValueAtSpecificLocationList;
    }

    public static void stop() {
        keepRunning = false;
    }

    @Override
    public void run() {

        int count = 1;

        //List of Sensor objects
        List<Sensor> sensorDetailsList = new ArrayList<>();

        //Mapping of SpaceName with respect to the Space objects
        allSensorsValueAtSpecificLocationList = new HashMap<>();
        List<String> sparqlQueryAllSensorsList;
        String timeNow;

        try {

            //Sparql query to find all the sensors, along with their type and the space where they are installed.
            sparqlQueryAllSensorsList = CareeInfModel.Instance().getQueryResult("data/Queries/sparql/FindAllSensorsInTheBuildingAlongWithTheirSpace.txt");

            if(!sparqlQueryAllSensorsList.isEmpty()) {
                timeNow = String.valueOf(System.currentTimeMillis());

                //Organizing the results of the Sparql query
                for (int i = 0; i < sparqlQueryAllSensorsList.size() - 2; i += 3) {
                    String sensorName = sparqlQueryAllSensorsList.get(i);
                    String observationType =  sparqlQueryAllSensorsList.get(i + 2);
                    String locationName = sparqlQueryAllSensorsList.get(i + 1);

                    //Creating a new Sensor object and adding it in a Sensors List
                    Sensor sensor = new Sensor(sensorName, observationType, locationName);
                    sensorDetailsList.add(sensor);

                    //
                    if(!allSensorsValueAtSpecificLocationList.containsKey(locationName)){
                        Optional<Space> space = HelpingVariables.spaceInfoList.stream()
                                .filter(x -> x.getName().equals(locationName))
                                .findFirst();

                        if (space.isPresent()) {
                            allSensorsValueAtSpecificLocationList.put(locationName, space.get());
                        } else {
                            throw new Exception("Location " + locationName +  " not found in the spaceInfoList");
                        }
                    }

                    generateSensorValue(sensor, timeNow, allSensorsValueAtSpecificLocationList.get(locationName));
                }
            }
            count = count + 1;
            Thread.sleep(timeStep);
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (keepRunning){
            //System.out.println("Sensors Streamer Time Step No: " +  count);
            timeNow = String.valueOf(System.currentTimeMillis());

            for (Sensor s: sensorDetailsList) {
                generateSensorValue(s, timeNow, allSensorsValueAtSpecificLocationList.get(s.getLocation()));
            }


            // Manually Trigger for 1st incident
            if (count==manualIncidentReferenceTime){
                SpaceSensorsStreamer.getSpacesInfo().get(HelpingVariables.exPrefix+manualIncidentLocation).setSafetyValue(0.49);
            }

            // special case for interrupt (Incidents) generation
//            if (count==12){
//                SpaceSensorsStreamer.getSpacesInfo().get(HelpingVariables.exPrefix+"REw1").setSafetyValue(0.1);
//            }
//            if (iterationCounter==referenceSavedForThisScenario+11){
//                SpaceSensorsStreamer.getSpacesInfo().get(HelpingVariables.exPrefix+"OE3").setSafetyValue(0.1);
//            }
//            if (iterationCounter==referenceSavedForThisScenario+6){
//                SpaceSensorsStreamer.getSpacesInfo().get(HelpingVariables.exPrefix+"J1").setSafetyValue(0.1);
//            }


            /*
            Iterating all the spaces (i.e., nodes & edges) to get their safety value after each time step.
            */
//            RdfQuadruple q;
            for (String key : allSensorsValueAtSpecificLocationList.keySet()) {
                Space s = allSensorsValueAtSpecificLocationList.get(key);
//                Resource locationInstance = CareeInfModel.Instance().getResource(s.getName());

                /*
                * An interruption in the existing routing plan could be raised from here if the safety value reduces
                * from a specific value.
                * Multiple types of interruptions could be raised here such as depending on the type of persons.
                */

                //This condition only runs once to trigger the evacuation
                if (s.getSafetyValue() < evacuationTriggerSafetyValue) {
                    if (!hazardDetectionFlag) {

                        // *** Testing Block Start ***
                        hazardDetectionFlag = true;
                        System.out.println("************");
                        System.out.print("HAZARD DETECTED on ");
                        System.out.println(s.getReadableName() + " and its safety value has become " +  s.getSafetyValue());
                        System.out.println("Hazard detection flag = true");
                        System.out.println("At Reference time = " + count);
                        System.out.println("************");
                        long hazardDetectionTime = System.currentTimeMillis();
                        System.out.println("Hazard Detected and Evacuation is about to Start: " + hazardDetectionTime);
                        InitializeEvacuationStreamer(hazardDetectionTime, count);
                        // *** Testing Block End ***
                    }
//
//
//
                }



//                CareeInfModel.Instance().addLiteral(locationInstance, HelpingVariables.safetyValue, s.getSafetyValue());
//                q = new RdfQuadruple(
//                        s.getName(),
//                        HelpingVariables.safetyValue.toString(),
//                        String.valueOf(s.getSafetyValue())+"^^http://www.w3.org/2001/XMLSchema#double", System.currentTimeMillis());
//                this.put(q);

//                if (!s.isAvailable()) {
//                    CareeInfModel.Instance().add(locationInstance, HelpingVariables.hasAvailabilityStatus, HelpingVariables.unavailableInstance);
//                }
            }
            count ++;
            try {
                //Space Sensor going to sleep
                Thread.sleep(timeStep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void InitializeEvacuationStreamer(long hazardDetectionTime, int count) {
//        EvacuationStreamer evacuationStream = new EvacuationStreamer(HelpingVariables.kbIRI, 100, true, 1f);
//        CareeCsparqlEngineImpl.Instance().registerStream(evacuationStream);
//        Thread evacuationStreamThread = new Thread(evacuationStream);
//        evacuationStreamThread.start();
//
        List<PersonController> personControllers = AutomatedOperations.GetAllPersonControllers();
        System.out.println("Time taken for getting locations of each person and setting up individual controllers: " + Math.subtractExact(System.currentTimeMillis(), hazardDetectionTime));
        EvacuationController ec = new EvacuationController(personControllers, timeStep, count);
        ec.start();
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

                // Once, the space safety value becomes 0, it can't be changed.
                if (space.getSafetyValue() != 0) {
                    //applying an equation to get safety value of a space using temperature value
                    safetyVal = (-0.0131 * sensorValueNumber) + 1.3;
                    if (safetyVal < 0) space.setSafetyValue(0);
                    else if (safetyVal > 1) space.setSafetyValue(1);
                    else space.setSafetyValue(safetyVal);
                }

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
                } else if(space.getTemperatureSensorValue() >= 55){ //check if the temperature of the same location is greater than 55
                    sensor.setValue(true);
                }

                sensorValueBoolean = (boolean) sensor.getValue();
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        sensorValueBoolean + "^^http://www.w3.org/2001/XMLSchema#boolean", System.currentTimeMillis());
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
                } else if(space.getSafetyValue() == 0){
                    sensor.setValue(false);
                }
//                else if (space.getTemperatureSensorValue() >=60 && space.isSmokeExists() && space.getHumiditySensorValue() <= 0.2){ //making a space inaccessible using a specific constraint
//                    sensor.setValue(false);
//                }
                sensorValueBoolean = (boolean) sensor.getValue();
                q = new RdfQuadruple(
                        sensor.getSensorName() + "_Observation",
                        HelpingVariables.sosaPrefix + "hasSimpleResult",
                        sensorValueBoolean + "^^http://www.w3.org/2001/XMLSchema#boolean", System.currentTimeMillis());
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
