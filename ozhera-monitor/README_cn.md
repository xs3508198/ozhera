<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# 概述
   ozhera-monitor 是Apache OzHera(incubating)体系主体工程之一，负责管理监控体系的应用中心、指标监控、指标报警配置、
报警组配置、监控面板展示等多维度的重要功能。

## 涉及的环境
### JDK: java8
### DB: mysql（具体的表结构请参考工程下的sql文件）
### 其他: rocketMq、redis
### 部署
    可通过operator一键部署，请参考同级目录下的ozhera-operator部署过程。

# 应用接入
应用首先通过tpc创建应用，应用信息会通过tpc自动同步到ozhera应用资源池，
用户可将需要监控的应用添加至应用中心的参与列表及关注列表，具体使用过程，
请参考ozhera的使用手册

# **Prometheus**
### 一、版本：V2.37.2 及以上
### 二、配置修改：
##### 1、填充 AlertManager 地址,例如：
alerting:
alertmanagers:
static_configs: - targets: - 127.0.0.1:9093
##### 2、填充报警规则文件路径，例如：
rule_files: - /home/work/app/prometheus_mione/alert.rules

### 三、下载地址：
<https://prometheus.io/docs>

# **Grafana**
### 一、版本：
#### V7.5 及以上
### 二、运行配置依赖：
##### 1、提前申请 api key 并替换 grafana.api.key
##### 2、提前在配置中配置 grafana 地址，并替换 grafana.address
##### 3、提前在 grafana 中配置 Prometheus 数据源，并将数据源名字替换在 grafana.prometheus.datasource
##### 4、提前在 grafana 中生成目录，并调用 grafana 查看目录接口获取目录 Uid，并填充在 grafana.folder.id 与 grafana.folder.uid
##### 5、提前部署好容器和物理机监控图表，并填充配置在 grafana.container.url 与 grafana.host.url
### 三、配置修改：
	domain= //改成本机 ip
	disable_login_form=true
	oauth_auto_login=true

    [auth.generic_oauth]
    enabled = true
    name = TPC
    allow_sign_up = true
    client_id = zgftest
    client_secret = 
    empty_scopes = true
    auth_url = http://xx.xx.xx/user-manage/login
    token_url = http://localhost:8098/oauth/token
    api_url = http://localhost:8098/oauth/api/user

    cookie_samesite = none   //cookie策略
    allow_embedding = true  //允许iframe
    cookie_secure = true    //如果是grafana域名是https的需要设置
    auto_assign_org_role = Editor  //默认编辑权限

    [auth.anonymous]
    enabled = true        //默认匿名登录

### 四、添加插件：
##### 添加 grafana-singlestat-panel 与 grafana-piechart-panel 插件
### 五、下载地址：
<https://grafana.com/docs>

# **AlertManager**
### 一、版本：
V0.22.2 及以上
### 二、配置修改：
1、配置报警触达，例如 webhook 方式：
##### receivers: - name: 'web.hook'
##### webhook_configs:
##### - url: 'http://localhost:8080/api/v1/rules/alert/sendAlert'

### 三、下载地址

# **Node-exporter**
### 一、版本：
##### V1.2.2 及以上
### 二、下载地址
https://github.com/prometheus/node_exporter/releases

# **Cadvisor**
### 一、版本：
##### V0.42.0 及以上
### 二、下载地址
https://github.com/google/cadvisor
