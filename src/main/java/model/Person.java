package model;

public class Person {

    private String person;
    private String location;
    private String id;

    public Person(String person, String location, String id) {
        this.person = person;
        this.location = location;
        this.id = id;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
