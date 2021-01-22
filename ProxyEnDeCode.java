package org.apache.zookeeper.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public class ProxyEnDeCode {
    public static void createMsgToBuf(ZkCreateRequest msg, ByteBuf out) {
        out.writeInt(msg.getXid());
        out.writeInt(msg.getType());
        String path = msg.getPath();
        SerializeUtils.writeStringToBuffer(path, out);
        byte [] bytes = msg.getData();
        SerializeUtils.writeByteArrToBuffer(bytes, out);
        List<ZkAcl> list = msg.getAcl();
        if (list == null) {
            out.writeInt(-1);
        }else {
            out.writeInt(list.size());
            for(ZkAcl acl : list){
                out.writeInt(acl.getPerms());
                ZkAclId zkAclId = acl.getId();
                SerializeUtils.writeStringToBuffer(zkAclId.getScheme(), out);
                SerializeUtils.writeStringToBuffer(zkAclId.getId(), out);
            }
        }
        out.writeInt(msg.getFlags());
    }

}
