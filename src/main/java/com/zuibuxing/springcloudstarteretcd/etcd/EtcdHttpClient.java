package com.zuibuxing.springcloudstarteretcd.etcd;

import com.alibaba.fastjson.JSON;
import com.netflix.loadbalancer.Server;
import lombok.extern.slf4j.Slf4j;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * @author
 */

@Slf4j
public class EtcdHttpClient {

    private EtcdClient client;

    private String basePath;

    public EtcdHttpClient(EtcdClient etcdClient, EtcdServerProperties etcdServerProperties) {
        this.client = etcdClient;
        this.basePath = etcdServerProperties.getBasePath();
    }


    /**
     * 获取指定服务下的节点信息
     *
     * @param serviceName 发布平台中的服务名称
     * @return
     * @throws Exception
     */
    public List<Server> get(String serviceName) {

        List<Server> serverList = new ArrayList<>();

        EtcdKeysResponse etcdKeysResponse = null;
        try {
            etcdKeysResponse = client.getDir(getPath(serviceName)).consistent().send().get();
        } catch (TimeoutException e) {
            log.error("request etcd timeout", e);
        } catch (Exception e) {
            log.error("request etcd exception", e);
        }

        if (etcdKeysResponse == null) {
            return serverList;
        }
        try {
            String value = etcdKeysResponse.getNode().getValue();
            List<String> list = JSON.parseObject(value, List.class);
            list.forEach(node -> {
                String[] split = node.split(":");
                serverList.add(createServer(split));
            });
        } catch (Exception e) {
            log.error("generate serviceInstance model exception", e);
        }
        return serverList;
    }

    private String getPath(String serviceName) {
        return basePath + "/" + serviceName;
    }

    private Server createServer(String[] serverArray) {
        return new Server(serverArray[0], Integer.parseInt(serverArray[1]));
    }

}
