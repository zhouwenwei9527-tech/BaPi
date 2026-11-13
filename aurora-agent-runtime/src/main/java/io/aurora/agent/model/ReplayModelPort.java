package io.aurora.agent.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic replay of recorded model turns. The agent decision sequence
 * can be reproduced without calling a real model (PRD §8, AC-14).
 */
public class ReplayModelPort implements ModelPort {

    private final Deque<ModelTurn> turns;

    public ReplayModelPort(List<ModelTurn> turns) {
        this.turns = new ArrayDeque<>(turns);
    }

    @Override
    public String provider() {
        return "replay";
    }

    @Override
    public ModelTurn complete(ModelRequest request) {
        ModelTurn turn = turns.pollFirst();
        if (turn == null) {
            return ModelTurn.text("录制已结束：没有更多模型响应。", 0);
        }
        return turn;
    }

    public int remaining() {
        return turns.size();
    }
}
