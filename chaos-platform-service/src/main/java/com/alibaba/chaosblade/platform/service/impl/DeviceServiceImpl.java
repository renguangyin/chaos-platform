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

package com.alibaba.chaosblade.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import com.alibaba.chaosblade.platform.cmmon.enums.DeviceStatus;
import com.alibaba.chaosblade.platform.cmmon.enums.DeviceType;
import com.alibaba.chaosblade.platform.cmmon.exception.BizException;
import com.alibaba.chaosblade.platform.cmmon.exception.ExceptionMessageEnum;
import com.alibaba.chaosblade.platform.cmmon.utils.JsonUtils;
import com.alibaba.chaosblade.platform.cmmon.utils.Preconditions;
import com.alibaba.chaosblade.platform.dao.QueryWrapperBuilder;
import com.alibaba.chaosblade.platform.dao.mapper.DeviceNodeMapper;
import com.alibaba.chaosblade.platform.dao.model.*;
import com.alibaba.chaosblade.platform.dao.page.PageUtils;
import com.alibaba.chaosblade.platform.dao.repository.*;
import com.alibaba.chaosblade.platform.service.DeviceService;
import com.alibaba.chaosblade.platform.service.model.device.*;
import com.alibaba.chaosblade.platform.service.model.tools.ToolsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.chaosblade.platform.cmmon.exception.ExceptionMessageEnum.DEVICE_NOT_FOUNT;

/**
 * @author yefei
 */
