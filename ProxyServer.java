//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.zookeeper.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxyServer {
    static LinkedBlockingQueue<Object> sendQ = new LinkedBlockingQueue<>();
    static LinkedBlockingQueue<Object> recvQ = new LinkedBlockingQueue<>();
    static Map<String, LinkedBlockingDeque<Object>> queue
            = new ConcurrentHashMap<>();
    static String[] hostAndPort;
    public ProxyServer() {
    }

    public static void main(String[] args) throws Exception {
        hostAndPort = args;
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        ((ServerBootstrap)((ServerBootstrap)serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)).option(ChannelOption.SO_BACKLOG, 12)).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ProxyClientHandler());
        System.out.println("...ProxyServer is Ready...");
        ChannelFuture sf = serverBootstrap.bind(8888).sync();
        System.out.println("....ProxyServer is Start....");
        sf.channel().closeFuture().sync();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    private static void startClient(String[] args) {
        (new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            ((Bootstrap)((Bootstrap)b.group(group)).channel(NioSocketChannel.class)).handler(new ProxyServerHandler());
            System.out.println("...ProxyClient is Ready...");
            ChannelFuture cf = null;

            try {
                cf = b.connect(args[0], Integer.parseInt(args[1])).sync();
                System.out.println("...ProxyClient is Start...");
            } catch (InterruptedException var6) {
                var6.printStackTrace();
            }

            try {
                cf.channel().closeFuture().sync();
            } catch (InterruptedException var5) {
                var5.printStackTrace();
            }

        })).start();
    }

    public static void startClient(LinkedBlockingDeque<Object> sendQ,LinkedBlockingDeque<Object> recvQ,String[] hostAndPort) {
        (new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            ((Bootstrap)((Bootstrap)b.group(group)).channel(NioSocketChannel.class)).handler(new ProxyServerHandler(sendQ,recvQ));
            System.out.println("...ProxyClient is Ready...");
            ChannelFuture cf = null;

            try {
                cf = b.connect(hostAndPort[0], Integer.parseInt(hostAndPort[1])).sync();
                System.out.println("...ProxyClient is Start...");
            } catch (InterruptedException var6) {
                var6.printStackTrace();
            }

            try {
                cf.channel().closeFuture().sync();
            } catch (InterruptedException var5) {
                var5.printStackTrace();
            }

        })).start();
    }
}
