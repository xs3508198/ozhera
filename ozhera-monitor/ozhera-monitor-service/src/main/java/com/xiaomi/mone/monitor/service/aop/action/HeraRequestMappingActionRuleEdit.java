/*
 *  Copyright (C) 2020 Xiaomi Corporation
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.xiaomi.mone.monitor.service.aop.action;

import com.xiaomi.mone.monitor.bo.HeraReqInfo;
import com.xiaomi.mone.monitor.bo.OperLogAction;
import com.xiaomi.mone.monitor.dao.model.AlarmStrategy;
import com.xiaomi.mone.monitor.dao.model.AppAlarmRule;
import com.xiaomi.mone.monitor.dao.model.HeraOperLog;
import com.xiaomi.mone.monitor.result.Result;
import com.xiaomi.mone.monitor.service.aop.helper.HeraRequestMappingActionStrategyHelper;
import com.xiaomi.mone.monitor.service.model.prometheus.AlarmRuleData;
import lombok.extern.slf4j.Slf4j;
import org.nutz.json.Json;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: zgf1
 * @date: 2022/1/13 16:01
 */
@Slf4j
@Service
public class HeraRequestMappingActionRuleEdit extends HeraRequestMappingActionArg2<HttpServletRequest, AlarmRuleData, Result>{

    @Autowired
    private HeraRequestMappingActionStrategyHelper heraRequestMappingActionStrategyHelper;

    @Override
    public void beforeAction(HttpServletRequest arg1, AlarmRuleData arg2, HeraReqInfo heraReqInfo) {
        if (arg2 == null || arg2.getStrategyId() == null) {
            return;
        }
        Pair<AlarmStrategy, List<AppAlarmRule>> pair = heraRequestMappingActionStrategyHelper.queryStrategyById(arg2.getStrategyId());
        HeraOperLog operLog = new HeraOperLog();
        operLog.setOperName(heraReqInfo.getUser());
        operLog.setModuleName(heraReqInfo.getModuleName());
        operLog.setInterfaceName(heraReqInfo.getInterfaceName());
        operLog.setInterfaceUrl(heraReqInfo.getReqUrl());
        operLog.setAction(OperLogAction.STRATEGY_EDIT.getAction());
        heraRequestMappingActionStrategyHelper.saveHeraOperLogs(pair, operLog, heraReqInfo);
        if (operLog.getId() != null) {
            heraReqInfo.setOperLog(operLog);
        }
    }

    @Override
    public void afterAction(HttpServletRequest arg1, AlarmRuleData arg2, HeraReqInfo heraReqInfo, Result result) {
        if (arg2 == null || arg2.getStrategyId() == null) {
            return;
        }
        Integer strategyId = heraRequestMappingActionStrategyHelper.getStrategyIdByRuleId(arg2.getStrategyId());
        if (strategyId == null) {
            return;
        }
        Pair<AlarmStrategy, List<AppAlarmRule>> pair = heraRequestMappingActionStrategyHelper.queryStrategyById(strategyId);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("code", result.getCode());
        resultMap.put("message", result.getMessage());
        heraReqInfo.getOperLog().setResultDesc(Json.toJson(resultMap));
        heraRequestMappingActionStrategyHelper.saveHeraOperLogs(pair, heraReqInfo.getOperLog(), heraReqInfo);
    }
}
