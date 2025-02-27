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

package org.apache.ozhera.monitor.dao;

import org.apache.ozhera.monitor.dao.model.HeraOperLog;
import lombok.extern.slf4j.Slf4j;
import org.nutz.dao.Dao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Slf4j
@Repository
public class HeraOperLogDao {


    @Autowired
    private Dao dao;

    public HeraOperLog getById(Integer id) {
        return dao.fetch(HeraOperLog.class, id);
    }

    public boolean insertOrUpdate(HeraOperLog log) {
        log.setUpdateTime(new Date());
        if (log.getCreateTime() == null) {
            log.setCreateTime(new Date());
        }
        if (log.getOperName() == null) {
            log.setOperName("");
        }
        if (log.getLogType() == null) {
            log.setLogType(0);
        }
        if (log.getDataType() == null) {
            log.setDataType(0);
        }
        if (log.getBeforeParentId() == null) {
            log.setBeforeParentId(0);
        }
        if (log.getAfterParentId() == null) {
            log.setAfterParentId(0);
        }
        try {
            return dao.insertOrUpdate(log) != null;
        } catch (Exception e) {
            HeraOperLogDao.log.error("HeraOperLog表插入或更新异常； log={}", log, e);
            return false;
        }
    }

    public void batchInsert(List<HeraOperLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (HeraOperLog log : logs) {
            log.setUpdateTime(new Date());
            if (log.getCreateTime() == null) {
                log.setCreateTime(new Date());
            }
            if (log.getOperName() == null) {
                log.setOperName("");
            }
            if (log.getLogType() == null) {
                log.setLogType(0);
            }
            if (log.getDataType() == null) {
                log.setDataType(0);
            }
            if (log.getBeforeParentId() == null) {
                log.setBeforeParentId(0);
            }
            if (log.getAfterParentId() == null) {
                log.setAfterParentId(0);
            }
        }
        try {
            dao.fastInsert(logs);
        } catch (Exception e) {
            HeraOperLogDao.log.error("HeraOperLog表批量插入异常； logs={}", logs, e);
        }
    }

    public boolean updateById(HeraOperLog log) {
        if (log.getUpdateTime() == null) {
            log.setUpdateTime(new Date());
        }
        try {
            return  dao.updateIgnoreNull(log) > 0;
        } catch (Exception e) {
            HeraOperLogDao.log.error("HeraOperLog表更新异常； template={}", log, e);
            return false;
        }
    }

    public boolean deleteById(Integer id) {
        try {
            return  dao.delete(HeraOperLog.class, id) > 0;
        } catch (Exception e) {
            log.error("HeraOperLog表删除异常； id={}", id, e);
            return false;
        }
    }

}
