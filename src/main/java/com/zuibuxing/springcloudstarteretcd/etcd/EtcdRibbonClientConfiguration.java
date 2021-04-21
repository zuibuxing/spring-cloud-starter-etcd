/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zuibuxing.springcloudstarteretcd.etcd;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListUpdater;
import mousio.etcd4j.EtcdClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.ribbon.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;


/**
 * Preprocessor that configures defaults for etcd-discovered ribbon clients. Such as:
 * <code>@zone</code>, NIWSServerListClassName, DeploymentContextBasedVipAddresses,
 * NFLoadBalancerRuleClassName, NIWSServerListFilterClassName and more
 *
 * @author huangting
 */
@Configuration
@EnableConfigurationProperties(EtcdServerProperties.class)
public class EtcdRibbonClientConfiguration {

    private static final Log log = LogFactory.getLog(EtcdRibbonClientConfiguration.class);

    @RibbonClientName
    private String serviceId = "client";


    @Autowired
    private PropertiesFactory propertiesFactory;

    public EtcdRibbonClientConfiguration() {
    }


    @Bean
    @ConditionalOnMissingBean
    public EtcdClient etcdClient(EtcdServerProperties etcdServerProperties) {
        String[] uriStrs = etcdServerProperties.getEtcdUrl().split(",");
        URI[] uris = new URI[uriStrs.length];
        for (int i = 0; i < uriStrs.length; i++) {
            uris[i] = URI.create(uriStrs[i]);
        }
        return new EtcdClient(uris);
    }


    @Bean
    @ConditionalOnMissingBean
    public EtcdHttpClient etcdHttpClient(EtcdClient etcdClient, EtcdServerProperties etcdServerProperties) {

        System.out.println("etcdHttpClient init ");

        return new EtcdHttpClient(etcdClient, etcdServerProperties);
    }


    @Bean
    @ConditionalOnMissingBean
    public ServerList<?> ribbonServerList(EtcdHttpClient etcdHttpClient, IClientConfig config) {


        System.out.println("ribbonServerList init ");

        if (this.propertiesFactory.isSet(ServerList.class, serviceId)) {
            return this.propertiesFactory.get(ServerList.class, config, serviceId);
        }
        EtcdServerList serverList = new EtcdServerList(etcdHttpClient, config);
        return serverList;
    }

    @Bean
    @ConditionalOnMissingBean
    public ServerListUpdater ribbonServerListUpdater(EtcdClient client, EtcdServerProperties etcdServerProperties, IClientConfig config) {
        return new EtcdNotificationServerListUpdater(client, etcdServerProperties, config);
    }


}
