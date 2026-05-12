package com.bxwbb.phy.particle;

import java.util.List;

public interface ParticleContactGenerator {

    int addContact(List<ParticleContact> contacts, int contactIndex, int limit);

}
