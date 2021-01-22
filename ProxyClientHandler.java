//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.apache.zookeeper.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import org.apache.zookeeper.proto.CreateResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ChannelHandler.Sharable
@SuppressWarnings("unchecked")
public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    int curIndex;
    ByteBuf bmsg;
    Map<String,LinkedBlockingDeque<Integer>> liukongMap = new ConcurrentHashMap<>();
    public ProxyClientHandler() {
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        ByteBuf byteBuf = (ByteBuf)msg;
        System.out.println("客户端" + ctx.channel().remoteAddress() + "发来的消息：" + byteBuf.toString(CharsetUtil.UTF_8));
//        ProxyServer.sendQ.put(msg);
//        Object recvMsg = ProxyServer.recvQ.take();
//        ctx.writeAndFlush(recvMsg);
        this.bmsg = byteBuf.copy();
//        handleData(ctx, msg);

        int contentLen = bmsg.readInt();//去掉请求头
        int two = bmsg.readInt();//登录请求协议版本号，其他请求xid
        int three = bmsg.readInt();//登陆请求timeout，其他请求type
        System.out.println("请求类容长度： "+contentLen+"; two: "+two+"; three: "+three);

        //流控写请求
        if(!liukong(ctx,three)){
            ByteBuf liuR = Unpooled.buffer();
            String path = "请求过多";
            liuR.writeInt(20+path.getBytes().length);
            liuR.writeInt(two);
            liuR.writeLong(0L);
            liuR.writeInt(0);
            SerializeUtils.writeStringToBuffer(path,liuR);
            ctx.writeAndFlush(liuR);
        }else {
            //判断是什么请求命令
            List<Object> list = jugeRequest(ctx, contentLen, two, three, bmsg);
            Object recvMsg = Unpooled.buffer();
            if (list.size() > 1) {
                ByteBuf createRes = Unpooled.buffer();
                StringBuffer paths = new StringBuffer();
                int xid = 0, errorCode = 0;
                long zxid = 0L;
                for (int i = 0; i < list.size(); i++) {
                    Object temp = handleData(ctx, list.get(i));
                    ByteBuf buf = (ByteBuf) temp;
                    buf.readInt();//头
                    xid = buf.readInt();//xid
                    zxid = buf.readLong();//zxid
                    errorCode = buf.readInt();//code
                    if (errorCode == 0)
                        paths.append(SerializeUtils.readStringToBuffer(buf) + "\n");
                }
                createRes.writeInt(20 + paths.toString().getBytes().length);
                createRes.writeInt(xid);
                createRes.writeLong(zxid);
                createRes.writeInt(errorCode);
                SerializeUtils.writeStringToBuffer(paths.toString(), createRes);
                recvMsg = createRes;
            }

            if (three != 1 || list.size() == 1)
                recvMsg = handleData(ctx, msg);

            ctx.writeAndFlush(recvMsg);
        }

    }

    public boolean liukong(ChannelHandlerContext ctx, int three) throws InterruptedException {
        LinkedBlockingDeque liukong = liukongMap.get(ctx.channel().remoteAddress().toString());
        if(liukong==null) {
            liukongMap.put(ctx.channel().remoteAddress().toString(),new LinkedBlockingDeque<>(2));
            liukong = liukongMap.get(ctx.channel().remoteAddress().toString());
        }
        if(three==1||three==2||three==5||three==7){
            if(!liukong.offer(0,1,TimeUnit.MILLISECONDS)){
                liukong.poll(1,TimeUnit.MILLISECONDS);
                return false;
            }
            LinkedBlockingDeque finalLiukong = liukong;
            new Thread(()->{
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    finalLiukong.poll(1,TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        return true;
    }

    public List<Object> jugeRequest(ChannelHandlerContext ctx,int contentLen, int two, int three, ByteBuf content) throws InterruptedException {
        List<Object> sendMsgs = new ArrayList<>();
        if(contentLen==45&&two==0&&three==0)
            System.out.println("createSession...");
        else{
            switch (three){
                case 0:{
                    System.out.println("notification...");
                    break;
                }
                case 1:{
                    System.out.println("create...");
                    ZkCreateRequest request = new ZkCreateRequest();
                    String path = SerializeUtils.readStringToBuffer(content);
                    String data = SerializeUtils.readStringToBuffer(content);
                    data = "proxy_"+data;
                    request.setXid(two);
                    request.setType(three);
                    request.setPath(path);
                    request.setData(data.getBytes());
                    int aclLen = content.readInt();
                    if(aclLen==-1){
                        int flag = content.readInt();
                        request.setFlags(flag);
                    }else {
                        List<ZkAcl> acl = new ArrayList<>();
                        for (int i = 0; i < aclLen; i++) {
                            ZkAcl zkAcl = new ZkAcl();
                            ZkAclId zkAclId = new ZkAclId();
                            int perm = content.readInt();
                            zkAcl.setPerms(perm);
                            zkAclId.setScheme(SerializeUtils.readStringToBuffer(content));
                            zkAclId.setId(SerializeUtils.readStringToBuffer(content));
                            zkAcl.setId(zkAclId);
                            acl.add(zkAcl);
                        }
                        request.setAcl(acl);
                        request.setFlags(content.readInt());
                    }
                    content.writeInt(contentLen+"proxy_".getBytes().length);
                    String[] paths = path.split("/");
                    for (int i = 2; i <= paths.length; i++) {
                        ByteBuf toMsg = Unpooled.buffer();
                        StringBuffer pathChild = new StringBuffer();
                        for (int j = 1; j < i; j++) {
                            pathChild.append("/"+paths[j]);
                        }
                        String pathT = pathChild.toString();
                        request.setPath(pathT);
                        request.setXid(two);
                        toMsg.writeInt(contentLen+"proxy_".getBytes().length-(path.getBytes().length-pathT.getBytes().length));
                        ProxyEnDeCode.createMsgToBuf(request,toMsg);
                        sendMsgs.add(toMsg);
//                        handleData(ctx,toMsg);
                    }
                    break;
                }
                case 2:{
                    System.out.println("delete...");
                    String path = SerializeUtils.readStringToBuffer(content);
                    int version = content.readInt();
                    System.out.println("删除路径： "+path+"  版本： "+version);
                    Stack<String> stack = new Stack<>();
                    Stack<String> childs = findChilds(ctx,path,stack);
                    System.out.println("子节点："+childs.size());
                    while (!childs.empty()){
                        String pathD = childs.pop();
                        ByteBuf bufD = Unpooled.buffer();
                        bufD.writeInt(16+pathD.getBytes().length);//头
                        bufD.writeInt(0);//xid
                        bufD.writeInt(2);//type//delete
                        SerializeUtils.writeStringToBuffer(pathD,bufD);
                        bufD.writeInt(0);//version
                        handleData(ctx,bufD);
                    }
                    break;
                }
                case 3:{
                    System.out.println("exists...");
                    break;
                }
                case 4:{
                    System.out.println("getData...");
                    break;
                }
                case 5:{
                    System.out.println("setData...");
                    String path = SerializeUtils.readStringToBuffer(content);
                    String data = SerializeUtils.readStringToBuffer(content);
                    int version = content.readInt();
                    System.out.println("path: "+path+"  data: "+data+"  version: "+version);
                    break;
                }
                case 6:{
                    System.out.println("getACL...");
                    break;
                }
                case 7:{
                    System.out.println("setACL...");
                    break;
                }
                case 8:{
                    System.out.println("getChildren...");
                    String p8 = SerializeUtils.readStringToBuffer(content);
                    boolean watch = content.readBoolean();
                    System.out.println("ls路径： "+p8+" watch: "+watch);
                    break;
                }
                case 9:{
                    System.out.println("sync...");
                    break;
                }
                case 11:{
                    System.out.println("ping...");
                    break;
                }
                case 12:{
                    System.out.println("getChildren2...");
                    break;
                }
                case 13:{
                    System.out.println("check...");
                    break;
                }
                case 14:{
                    System.out.println("multi...");
                    break;
                }
                case 100:{
                    System.out.println("auth...");
                    break;
                }
                case 101:{
                    System.out.println("setWatches...");
                    break;
                }
                case 102:{
                    System.out.println("sasl...");
                    break;
                }
                case -10:{
                    System.out.println("createSession...");
                    break;
                }
                case -11:{
                    System.out.println("closeSession...");
                    break;
                }
                case -1:{
                    System.out.println("error...");
                    break;
                }
            }
        }
        return sendMsgs;
    }

    public Stack<String> findChilds(ChannelHandlerContext ctx, String path, Stack<String> stack) throws InterruptedException {
        stack.push(path);
        Stack<String> work = new Stack<>();
        work.push(path);
        while (!work.empty()) {
            String pathH = work.pop();
            ByteBuf lsRequest = Unpooled.buffer();
            lsRequest.writeInt(13 + pathH.getBytes().length);//头
            lsRequest.writeInt(0);//xid
            lsRequest.writeInt(8);//type//getChildren
            SerializeUtils.writeStringToBuffer(pathH, lsRequest);
            lsRequest.writeBoolean(false);
            Object childs = handleData(ctx, lsRequest);
            ByteBuf lsResp = (ByteBuf) childs;
            int contentLen = lsResp.readInt();//头
            int rxid = lsResp.readInt();//xid
            long zxid = lsResp.readLong();//zxid
            int errorCode = lsResp.readInt();//code
            if (errorCode == 0) {
                int childSize = lsResp.readInt();
                System.out.println(path + "的子节点长度： " + childSize);
                if(childSize>0) {
                    List<String> childdd = new ArrayList<>();
                    for (int i = 0; i < childSize; i++) {
//                    System.out.print(SerializeUtils.readStringToBuffer(lsResp) + " ");
//                stack.push(path+"/"+SerializeUtils.readStringToBuffer(lsResp));
                        childdd.add(pathH + "/" + SerializeUtils.readStringToBuffer(lsResp));
                    }
                    childdd.stream().forEach(e -> {
                        stack.push(e);
                        work.push(e);
                    });
                }
            }
        }
        return stack;
    }

    public Object handleData(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        String client = ctx.channel().remoteAddress().toString();
        if(!ProxyServer.queue.containsKey(client+"send")) {
            LinkedBlockingDeque<Object> sendQ = new LinkedBlockingDeque<>();
            LinkedBlockingDeque<Object> recvQ = new LinkedBlockingDeque<>();
            int size = ProxyServer.hostAndPort.length;
            int from = (curIndex+2)%size;
            int to = from+1;
            curIndex = from;
            String[] hostAndPort = new String[]{ProxyServer.hostAndPort[from],ProxyServer.hostAndPort[to]};
            ProxyServer.startClient(sendQ, recvQ, hostAndPort);
            ProxyServer.queue.put(client+"send", sendQ);
            ProxyServer.queue.put(client+"recv", recvQ);
        }
        ProxyServer.queue.get(client+"send").offer(msg);
        Object recvMsg = ProxyServer.queue.get(client+"recv").poll(5, TimeUnit.SECONDS);
        if(recvMsg==null) {//5s超时没返回关掉连接
            ctx.close();
            ProxyServer.queue.remove(client+"send");
            ProxyServer.queue.remove(client+"recv");
//            handleData(ctx, msg);
        }
        return recvMsg;
    }

    public void channelReadComplete(ChannelHandlerContext ctx) {
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    public static void main(String[] args) {
        System.out.println("proxy".getBytes().length);
    }
}
