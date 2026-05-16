package org.soft.softrear.service.agent;

import org.soft.softrear.pojo.dto.dify.DifyDecisionResult;

import java.util.Map;

public interface AgentDecisionService {

    DifyDecisionResult runDecision(Map<String, Object> inputs, String userSeed);

    Map<String, Object> probeStatus();

    String getProviderName();
}
