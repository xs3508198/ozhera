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
package org.apache.ozhera.monitor.service.api;

import com.google.gson.JsonArray;
import org.apache.ozhera.monitor.dao.model.GrafanaTemplate;
import org.apache.ozhera.monitor.service.model.MutiGrafanaResponse;

import java.util.List;
import java.util.Map;

/**
 * @author zhangxiaowei6
 */
public interface GrafanaServiceExtension {

    void setFolderData(String area);

    void setContainerAndHostUrl(String area);

    MutiGrafanaResponse requestGrafanaTemplate(String group, String title, String area, GrafanaTemplate template, List<String> funcList);

    String innerRequestGrafanaStr(String area, String title, String containerName, String group, GrafanaTemplate template, String application);

    Map<String, Object> getTemplateVariables(String folderId, String group, String title, String folderUid, String grafanaUrl, String containerName, String area, String application);

    String getFinalData(String data, String url, String apiKey, String method, String title, String panelIdList, boolean isFaas, String originTitle);

    String getDashboardLastVersion(String dashboardId);

    String requestGrafana(String serverType, String appName, String area);

    Map<String, String> beforeRequestGrafana(String area, String title);

    String innerRequestGrafana(String data, String url, String apiKey, String method);

    void getCustomPanels(String grafanaStr, JsonArray basicPanels, int basicDiyPanelGirdPosY, String title, String panelIdList);

    String isGrafanaDataJson(String jobJson);

}
