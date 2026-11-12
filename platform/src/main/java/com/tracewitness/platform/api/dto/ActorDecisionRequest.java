package com.tracewitness.platform.api.dto;

public class ActorDecisionRequest {
    private String decision;
    private String actor;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }
}
