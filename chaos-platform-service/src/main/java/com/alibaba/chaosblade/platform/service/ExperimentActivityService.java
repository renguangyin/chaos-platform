/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.chaosblade.platform.service;

import com.alibaba.chaosblade.platform.service.model.experiment.activity.ExperimentActivity;

import java.util.List;

/**
 * @author yefei
 */
public interface ExperimentActivityService {

    /**
     *
     * @param ExperimentId
     * @return
     */
    ExperimentActivity selectByExperimentId(Long ExperimentId);

    /**
     *
     * @param ExperimentId
     * @return
     * @throws Exception
     */
    List<ExperimentActivity> selectAttackByExperimentId(Long ExperimentId);
}
