package com.zuibuxing.springcloudstarteretcd.etcd;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @author
 */
@Slf4j
@Configuration
@Component
public class EtcdInit {

    @Autowired
    private EtcdHttpClient etcdHttpClient;

    @Autowired
    private EtcdClient client;


    @Value("${etcd.listen.serviceNames}")
    private String listenServiceNames;


    @Value("${etcd.registry.basepath}")
    private String basePath;


    @Value("${etcd.notify.exclude.serviceNames}")
    private String excludeServiceNames;

    private Set<String> serviceNameSet;

    @PostConstruct
    public void init() {
        log.info("init start.......");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
                new ThreadFactoryBuilder()
                        .setNameFormat("ServiceNodePull-%d")
                        .setDaemon(true)
                        .build());


        //项目启动后拉取etcd注册信息到本地，之后每隔5s更新一次
        scheduler.scheduleWithFixedDelay(this::refreshRegistry, 0, 5, TimeUnit.SECONDS);

        //項目启动后监听etcd
        serviceNameSet = Arrays.stream(getListenServiceNames()).collect(Collectors.toSet());

        scheduler.schedule(() -> startListener(), 0, TimeUnit.SECONDS);

    }


    private void refreshRegistry() {
        for (String serviceName : serviceNameSet) {
            refreshRegistry(serviceName);
        }
    }

    private void refreshRegistry(String serviceName) {
        log.info("refresh registry starting...... ");

        //若目前的监听服务中心不包含这次拉取的服务列表，且拉取到的实例数量大于0，加入到监听列表中
//        if (!serviceNameSet.contains(serviceName) && riskServiceInstance.getNodeInstances().size() > 0) {
//            serviceNameSet.add(serviceName);
//        }
        log.info("refresh registry end......");
    }

    private String[] getListenServiceNames() {
        return StringUtils.isEmpty(listenServiceNames) ? new String[0] : listenServiceNames.split(",");
    }

    public void startListener() {
        ResponsePromise<EtcdKeysResponse> promise;
        try {
            promise = client.getDir(basePath).recursive().waitForChange().consistent().send();
            promise.addListener(listener -> {
                try {
                    EtcdKeysResponse response = listener.get();
                    EtcdKeysResponse.EtcdNode node = response.getNode();
                    String shortKey = org.apache.commons.lang3.StringUtils
                            .substringAfterLast(node.getKey(), "/");
                    if (serviceNameSet.contains(shortKey) && !Arrays.asList(excludeServiceNames.split(",")).contains(shortKey)) {
                        log.info("listening etcd registry changed,serviceName:{}", shortKey);
                        refreshRegistry(shortKey);
                    }
                } catch (Exception e) {
                    log.error("listening etcd registry exception,reListen start", e);
                }
                //每次执行完监听动作后，会把该次监听移除，所以要重新注册监听
                startListener();
            });
        } catch (Exception e) {
            startListener();
            log.error("listening etcd registry exception,reListen start", e);
        }
    }


    public void addFetchSercice(String serviceId) {
        refreshRegistry(serviceId);
    }


}
