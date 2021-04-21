package com.zuibuxing.springcloudstarteretcd.etcd;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicIntProperty;
import com.netflix.loadbalancer.ServerListUpdater;
import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 如果使用 eureka， 有两种，更新策略PollingServerListUpdater 和  EurekaNotificationServerListUpdater，默认是 PollingServerListUpdater，
 * 这里参照一下两个类做整合，把两种实现的代码copy过来，实现轮询和通知并存的方式
 *
 * @author huangting
 */
public class EtcdNotificationServerListUpdater implements ServerListUpdater {

    private static final Logger logger = LoggerFactory.getLogger(EtcdNotificationServerListUpdater.class);


    private static long LISTOFSERVERS_CACHE_UPDATE_DELAY = 1000; // msecs;
    private static int LISTOFSERVERS_CACHE_REPEAT_INTERVAL = 5 * 1000; // msecs;

    private static class LazyHolder {
        private final static String CORE_THREAD = "EtcdNotificationServerListUpdater.ThreadPoolSize";
        private final static String QUEUE_SIZE = "EtcdNotificationServerListUpdater.queueSize";
        private final static EtcdNotificationServerListUpdater.LazyHolder SINGLETON = new EtcdNotificationServerListUpdater.LazyHolder();

        private final DynamicIntProperty poolSizeProp = new DynamicIntProperty(CORE_THREAD, 2);
        private final DynamicIntProperty queueSizeProp = new DynamicIntProperty(QUEUE_SIZE, 1000);
        private final ThreadPoolExecutor defaultServerListUpdateExecutor;
        private final Thread shutdownThread;

        private final Thread _shutdownThread;
        private final ScheduledThreadPoolExecutor _serverListRefreshExecutor;

        static {

        }

