/**
 * @(#)ProviderConfig.java, 6月 09, 2021.
 * <p>
 * Copyright 2021 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.badger.core.bootstrap.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.badger.core.bootstrap.NettyClient;
import org.badger.core.bootstrap.NettyServer;
import org.badger.core.bootstrap.confg.NettyServerConfig;
import org.badger.core.bootstrap.confg.ZkConfig;
import org.badger.core.bootstrap.entity.RpcProvider;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author liubin01
 */

@Slf4j
@Component
public class ProviderConfig implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private static final Map<String, Object> rpcMap = new HashMap<>();

    @Bean
    @ConditionalOnMissingBean(NettyServerConfig.class)
    @ConfigurationProperties(prefix = "rpc")
    public NettyServerConfig nettyServerConfig() {
        return new NettyServerConfig();
    }


    @Bean
    @ConditionalOnMissingBean(ZkConfig.class)
    @ConfigurationProperties(prefix = "zk")
    public ZkConfig zkConfig() {
        return new ZkConfig();
    }

    @Bean
    @ConditionalOnBean(ZkConfig.class)
    public CuratorFramework curatorFramework(ZkConfig zkConfig) {

        RetryPolicy retryPolicy
                = new RetryNTimes(zkConfig.getMaxRetries(), zkConfig.getSleepMsBetweenRetries());
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient(zkConfig.getAddress(), retryPolicy);
        zkClient.start();

        return zkClient;
    }

    @Bean
    public NettyClient nettyClient() {
        return new NettyClient();
    }


    @Bean
    @ConditionalOnBean(value = {NettyServerConfig.class, CuratorFramework.class})
    public NettyServer nettyServer(NettyServerConfig nettyServerConfig, CuratorFramework client) throws Throwable {
        Map<String, Object> objectMap = applicationContext.getBeansWithAnnotation(RpcProvider.class);
        Map<String, Object> serviceMap = new HashMap<>();
        Map<Pair<String, String>, Object> servicePairMap = new HashMap<>();
        objectMap.forEach((k, v) -> {
            Class<?> clazz = v.getClass();
            Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> inter : interfaces) {
                String interfaceName = inter.getName();
                serviceMap.put(interfaceName, v);
                servicePairMap.put(ImmutablePair.of(interfaceName, k), v);
            }
        });
        NettyServer nettyServer = new NettyServer(nettyServerConfig, serviceMap, servicePairMap);
        nettyServer.start();
        client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(String.format("/%s/%s", nettyServerConfig.getServiceName(), getIpAddress()));
        return nettyServer;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static String getIpAddress() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (!netInterface.isLoopback() && !netInterface.isVirtual() && netInterface.isUp()) {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ip = addresses.nextElement();
                        if (ip instanceof Inet4Address) {
                            return ip.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP地址获取失败" + e.toString());
        }
        return "";
    }

}