/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ozhera.monitor.service.alertmanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.ozhera.monitor.bo.UserInfo;
import org.apache.ozhera.monitor.result.Result;
import org.apache.ozhera.monitor.service.model.PageData;
import org.apache.ozhera.monitor.service.model.alarm.duty.DutyInfo;

import java.util.List;
import java.util.Set;

/**
 * @author gaoxihui
 * @date 2022/11/7 2:57 下午
 */
public interface AlertManager {

    public Result addRule(JsonObject param,String identifyId, String user);

    public Result editRule(Integer alertId,JsonObject param,String identifyId, String user);

    public Result delRule(Integer alertId,String identifyId, String user);

    public Result enableRule(Integer alertId,Integer pauseStatus,String identifyId, String user);

    public Result  queryRuels(JsonObject params, String identifyId, String user);

    public Result<JsonElement>  getAlarmRuleRemote(Integer alarmId,Integer iamId,String user);

    public Result updateAlarm(Integer alarmId,Integer iamId,String user,String body);

    Result<JsonElement> addAlarmGroup(JsonObject params, String identifyId, String user);

    Result<JsonElement> searchAlarmGroup(String alarmGroup,String identifyId,String user);

    Result<PageData> searchAlertTeam(String name,String note,String manager,String oncallUser,String service,Integer iamId,String user,Integer page_no,Integer page_size);

    Result<PageData> queryEvents(String user, Integer treeId, String alertLevel, Long startTime, Long endTime, Integer pageNo, Integer pageSize, JsonObject labels);

    Result<PageData> queryLatestEvents(Set<Integer> treeIdSet, String alertStat, String alertLevel, Long startTime, Long endTime, Integer pageNo, Integer pageSize, JsonObject labels);

    Result<JsonObject> getEventById(String user, Integer treeId, String eventId);

    Result<PageData> getAlertGroupPageData(String user, String name, int pageNo, int pageSize);

    Result<JsonObject> resolvedEvent(String user, Integer treeId, String alertName, String comment, Long startTime, Long endTime);

    Result<PageData<List<UserInfo>>> searchUser(String user, String searchName, int pageNo, int pageSize);

    Result<JsonObject> createAlertGroup(String user, String name, String note, String chatId, List<Long> memberIds, DutyInfo dutyInfo);

    Result<JsonObject> getAlertGroup(String user, long id);

    Result<JsonObject> editAlertGroup(String user, long id, String name, String note, String chatId, List<Long> memberIds,DutyInfo dutyInfo);

    Result<JsonObject> deleteAlertGroup(String user, long id);

    Result<JsonElement> dutyInfoList(String user, long id,Long start,Long end);

    Integer getDefaultIamId();
}
