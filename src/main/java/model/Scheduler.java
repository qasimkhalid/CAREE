package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Scheduler {

    private List<PersonMovementTime> movingPersons = new ArrayList<>();

    public Scheduler() {
    }

    public List<PersonMovementTime> getMovingPersons() {
        return movingPersons;
    }

    public void setMovingPersons( List<PersonMovementTime> movingPersons ) {
        this.movingPersons = movingPersons;
    }

    public void addMovingPerson(String person, long timeRequired, long timeElapsed, String origin, String destination, String id){
        PersonMovementTime m = new PersonMovementTime(person, timeRequired, timeElapsed, origin, destination, id);
        this.movingPersons.add(m);
    }

    public void removeMovingPerson(List<PersonMovementTime> list){
        for(PersonMovementTime l :list){
            this.movingPersons.remove(l);
        }
    }

    public List<PersonMovementTime> update( long deltaTime, List<PersonMovementTime> list){
        List<PersonMovementTime> personWhoFinishedMovement = new ArrayList<>();
        for(PersonMovementTime l : list){
            long getPersonTimeElapsed = l.getTimeElapsed();
            long getPersonTimeRequired = l.getTimeRequired();
            if (getPersonTimeRequired > getPersonTimeElapsed){
                l.setTimeElapsed(getPersonTimeElapsed + deltaTime);
            } else {
                personWhoFinishedMovement.add(l);
            }
        }
        removeMovingPerson(personWhoFinishedMovement);
        return personWhoFinishedMovement;
    }

    public void addMovingPerson( PersonMovementTime person ) {
        this.movingPersons.add(person);
    }
}