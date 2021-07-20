# spring-cloud-starter-etcd
基于Etcd做注册中心的starter依赖包，引入maven依赖，配置Etcd地址即可使用，整合ribbon

# 比较糙的一版starter，用的是低版本的etcd的依赖jar包，高版本的改了一半忘保存了，到时候再加分支，这里先给出这版的使用方式吧

1、在gateway服务的pom文件中引入这个jar包

```
<dependency>
   <groupId>com.zuibuxing</groupId>
   <artifactId>spring-cloud-starter-etcd</artifactId>
   <version>0.0.1-SNAPSHOT</version>
</dependency>
```

2、在application-test.yml或者properties中添加 etcd的配置，这里给出yml的配置方式，剩下的同理了
``` 
etcd:
  # 需要监听的服务列表，可以改造成拉取未配置的服务名后，自动添加到需要监听的map中
  listen:
    serviceNames: risk-api-gateway,crawlers,antifraud-risk-datacenter,antifraud-risk-gateway,babel-read,antifraud-risk-verify
  
  notify:
    # 通知哪个服务，etcd中别的服务节点列表有变动，目前没用上
    serviceName: risk-api-gateway
    # 哪个服务的变动不需要通知
    exclude:
      serviceNames: risk-api-gateway
  registry:
    # 服务注册到etcd的哪个路径下
    basepath: /nodes/access
  # etcd的地址
  node:
    list: http://172.16.1.125:2379
```
