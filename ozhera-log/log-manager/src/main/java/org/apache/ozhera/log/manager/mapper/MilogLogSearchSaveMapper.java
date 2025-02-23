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
package org.apache.ozhera.log.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ozhera.log.manager.model.dto.SearchSaveDTO;
import org.apache.ozhera.log.manager.model.pojo.MilogLogSearchSaveDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper
 * </p>
 *
 * @author wanghaoyang
 * @since 2022-03-29
 */
public interface MilogLogSearchSaveMapper extends BaseMapper<MilogLogSearchSaveDO> {

    List<MilogLogSearchSaveDO> selectByStoreId(@Param(value = "storeId") Long storeId, @Param(value = "startIndex") Integer startIndex, @Param(value = "pageSize") Integer pageSize);

    Long countByStoreId(@Param(value = "storeId") Long storeId);

    Long countByStoreAndName(@Param(value = "name") String name, @Param(value = "creator") String creator);

    int removeById(@Param(value = "id") Long id);

    List<SearchSaveDTO> selectByCreator(@Param(value = "creator") String creator, @Param(value = "sort") Integer sort);

    Integer getMaxOrder(@Param(value = "creator") String creator, @Param(value = "sort") Integer sort);

    Integer isMyFavouriteStore(@Param(value = "creator") String creator, @Param(value = "storeId") Long storeId);

    Integer isMyFavouriteTail(@Param(value = "creator") String creator, @Param(value = "tailId") Long tailId);

    List<MilogLogSearchSaveDO> getAll();
}
