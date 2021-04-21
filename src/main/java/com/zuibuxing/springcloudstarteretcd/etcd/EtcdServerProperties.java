package com.zuibuxing.springcloudstarteretcd.etcd;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huangting
 */
@ConfigurationProperties
public class EtcdServerProperties {

    @Value("${etcd.node.list}")
    private String etcdUrl;

    @Value("${etcd.listen.serviceNames}")
    private String listenServiceNames;

    @Value("${etcd.registry.basepath}")
    private String basePath;


    public String getEtcdUrl() {
        return etcdUrl;
    }

    public void setEtcdUrl(String etcdUrl) {
        this.etcdUrl = etcdUrl;
    }

    public String getListenServiceNames() {
        return listenServiceNames;
    }

    public void setListenServiceNames(String listenServiceNames) {
        this.listenServiceNames = listenServiceNames;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
