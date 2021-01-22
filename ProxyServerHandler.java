//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.zookeeper.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

@ChannelHandler.Sharable
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private LinkedBlockingDeque<Object> sendQ;
    private LinkedBlockingDeque<Object> recvQ;
    public ProxyServerHandler(LinkedBlockingDeque<Object> sendQ,LinkedBlockingDeque<Object> recvQ) {
        this.sendQ = sendQ;
        this.recvQ = recvQ;
    }

    public ProxyServerHandler(){}

    public void channelActive(ChannelHandlerContext ctx) throws IOException, InterruptedException {
        System.out.println("Client :" + ctx);
        (new Thread(() -> {
            while(true) {
                try {
//                    Object sendMsg = ProxyServer.sendQ.take();
//                    ctx.writeAndFlush(sendMsg);
                    Object sendMsg = sendQ.take();
                    ctx.writeAndFlush(sendMsg);
                } catch (InterruptedException var2) {
                    var2.printStackTrace();
                }
            }
        })).start();
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        ByteBuf byteBuf = (ByteBuf)msg;
        System.out.println("服务器" + ctx.channel().remoteAddress() + "端发来的消息：" + byteBuf.toString(CharsetUtil.UTF_8));
//        ProxyServer.recvQ.put(msg);
        recvQ.put(msg);


        ByteBuf bmsg = byteBuf.copy();
        System.out.println("返回消息长度："+bmsg.capacity());
        if(bmsg.capacity()>=24) {
            int contentLen = bmsg.readInt();//去掉请求头
            int two = bmsg.readInt();//登录请求协议版本号，其他请求xid
            long three = bmsg.readLong();//登陆请求timeout，其他请求type
            int errorCode = bmsg.readInt();
            String path = SerializeUtils.readStringToBuffer(bmsg);
            System.out.println("响应内容长度：" + contentLen + "  two: " + two + "  three: " + three + " errorCode: " + errorCode + " path: " + path);
        }
    }
}
