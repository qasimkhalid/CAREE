package operations;

import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import helper.HelpingVariables;
import model.CareeInfModel;
import model.graph.ODPair;
import model.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AutomatedOperations {
    /**
     * This methods runs a Sparql query and filters out the nodes and edges, along
     * with their other characteristics,
     * such as area, and accommodation capacity.
     *
     * @param infModel   - Inference Model
     * @param odPairList - List of ODPair objects (already loaded)
     * @return List of Space objects.
     */
    public static List<Space> getSpaceInfo(InfModel infModel, List<ODPair> odPairList) {
        List<String> spaceInfoQueryResult = Sparql.getSPARQLQueryResult(infModel,
                "data/Queries/sparql/GetSpaceInfo.txt");
        List<Space> list = new ArrayList<>();
        Space s;
        for (int i = 0; i < spaceInfoQueryResult.size() - 2; i += 3) {
            if (spaceInfoQueryResult.get(i).contains("_")) {
                s = new Space(spaceInfoQueryResult.get(i), spaceInfoQueryResult.get(i + 1),
                        spaceInfoQueryResult.get(i + 2), "edge");

                String[] tokens = spaceInfoQueryResult.get(i).split("_");
                List<ODPair> odPairMatchedList = odPairList.stream()
                        .filter(x -> (x.getOrigin().equals(tokens[0]) && x.getDestination()
                                .equals("https://w3id.org/sbeo/example/officescenario#" + tokens[1]))
                                || (x.getOrigin().equals("https://w3id.org/sbeo/example/officescenario#" + tokens[1])
                                && x.getDestination().equals(tokens[0])))
                        .collect(Collectors.toList());

                if (!odPairMatchedList.isEmpty() && odPairList.size() != 2) {
                    for (ODPair odPair : odPairMatchedList) {
                        odPair.setSpace(s);
                    }
                } else {
                    System.out.println("Origin and Destination not found in odPairList for injecting a common edge");
                }

                list.add(s);
            } else
                list.add(new Space(spaceInfoQueryResult.get(i), spaceInfoQueryResult.get(i + 1),
                        spaceInfoQueryResult.get(i + 2), "node"));
        }
        return list;
    }

    /**
     * This methods returns a list of OD Pairs (i.e., edges), along with their other
     * characteristics, such as cost,
     * corresponding space, both nodes (i.e., origin and destination) to which it is
     * connected.
     *
     * @param infModel - Inference Model
     * @return list of ODPair objects
     */
    public static List<ODPair> getCostOfAllODPairs(InfModel infModel) {
        List<String> odPairQueryResult = null;
        try {
            odPairQueryResult = Sparql.getSPARQLQueryResult(infModel, "data/Queries/sparql/FindO-DPairs.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<ODPair> list = new ArrayList<>();
        ODPair odp;
        for (int i = 0; i < odPairQueryResult.size() - 2; i += 3) {
            odp = new ODPair(odPairQueryResult.get(i), odPairQueryResult.get(i + 1), odPairQueryResult.get(i + 2));
            list.add(odp);
            odp = new ODPair(odPairQueryResult.get(i + 1), odPairQueryResult.get(i), odPairQueryResult.get(i + 2));
            list.add(odp);
        }
        return list;
    }

    /**
     * This method sets up the persons in the building who might later be used as a
     * moving agents
     *
     * @param infModel              - Inference Model
     * @param personsCount          - Total number of persons.
     * @param personsWithWheelChair - Number of persons having a mobility impairment
     */
    public static void setPeopleInBuilding(InfModel infModel, int personsCount, int personsWithWheelChair) {
        long initialTime = System.currentTimeMillis();
        int personsWithWheelChairCounter = 0;
        Resource personInstance;
        Resource spaceInstance;

        List<String> availableSpaces = getAvailableNodes(infModel);

        for (int i = 1; i <= personsCount; i++) {
            personInstance = ResourceFactory.createResource(HelpingVariables.exPrefix + "Person" + i);

            if (personsWithWheelChairCounter < personsWithWheelChair) {
                infModel.add(personInstance, HelpingVariables.rdfType,
                        HelpingVariables.NonMotorisedWheelchairPersonClass);
                personsWithWheelChairCounter++;
            } else {
                infModel.add(personInstance, HelpingVariables.rdfType, HelpingVariables.personClass);
            }
            infModel.addLiteral(personInstance, HelpingVariables.id, i);
            infModel.add(personInstance, HelpingVariables.motionState, HelpingVariables.motionStateResting);

            // Choosing a random space as a person location
            String rs = availableSpaces.get(MathOperations.getRandomNumber(availableSpaces.size()));
            spaceInstance = ResourceFactory.createResource(rs);
            infModel.add(personInstance, HelpingVariables.locatedIn, spaceInstance);
            infModel.addLiteral(personInstance, HelpingVariables.atTime, initialTime);
        }
    }


    /**
     * This method returns a list of available spaces in the building.
     *
     * @param infModel - Inference Model
     * @return A list of strings having all available spaces. It needs to be
     * processed before using it.
     */
    public static List<String> getAvailableSpaces(InfModel infModel) {
        return Sparql.getSPARQLQueryResult(infModel,
                "data/Queries/sparql/FindAllAvailableSpacesInBuilding.txt");
    }

    /**
     * This method returns a list of available nodes in the model.graph.
     * THIS METHOD IS AS SAME AS getAvailableSpaces method, but a bit better in
     * terms of results.
     *
     * @param infModel - Inference Model
     * @return A list of strings having all available nodes (i.e., spaces). It needs
     * to be processed before using it.
     */
    public static List<String> getAvailableNodes(InfModel infModel) {
        return Sparql.getSPARQLQueryResult(infModel,
                "data/queries/sparql/FindAllAvailableNodesInBuilding.txt");
    }

//    public static void updateModelWhenPersonFinishesRoute(List<PersonMovementInformation> list) {
//        Resource personInstance;
//        for (PersonMovementInformation personMovementInformation : list) {
//            personInstance = CareeInfModel.Instance().getResource(personMovementInformation.getPerson());
//            Resource destinationInstance = CareeInfModel.Instance()
//                    .getResource(personMovementInformation.getDestination());
//            CareeInfModel.Instance().remove(personInstance, HelpingVariables.motionState,
//                    HelpingVariables.motionStateWalking);
//            CareeInfModel.Instance().add(personInstance, HelpingVariables.locatedIn, destinationInstance);
//            CareeInfModel.Instance().add(personInstance, HelpingVariables.motionState,
//                    HelpingVariables.motionStateResting);
//        }
//    }

    public static void updatePersonLocationOnSuccessfulPathTraversal(String personName, String personOldLocation, String personNewLocation) {
        Resource personResource = CareeInfModel.Instance().getResource(personName);
        Resource locationResource = CareeInfModel.Instance().getResource(personOldLocation);
        CareeInfModel.Instance().remove(personResource, HelpingVariables.locatedIn, locationResource);
        locationResource = CareeInfModel.Instance().getResource(personNewLocation);
        CareeInfModel.Instance().add(personResource, HelpingVariables.locatedIn, locationResource);
    }

    /**
     * This method assumes the person has been updated in the model with its
     * newLocation
     *
     * @param personName name of the person.
     */
    public static void updateModelWhenPersonCompletesPath(String personName) {
        Resource personResource = CareeInfModel.Instance().getResource(personName);
        CareeInfModel.Instance().remove(personResource, HelpingVariables.motionState,
                HelpingVariables.motionStateWalking);
        CareeInfModel.Instance().add(personResource, HelpingVariables.motionState, HelpingVariables.motionStateResting);
    }

    public static long getODPairCostInSeconds(String origin, String destination) throws Exception {
        Optional<ODPair> odPair = HelpingVariables.odPairList.stream()
                .filter(x -> x.getOrigin().equals(origin) && x.getDestination().equals(destination)).findFirst();

        if (odPair.isPresent()) {
            return odPair.get().getCost() * 1000;
        } else {
            throw new Exception("Origin and Destination not found in odPairList");
        }
    }

    public static List<String> getExits() {
        return Sparql.getSPARQLQueryResult(CareeInfModel.Instance().getInfModel(), "data/queries/sparql/GetExits.txt");
    }
}