@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceNodeRepository deviceNodeRepository;

    @Autowired
    private DeviceNodeMapper deviceNodeMapper;

    @Autowired
    private DevicePodRepository devicePodRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationGroupRepository applicationGroupRepository;

    @Autowired
    private ApplicationDeviceRepository applicationDeviceRepository;

    @Autowired
    private ProbesRepository probesRepository;

    @Autowired
    private ToolsRepository toolsRepository;

    @Override
    @Transactional
    public void deviceRegister(DeviceRegisterRequest deviceRegisterRequest) {
        Long appId = applicationRepository.selectOneByNamespaceAndAppName(deviceRegisterRequest.getNamespace(), deviceRegisterRequest.getAppInstance())
                .map(ApplicationDO::getId)
                .orElseGet(() -> {
                    ApplicationDO applicationDO = ApplicationDO.builder()
                            .namespace(deviceRegisterRequest.getNamespace())
                            .appName(deviceRegisterRequest.getAppInstance())
                            .build();
                    applicationRepository.insert(applicationDO);
                    return applicationDO.getId();
                });

        Long groupId = applicationGroupRepository.selectOneByAppIdAndGroupName(appId, deviceRegisterRequest.getAppGroup())
                .map(ApplicationGroupDO::getId)
                .orElseGet(() -> {
                    ApplicationGroupDO applicationGroupDO = ApplicationGroupDO.builder()
                            .appId(appId)
                            .appName(deviceRegisterRequest.getAppInstance())
                            .groupName(deviceRegisterRequest.getAppGroup())
                            .build();

                    applicationGroupRepository.insert(applicationGroupDO);
                    return applicationGroupDO.getId();
                });


        DeviceType deviceType = DeviceType.transByCode(deviceRegisterRequest.getDeviceType());
        Preconditions.checkNotNull(deviceType, ExceptionMessageEnum.DEVICE_TYPE_NOT_FOUNT, deviceRegisterRequest.getDeviceType());

        switch (deviceType) {
            case NODE:
                // todo
                break;
            case POD:
                // todo
                break;
            case HOST:
                DeviceDO device = deviceRepository.selectOneByUnique(deviceType.getCode(), deviceRegisterRequest.getHostName(), deviceRegisterRequest.getIp())
                        .orElseGet(() -> {
                            DeviceDO deviceDO = DeviceDO.builder()
                                    .ip(deviceRegisterRequest.getIp())
                                    .hostname(deviceRegisterRequest.getHostName())
                                    .version(deviceRegisterRequest.getVersion())
                                    .cpuCore(deviceRegisterRequest.getCpuCore())
                                    .status(DeviceStatus.ONLINE.getStatus())
                                    .memorySize(deviceRegisterRequest.getMemorySize() == null ? null : deviceRegisterRequest.getMemorySize().intValue())
                                    .uptime(deviceRegisterRequest.getUptime())
                                    .installMode(deviceRegisterRequest.getInstallMode())
                                    .type(deviceRegisterRequest.getDeviceType())
                                    .build();
                            deviceRepository.insert(deviceDO);

                            applicationDeviceRepository.insert(ApplicationDeviceDO.builder()
                                    .namespace(deviceRegisterRequest.getNamespace())
                                    .appId(appId)
                                    .appName(deviceRegisterRequest.getAppInstance())
                                    .groupId(groupId)
                                    .groupName(deviceRegisterRequest.getAppGroup())
                                    .deviceId(deviceDO.getId())
                                    .build());

                            return deviceDO;
                        });

                try {
                    long id = Long.parseLong(deviceRegisterRequest.getAgentId());
                    probesRepository.updateByPrimaryKey(id,
                            ProbesDO.builder()
                                    .deviceId(device.getId())
                                    .version(deviceRegisterRequest.getVersion())
                                    .build());
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
                break;
        }

        probesRepository.selectByHost(deviceRegisterRequest.getIp()).ifPresent(probesDO -> {
            probesRepository.updateByHost(deviceRegisterRequest.getIp(), ProbesDO.builder()
                    .hostname(deviceRegisterRequest.getHostName())
                    .status(DeviceStatus.ONLINE.getStatus())
                    .build());
        });

    }

    @Override
    public List<DeviceResponse> getMachinesForHost(DeviceRequest deviceRequest) {
        if (deviceRequest.getProbeId() != null) {
            PageUtils.startPage(deviceRequest);
            ProbesDO probesDO = probesRepository.selectById(deviceRequest.getProbeId())
                    .orElseThrow(() -> new BizException(ExceptionMessageEnum.PROBES_NO_FOUND));
            return deviceRepository.selectById(probesDO.getDeviceId())
                    .map(deviceDO -> CollUtil.newArrayList(covert(deviceDO)))
                    .orElse(new ArrayList<>());
        }

        PageUtils.startPage(deviceRequest);

        List<DeviceDO> devices = deviceRepository.selectMachines(DeviceDO.builder()
                .ip(deviceRequest.getIp())
                .hostname(deviceRequest.getHostname())
                .isExperimented(deviceRequest.getChaosed())
                .status(deviceRequest.getStatus())
                .type(DeviceType.HOST.getCode())
                .build());

        if (CollectionUtil.isEmpty(devices)) {
            return Collections.emptyList();
        }

        return devices.stream().map(deviceDO -> covert(deviceDO)).collect(Collectors.toList());
    }

    public DeviceResponse covert(DeviceDO deviceDO) {
        DeviceResponse deviceResponse = new DeviceResponse();
        deviceResponse.setVersion(deviceDO.getVersion())
                .setDeviceId(deviceDO.getId())
                .setIp(deviceDO.getIp())
                .setHostname(deviceDO.getHostname())
                .setStatus(deviceDO.getStatus())
                .setChaosed(deviceDO.getIsExperimented())
                .setCreateTime(deviceDO.getGmtCreate())
                .setHeartbeatTime(deviceDO.getLastOnlineTime())
                .setChaosTime(deviceDO.getLastExperimentTime())
                .setTaskId(deviceDO.getLastTaskId())
                .setChaostools(toolsRepository.selectByDeviceId(deviceDO.getId()).stream().map(toolsDO ->
                        ToolsResponse.builder()
                                .name(toolsDO.getName())
                                .version(toolsDO.getVersion())
                                .build()
                ).collect(Collectors.toList()))
                .setTaskStatus(deviceDO.getLastTaskStatus());

        return deviceResponse;
    }

    @Override
    public KubernetesStatisticsResponse getKubernetesTotalStatistics() {
        return KubernetesStatisticsResponse.builder()
                .nodes(deviceRepository.selectCount(DeviceDO.builder()
                        .type(DeviceType.NODE.getCode())
                        .build()))
                .pods(deviceRepository.selectCount(DeviceDO.builder()
                        .type(DeviceType.POD.getCode())
                        .build()))
                .cluster(Optional.ofNullable(deviceNodeMapper.selectCount(QueryWrapperBuilder.<DeviceNodeDO>build()
                        .groupBy("cluster_id"))).orElse(0))
                .containers(devicePodRepository.selectList(DevicePodDO.builder().build()).stream().flatMap(devicePodDO ->
                        Optional.ofNullable(devicePodDO.getContainers()).map(
                                containers -> {
                                    try {
                                        return Stream.of(JsonUtils.reader(ContainerBO[].class).readValue(containers));
                                    } catch (IOException e) {
                                        return Stream.empty();
                                    }
                                }
                        ).orElse(Stream.empty())
                ).count())
                .build();
    }

    @Override
    public List<DeviceNodeResponse> getMachinesForNode(DevicePodsRequest deviceNodeRequest) {

        PageUtils.startPage(deviceNodeRequest);
        List<DeviceNodeDO> deviceNodeDOS = deviceNodeRepository.selectList(DeviceNodeDO.builder()
                .clusterName(deviceNodeRequest.getClusterName())
                .nodeName(deviceNodeRequest.getNodeName())
                .build()
        );
        if (CollUtil.isEmpty(deviceNodeDOS)) {
            return Collections.emptyList();
        }

        Map<Long, DeviceDO> deviceDOMap = deviceRepository.selectBatchIds(deviceNodeDOS.stream().map(DeviceNodeDO::getDeviceId)
                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(DeviceDO::getId, u -> u));

        return deviceNodeDOS.stream().map(deviceNodeDO ->
                {
                    DeviceNodeResponse deviceNodeResponse = new DeviceNodeResponse();

                    deviceNodeResponse.setClusterName(deviceNodeDO.getClusterName())
                            .setNodeName(deviceNodeDO.getNodeName())
                            .setNodeIp(deviceNodeDO.getNodeIp())
                            .setNodeVersion(deviceNodeDO.getNodeVersion())
                            .setDeviceId(deviceNodeDO.getDeviceId())
                            .setStatus(deviceDOMap.get(deviceNodeDO.getDeviceId()).getStatus())
                            .setChaosed(deviceDOMap.get(deviceNodeDO.getDeviceId()).getIsExperimented())
                            .setCreateTime(deviceDOMap.get(deviceNodeDO.getDeviceId()).getGmtCreate())
                            .setHeartbeatTime(deviceDOMap.get(deviceNodeDO.getDeviceId()).getLastOnlineTime())
                            .setChaosTime(deviceDOMap.get(deviceNodeDO.getDeviceId()).getLastExperimentTime())
                            .setTaskId(deviceDOMap.get(deviceNodeDO.getDeviceId()).getLastTaskId())
                            .setTaskStatus(deviceDOMap.get(deviceNodeDO.getDeviceId()).getLastTaskStatus());

                    return deviceNodeResponse;
                }
        ).collect(Collectors.toList());
    }

    @Override
    public List<DevicePodResponse> getMachinesForPod(DevicePodRequest devicePodRequest) {

        List<DeviceNodeDO> deviceNodeDOS = deviceNodeRepository.selectList(DeviceNodeDO.builder()
                .clusterName(devicePodRequest.getClusterName())
                .nodeName(devicePodRequest.getNodeName())
                .build()
        );
        Map<Long, DeviceNodeDO> nodeMap = deviceNodeDOS.stream().collect(Collectors.toMap(DeviceNodeDO::getId, v -> v));

        PageUtils.startPage(devicePodRequest);
        List<DevicePodDO> devicePodDOS = devicePodRepository.selectList(DevicePodDO.builder()
                .podName(devicePodRequest.getPodName())
                .podIp(devicePodRequest.getPodId())
                .build());

        Map<Long, DeviceDO> deviceDOMap = deviceRepository.selectBatchIds(devicePodDOS.stream().map(DevicePodDO::getDeviceId)
                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(DeviceDO::getId, u -> u));


        return devicePodDOS.stream().map(devicePodDO ->
                {
                    DevicePodResponse devicePodResponse = new DevicePodResponse();

                    devicePodResponse.setClusterName(nodeMap.get(devicePodDO.getNodeId()).getClusterName())
                            .setNodeName(nodeMap.get(devicePodDO.getNodeId()).getNodeName())
                            .setNodeIp(nodeMap.get(devicePodDO.getNodeId()).getNodeIp())
                            .setNodeVersion(nodeMap.get(devicePodDO.getNodeId()).getNodeVersion())
                            .setPodName(devicePodDO.getPodName())
                            .setPodIp(devicePodDO.getPodIp())
                            .setDeviceId(devicePodDO.getId())
                            .setStatus(deviceDOMap.get(devicePodDO.getDeviceId()).getStatus())
                            .setChaosed(deviceDOMap.get(devicePodDO.getDeviceId()).getIsExperimented())
                            .setCreateTime(deviceDOMap.get(devicePodDO.getDeviceId()).getGmtCreate())
                            .setHeartbeatTime(deviceDOMap.get(devicePodDO.getDeviceId()).getLastOnlineTime())
                            .setChaosTime(deviceDOMap.get(devicePodDO.getDeviceId()).getLastExperimentTime())
                            .setTaskId(deviceDOMap.get(devicePodDO.getDeviceId()).getLastTaskId())
                            .setTaskStatus(deviceDOMap.get(devicePodDO.getDeviceId()).getLastTaskStatus());

                    return devicePodResponse;
                }
        ).collect(Collectors.toList());
    }

    @Override
    public DeviceResponse banMachine(DeviceRequest deviceRequest) {

        deviceRepository.updateByPrimaryKey(deviceRequest.getDeviceId(), DeviceDO.builder()
                .status(DeviceStatus.FORBIDDEN.getStatus())
                .build());

        return getMachinesById(deviceRequest);
    }

    @Override
    public DeviceResponse unbanMachine(DeviceRequest deviceRequest) {
        DeviceDO deviceDO = deviceRepository.selectById(deviceRequest.getDeviceId())
                .orElseThrow(() -> new BizException(DEVICE_NOT_FOUNT));

        if (DateUtil.date().offset(DateField.MINUTE, -1).after(deviceDO.getLastOnlineTime())) {
            deviceRepository.updateByPrimaryKey(deviceRequest.getDeviceId(), DeviceDO.builder()
                    .status(DeviceStatus.OFFLINE.getStatus())
                    .build());
        } else {
            deviceRepository.updateByPrimaryKey(deviceRequest.getDeviceId(), DeviceDO.builder()
                    .status(DeviceStatus.ONLINE.getStatus())
                    .build());
        }

        return getMachinesById(deviceRequest);
    }


    @Override
    public DeviceResponse getMachinesById(DeviceRequest deviceRequest) {
        DeviceDO deviceDO = deviceRepository.selectById(deviceRequest.getDeviceId()).orElseThrow(
                () -> new BizException(DEVICE_NOT_FOUNT)
        );

        List<ToolsDO> toolsDOS = toolsRepository.selectByDeviceId(deviceRequest.getDeviceId());
        List<ToolsResponse> tools = toolsDOS.stream().map(toolsDO ->
                ToolsResponse.builder()
                        .name(toolsDO.getName())
                        .version(toolsDO.getVersion())
                        .build()
        ).collect(Collectors.toList());

        DeviceResponse deviceResponse = new DeviceResponse();
        deviceResponse.setVersion(deviceDO.getVersion())
                .setDeviceId(deviceDO.getId())
                .setIp(deviceDO.getIp())
                .setHostname(deviceDO.getHostname())
                .setStatus(deviceDO.getStatus())
                .setChaosed(deviceDO.getIsExperimented())
                .setCreateTime(deviceDO.getGmtCreate())
                .setHeartbeatTime(deviceDO.getLastOnlineTime())
                .setChaosTime(deviceDO.getLastExperimentTime())
                .setTaskId(deviceDO.getLastTaskId())
                .setTaskStatus(deviceDO.getLastTaskStatus())
                .setOriginal(deviceRequest.getOriginal())
                .setChaostools(tools);
        return deviceResponse;
    }
}