package io.aurora.contract;

/**
 * Who is acting. A human request is USER; an agent acting for a human is AGENT.
 */
public record Actor(Type type, String agentId) {

    public enum Type { USER, AGENT }

    public static Actor user() {
        return new Actor(Type.USER, null);
    }

    public static Actor agent(String agentId) {
        return new Actor(Type.AGENT, agentId);
    }
}
