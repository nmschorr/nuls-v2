/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.pocnetwork.message.v1;

import io.nuls.base.RPCUtil;
import io.nuls.base.data.NulsHash;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.manager.ChainManager;
import io.nuls.pocnetwork.constant.NetworkCmdConstant;
import io.nuls.pocnetwork.model.ConsensusKeys;
import io.nuls.pocnetwork.model.ConsensusNet;
import io.nuls.pocnetwork.model.message.ConsensusIdentitiesMsg;
import io.nuls.pocnetwork.service.ConsensusNetService;
import io.nuls.pocnetwork.service.NetworkService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nuls.pocnetwork.constant.NetworkCmdConstant.POC_IDENTITY_MESSAGE;


/**
 * 处理收到的{@link ConsensusIdentitiesMsg},解析身份
 *
 * @author lanjinsheng
 * @version 1.0
 * @date 2019-10-17
 */
@Component("ConsensusIdentityProcessorV1")
public class ConsensusIdentityProcessor implements MessageProcessor {
    @Autowired
    private ChainManager chainManager;
    Map<String, Integer> MSG_HASH_MAP = new ConcurrentHashMap<>();
    int WARNING_HASH_SIZE = 10000;
    int MAX_HASH_SIZE = 12000;
    Map<String, Integer> MSG_HASH_MAP_TEMP = new ConcurrentHashMap<>();
    @Autowired
    ConsensusNetService consensusNetService;
    @Autowired
    NetworkService networkService;

    @Override
    public String getCmd() {
        return POC_IDENTITY_MESSAGE;
    }

    public String getLast8ByteByNulsHash(NulsHash hash) {
        byte[] out = new byte[8];
        byte[] in = hash.getBytes();
        int copyEnd = in.length;
        System.arraycopy(in, (copyEnd - 8), out, 0, 8);
        return HexUtil.encode(out);
    }

    public boolean duplicateMsg(ConsensusIdentitiesMsg message) {
        String simpleHash = getLast8ByteByNulsHash(message.getMsgHash());
        if (null != MSG_HASH_MAP.get(simpleHash)) {
            return true;
        }
        if (MSG_HASH_MAP.size() > MAX_HASH_SIZE) {
            MSG_HASH_MAP.clear();
            MSG_HASH_MAP.putAll(MSG_HASH_MAP_TEMP);
            MSG_HASH_MAP_TEMP.clear();
        }
        if (MSG_HASH_MAP.size() > WARNING_HASH_SIZE) {
            MSG_HASH_MAP_TEMP.put(simpleHash, 1);
        }
        MSG_HASH_MAP.put(simpleHash, 1);
        return false;
    }

    @Override
    public void process(int chainId, String nodeId, String msgStr) {
        Chain chain = chainManager.getChainMap().get(chainId);
        chain.getLogger().debug("recv csIndentity msg");
        ConsensusIdentitiesMsg message = RPCUtil.getInstanceRpcStr(msgStr, ConsensusIdentitiesMsg.class);
        chain.getLogger().debug("msgHash={} recv from node={}", message.getMsgHash().toHex(), nodeId);
        if (duplicateMsg(message)) {
            chain.getLogger().debug("msgHash={} is duplicate,drop msg", message.getMsgHash().toHex());
            return;
        }

//        接受身份信息，判断是否有自己的包，有解析，1.解析后判断是否在自己连接列表内，存在则跃迁，不存在进行第三步。
//        同时 广播转发/ 普通节点直接转发 【ConsensusIdentityProcessor】
//        message.getConsensusNet()
        ConsensusKeys consensusKeys = consensusNetService.getSelfConsensusKeys(chainId);
        if (null == consensusKeys) {
            //转发消息
        } else {
            ConsensusNet consensusNet = message.getDecryptConsensusNet(consensusKeys.getPrivKey(), consensusKeys.getPubKey());
            if (null == consensusNet) {
                return;
            }
            //解出的包,需要判断对方是否共识节点
            ConsensusNet dbConsensusNet = consensusNetService.getConsensusNode(chainId, consensusNet);
            if (null == dbConsensusNet) {
                //这边需要注意，此时如果共识节点列表里面还没有该节点，可能就会误判，所以必须保障 在收到消息时候，共识列表里已经存在该消息。
                chain.getLogger().error("nodeId = {} not in consensus Group", consensusNet.getNodeId());
                return;
            }
            //之前没有节点的ip，或者没有连接上，则可以重新告知对方自己的身份
            boolean sendIdentityMsg = (null == dbConsensusNet.getNodeId() || !dbConsensusNet.getNodeId().equals(consensusNet.getNodeId()) || !dbConsensusNet.isHadConnect());
            //可能没公钥，更新下公钥信息
            String consensusNetNodeId = consensusNet.getNodeId();
            dbConsensusNet.setNodeId(consensusNetNodeId);
            dbConsensusNet.setPubKey(consensusNet.getPubKey());
            chain.getLogger().debug("begin connect {}", consensusNetNodeId);
            boolean isConnect = networkService.connectPeer(chainId, consensusNetNodeId);
            if (!isConnect) {
                chain.getLogger().warn("connect fail .nodeId = {}", consensusNet.getNodeId());
            } else {
                chain.getLogger().debug("connect {} success", consensusNetNodeId);
                List<String> ips = new ArrayList<>();
                ips.add(consensusNet.getNodeId().split(":")[0]);
                networkService.addIps(chainId, NetworkCmdConstant.NW_GROUP_FLAG, ips);
                chainManager.consensusNodeLink(chain, consensusNet.getNodeId());
            }
            //更新共识连接组
            consensusNetService.updateConsensusNode(chainId, consensusNet, isConnect);
            //需要向对方发送身份信息
            if (sendIdentityMsg) {
                chain.getLogger().debug("bengin sendIdentityMessage {} success", consensusNetNodeId);
                networkService.sendIdentityMessage(chainId, consensusNetNodeId, consensusNet.getPubKey());
            }
        }
        if (message.isBroadcast()) {
//        广播转发消息
            chain.getLogger().debug("bengin broadCastIdentityMsg exclude={} success", nodeId);
            networkService.broadCastIdentityMsg(chainId, getCmd(), msgStr, nodeId);
        }
        chain.getLogger().debug("=====================consensusIdentityProcessor deal end");
    }
}
