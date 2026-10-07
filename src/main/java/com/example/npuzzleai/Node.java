package com.example.npuzzleai;

import java.util.Objects;
import java.util.Vector;

public class Node {
    public State state;
    public int f;
    public int g;
    public int h;
    public int cost;
    public Node parent;

    public Node(State state, int cost) {
        this.state = Objects.requireNonNull(state, "state");
        this.cost = cost;
    }

    public boolean equals(Node node) {
        return node != null && state.equals(node.state);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Node other)) return false;
        return state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    public int estimate(State goalState) {
        return state.estimate(goalState);
    }

    public Vector<Node> successors() {
        Vector<Node> nodes = new Vector<>();
        for (State successor : state.successors()) {
            nodes.add(new Node(successor, 1));
        }
        return nodes;
    }
}
