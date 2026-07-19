package com.splitsettle.model;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private final int id;
    private final String name;
    private final List<User> members = new ArrayList<>();

    public Group(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public List<User> getMembers() { return members; }

    public void addMember(User u) { members.add(u); }
}