        private void shutdownExecutorPool() {
            if (_serverListRefreshExecutor != null) {
                _serverListRefreshExecutor.shutdown();

                if (_shutdownThread != null) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(_shutdownThread);
                    } catch (IllegalStateException ise) { // NOPMD
                        // this can happen if we're in the middle of a real
                        // shutdown,
                        // and that's 'ok'
                    }
                }

            }
        }

        private LazyHolder() {

            int coreSize = getCorePoolSize();
            ThreadFactory factory = (new ThreadFactoryBuilder())
                    .setNameFormat("YouxinPollingServerListUpdater-%d")
                    .setDaemon(true)
                    .build();
            _serverListRefreshExecutor = new ScheduledThreadPoolExecutor(coreSize, factory);
            poolSizeProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    _serverListRefreshExecutor.setCorePoolSize(poolSizeProp.get());
                }

            });
            _shutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Shutting down the Executor Pool for YouxinPollingServerListUpdater");
                    shutdownExecutorPool();
                }
            });
            Runtime.getRuntime().addShutdownHook(_shutdownThread);


            int corePoolSize = getCorePoolSize();
            defaultServerListUpdateExecutor = new ThreadPoolExecutor(
                    corePoolSize,
                    corePoolSize * 5,
                    0,
                    TimeUnit.NANOSECONDS,
                    new ArrayBlockingQueue<Runnable>(queueSizeProp.get()),
                    new ThreadFactoryBuilder()
                            .setNameFormat("EtcdNotificationServerListUpdater-%d")
                            .setDaemon(true)
                            .build()
            );

            poolSizeProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    int corePoolSize = getCorePoolSize();
                    defaultServerListUpdateExecutor.setCorePoolSize(corePoolSize);
                    defaultServerListUpdateExecutor.setMaximumPoolSize(corePoolSize * 5);
                }
            });

            shutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Shutting down the Executor for EtcdNotificationServerListUpdater");
                    try {
                        defaultServerListUpdateExecutor.shutdown();
                        Runtime.getRuntime().removeShutdownHook(shutdownThread);
                    } catch (Exception e) {
                        // this can happen in the middle of a real shutdown, and that's ok.
                    }
                }
            });

            Runtime.getRuntime().addShutdownHook(shutdownThread);
        }

        private int getCorePoolSize() {
            int propSize = poolSizeProp.get();
            if (propSize > 0) {
                return propSize;
            }
            return 2; // default
        }
    }

    private final long initialDelayMs;
    private final long refreshIntervalMs;
    private volatile ScheduledFuture<?> scheduledFuture;


    private static ScheduledThreadPoolExecutor getRefreshExecutor() {
        return EtcdNotificationServerListUpdater.LazyHolder.SINGLETON._serverListRefreshExecutor;
    }

    public static ExecutorService getDefaultRefreshExecutor() {
        return EtcdNotificationServerListUpdater.LazyHolder.SINGLETON.defaultServerListUpdateExecutor;
    }

    String clientName;

    final AtomicBoolean updateQueued = new AtomicBoolean(false);
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicLong lastUpdated = new AtomicLong(System.currentTimeMillis());


    private final ExecutorService refreshExecutor;

    private EtcdClient client;

    private String etcdBasePath;
    ResponsePromise<EtcdKeysResponse> promise;
    ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> listener;


    public EtcdNotificationServerListUpdater(EtcdClient client, EtcdServerProperties etcdServerProperties, IClientConfig clientConfig) {
        this(client, getDefaultRefreshExecutor(), etcdServerProperties, clientConfig);
    }

    public EtcdNotificationServerListUpdater(EtcdClient client, ExecutorService refreshExecutor, EtcdServerProperties etcdServerProperties, IClientConfig clientConfig) {
        clientName = clientConfig.getClientName();
        this.client = client;
//        promise = client.getDir(basePath).recursive().waitForChange().consistent().send();
        this.etcdBasePath = etcdServerProperties.getBasePath();
        this.refreshExecutor = refreshExecutor;
        this.initialDelayMs = LISTOFSERVERS_CACHE_UPDATE_DELAY;
        this.refreshIntervalMs = getRefreshIntervalMs(clientConfig);
    }


    @Override
    public void start(UpdateAction updateAction) {
        if (isActive.compareAndSet(false, true)) {

            final Runnable wrapperRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isActive.get()) {
                        if (scheduledFuture != null) {
                            scheduledFuture.cancel(true);
                        }
                        return;
                    }
                    try {
                        updateAction.doUpdate();
                        lastUpdated.set(System.currentTimeMillis());
                    } catch (Exception e) {
                        logger.warn("Failed one update cycle", e);
                    }
                }
            };

            scheduledFuture = getRefreshExecutor().scheduleWithFixedDelay(
                    wrapperRunnable,
                    initialDelayMs,
                    refreshIntervalMs,
                    TimeUnit.MILLISECONDS
            );


         /*   try {
                promise = client.getDir(etcdBasePath).recursive().waitForChange().consistent().send();
            } catch (IOException e) {
                logger.error("listening etcd registry exception,reListen start", e);
            }*/

            listener = new ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse>() {
                @Override
                public void onResponse(ResponsePromise<EtcdKeysResponse> response) {
                    try {
                        EtcdKeysResponse.EtcdNode node = response.get().getNode();
                        String shortKey = StringUtils
                                .substringAfterLast(node.getKey(), "/");
                        if (!updateQueued.compareAndSet(false, true)) {  // if an update is already queued
                            logger.info("an update action is already queued, returning as no-op");
                            return;
                        }

                        if (!refreshExecutor.isShutdown()) {

                            if (clientName.equals(shortKey)) {
                                try {
                                    refreshExecutor.submit(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                updateAction.doUpdate();
                                                lastUpdated.set(System.currentTimeMillis());
                                            } catch (Exception e) {
                                                logger.warn("Failed to update serverList", e);
                                            } finally {
                                                updateQueued.set(false);
                                            }
                                        }
                                    });  // fire and forget
                                } catch (Exception e) {
                                    logger.warn("Error submitting update task to executor, skipping one round of updates", e);
                                    updateQueued.set(false);  // if submit fails, need to reset updateQueued to false
                                }

                            }
                        } else {
                            logger.debug("stopping EurekaNotificationServerListUpdater, as refreshExecutor has been shut down");
                            stop();
                        }
                    } catch (Exception e) {
                        logger.error("listening etcd registry exception,reListen start", e);
                    }
                    //每次执行完监听动作后，会把该次监听移除，所以要重新注册监听
                    startListener(this);
                }
            };

            startListener(listener);

        } else {
            logger.info("Update listener already registered, no-op");
        }
    }


    public void startListener(ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> listener) {
        try {
            promise = client.getDir(etcdBasePath).recursive().waitForChange().consistent().send();
            promise.addListener(listener);
        } catch (Exception e) {
            startListener(listener);
            logger.error("listening etcd registry exception,reListen start", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (isActive.compareAndSet(true, false)) {
            if (promise != null && listener != null) {
                promise.removeListener(listener);
            }
        } else {
            logger.info("Not currently active, no-op");
        }
    }

    @Override
    public String getLastUpdate() {
        return new Date(lastUpdated.get()).toString();
    }

    @Override
    public long getDurationSinceLastUpdateMs() {
        return System.currentTimeMillis() - lastUpdated.get();
    }

    @Override
    public int getNumberMissedCycles() {
        return 0;
    }

    @Override
    public int getCoreThreads() {
        if (isActive.get()) {
            if (refreshExecutor != null && refreshExecutor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) refreshExecutor).getCorePoolSize();
            }
        }
        return 0;
    }


    private static long getRefreshIntervalMs(IClientConfig clientConfig) {
        return clientConfig.get(CommonClientConfigKey.ServerListRefreshInterval, LISTOFSERVERS_CACHE_REPEAT_INTERVAL);
    }
}
