package com.bxwbb.cd;

public class CollisionData {

    public Contact[] contacts = new Contact[100];
    public int contactCount = 0;
    public int contactsLeft = 100;
    public double friction;
    public double restitution;
    public double tolerance;

    public CollisionData() {
        for (int i = 0; i < contactsLeft; i++) {
            contacts[i] = new Contact();
        }
    }

    public void addContacts(int count) {
        contactsLeft -= count;
        contactCount += count;
    }

    public void reset(int maxContacts) {
        contactsLeft = maxContacts;
        contactCount = 0;
    }

    public boolean hasMoreContacts() {
        return contactsLeft > 0;
    }
}
