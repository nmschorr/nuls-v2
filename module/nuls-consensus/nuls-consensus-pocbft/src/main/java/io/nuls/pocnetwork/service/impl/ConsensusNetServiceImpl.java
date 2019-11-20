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
package io.nuls.pocnetwork.service.impl;
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

import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.model.ArraysTool;
import io.nuls.core.rpc.info.Constants;
import io.nuls.core.rpc.model.ModuleE;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.core.rpc.netty.processor.ResponseMessageProcessor;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.LoggerUtil;
import io.nuls.pocbft.utils.manager.ChainManager;
import io.nuls.pocbft.utils.manager.ThreadManager;
import io.nuls.pocnetwork.constant.NetworkCmdConstant;
import io.nuls.pocnetwork.model.ConsensusKeys;
import io.nuls.pocnetwork.model.ConsensusNet;
import io.nuls.pocnetwork.model.ConsensusNetGroup;
import io.nuls.pocnetwork.model.message.ConsensusIdentitiesMsg;
import io.nuls.pocnetwork.service.ConsensusNetService;
import io.nuls.pocnetwork.service.NetworkService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lanjinsheng
 * @date 2019/10/17
 * @description
 */
@Component
public class ConsensusNetServiceImpl implements ConsensusNetService {
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private ThreadManager threadManager;
    @Autowired
    NetworkService networkService;
    static Map<Integer, ConsensusNetGroup> GROUPS_MAP = new ConcurrentHashMap<>();
    static Map<Integer, Boolean> NETTHREAD_MAP = new ConcurrentHashMap<>();
    static Map<Integer, ConsensusKeys> CONSENSUSKEYS_MAP = new ConcurrentHashMap<>();
    static final short ADD_CONSENSUS = 1;
    static final short DEL_CONSENSUS = 2;

    @Override
    public void printTestInfo() {
        for (Map.Entry<Integer, ConsensusNetGroup> entry : GROUPS_MAP.entrySet()) {
            Integer chainId = entry.getKey();
            LoggerUtil.commonLog.debug("GROUPS_MAP========chainId={}", chainId);
            ConsensusNetGroup consensusNetGroup = entry.getValue();
            for (Map.Entry<String, ConsensusNet> entry2 : consensusNetGroup.getGroup().entrySet()) {
                LoggerUtil.commonLog.debug("*********");
                LoggerUtil.commonLog.debug("========pubkey={}", entry2.getKey());
                LoggerUtil.commonLog.debug("========nodeId={}", entry2.getValue().getNodeId());
                LoggerUtil.commonLog.debug("========connected={}", entry2.getValue().isHadConnect());
            }
            LoggerUtil.commonLog.debug("==================================================");
        }
        for (Map.Entry<Integer, ConsensusKeys> entry : CONSENSUSKEYS_MAP.entrySet()) {
            Integer chainId = entry.getKey();
            LoggerUtil.commonLog.debug("CONSENSUSKEYS_MAP========chainId={}", chainId);
            ConsensusKeys consensusKeys = entry.getValue();
            LoggerUtil.commonLog.debug("========pubkey={}", HexUtil.encode(consensusKeys.getPubKey()));
            LoggerUtil.commonLog.debug("========privkey={}", HexUtil.encode(consensusKeys.getPrivKey()));
        }
    }

    @Override
    public boolean netStatusChange(Chain chain) {
        ConsensusNetGroup group = GROUPS_MAP.get(chain.getChainId());
        if (null != group) {
            return group.statusChange(67, chain);
        }
        return false;
    }

    @Override
    public boolean getNetStatus(Chain chain) {
        ConsensusNetGroup group = GROUPS_MAP.get(chain.getChainId());
        if (null != group) {
            return group.isAvailable();
        }
        return false;
    }

    /**
     * @param consensusPubKeyStr
     * @param updateType         1 增加  2 删除
     * @description 更新共识列表, 增加或者减少节点时候调用
     */
    @Override
    public boolean updateConsensusList(int chainId, String consensusPubKeyStr, short updateType) {
        ConsensusNetGroup group = GROUPS_MAP.get(chainId);
        byte[] consensusPubKey = HexUtil.decode(consensusPubKeyStr);
        if (ADD_CONSENSUS == updateType) {
            ConsensusNet consensusNet = new ConsensusNet(consensusPubKey, null);
            group.addConsensus(consensusNet);
        } else if (DEL_CONSENSUS == updateType) {
            String nodeId = group.removeConsensus(consensusPubKey);
            if (null != nodeId) {
                List<String> ips = new ArrayList<>();
                ips.add(nodeId.split(":")[0]);
                networkService.removeIps(chainId, ModuleE.CS.abbr, ips);
            }
        }
        return true;
    }

    /**
     * 启动或者自身跃迁为共识节点时候调用。
     *
     * @param chainId
     * @param selfPubKeyStr
     * @param selfPrivKeyStr
     * @param consensusPubKeyList
     * @return
     */
    @Override
    public boolean initConsensusNetwork(int chainId, String selfPubKeyStr, String selfPrivKeyStr, List<String> consensusPubKeyList) {
        Chain chain = chainManager.getChainMap().get(chainId);
        ConsensusNetGroup group = new ConsensusNetGroup(chainId);
        byte[] selfPubKey = HexUtil.decode(selfPubKeyStr);
        byte[] selfPrivKey = HexUtil.decode(selfPrivKeyStr);
        ConsensusKeys consensusKeys = new ConsensusKeys(selfPubKey, selfPrivKey);

        String nodeId = networkService.getSelfNodeId(chainId);
        chain.getLogger().debug("self nodeId:{}", nodeId);
//        nodeId = "112.0.52.247:8001";
        ConsensusNet selfConsensusNet = new ConsensusNet();
        selfConsensusNet.setPubKey(consensusKeys.getPubKey());
        selfConsensusNet.setNodeId(nodeId);
        ConsensusIdentitiesMsg consensusIdentitiesMsg = new ConsensusIdentitiesMsg(selfConsensusNet);
        consensusIdentitiesMsg.setBroadcast(true);
        for (String consensusPubKey : consensusPubKeyList) {
            byte[] pubKey = HexUtil.decode(consensusPubKey);
            if (!ArraysTool.arrayEquals(pubKey, selfPubKey)) {
                ConsensusNet consensusNet = new ConsensusNet(pubKey, null);
                group.addConsensus(consensusNet);
                try {
                    consensusIdentitiesMsg.addEncryptNodes(pubKey);
                } catch (Exception e) {
                    chain.getLogger().error(e);
                    return false;
                }
            }
        }
        GROUPS_MAP.put(chainId, group);
        CONSENSUSKEYS_MAP.put(chainId, consensusKeys);
        //广播身份消息
        try {
            networkService.broadCastIdentityMsg(chainId, NetworkCmdConstant.POC_IDENTITY_MESSAGE,
                    HexUtil.encode(consensusIdentitiesMsg.serialize()), null);
        } catch (IOException e) {
            chain.getLogger().error(e);
            return false;
        }
        if (null == NETTHREAD_MAP.get(chainId) || !NETTHREAD_MAP.get(chainId)) {
            NETTHREAD_MAP.put(chainId, true);
            threadManager.createChainThread(chain);
        }
        return true;
    }

    @Override
    public void cleanConsensusNetwork(int chainId) {
        GROUPS_MAP.remove(chainId);
        CONSENSUSKEYS_MAP.remove(chainId);
    }

    @Override
    public ConsensusKeys getSelfConsensusKeys(int chainId) {
        return CONSENSUSKEYS_MAP.get(chainId);
    }

    @Override
    public boolean isConsensusNode(int chainId, ConsensusNet consensusNet) {
        ConsensusNetGroup consensusNetGroup = GROUPS_MAP.get(chainId);
        if (null == consensusNetGroup) {
            return false;
        }
        if (null != consensusNetGroup.getGroup().get(HexUtil.encode(consensusNet.getPubKey()))) {
            return true;
        }
        return false;
    }

    @Override
    public boolean updateConsensusNode(int chainId, ConsensusNet consensusNet, boolean isConnect) {
        ConsensusNetGroup consensusNetGroup = GROUPS_MAP.get(chainId);
        ConsensusNet consensusNet1 = consensusNetGroup.getGroup().get(HexUtil.encode(consensusNet.getPubKey()));
        boolean orgConn = (null != consensusNet1.getNodeId() && consensusNet1.getNodeId().equals(consensusNet.getNodeId())) && consensusNet1.isHadConnect();
        consensusNet1.setNodeId(consensusNet.getNodeId());
        consensusNet1.setHadConnect(isConnect);
        return orgConn;
    }


    /**
     * 广播共识消息返回已发送连接列表
     *
     * @param chainId
     * @param cmd
     * @param messageBodyHex
     * @return
     */
    @Override
    public List<String> broadCastConsensusNet(int chainId, String cmd, String messageBodyHex) {
        Chain chain = chainManager.getChainMap().get(chainId);
        try {
            Map<String, Object> params = new HashMap<>(5);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chainId);
            params.put("ips", GROUPS_MAP.get(chainId).getConsensusNetIps());
            params.put("messageBody", messageBodyHex);
            params.put("command", cmd);
            Response response = ResponseMessageProcessor.requestAndResponse(ModuleE.NW.abbr, NetworkCmdConstant.NW_BROADCAST_CONSENSUS_NET, params);
            chain.getLogger().debug("broadcast: " + cmd + ", success:" + response.isSuccess());
            if (response.isSuccess()) {
                List<String> ips = (List) ((Map) ((Map) response.getResponseData()).get(NetworkCmdConstant.NW_BROADCAST_CONSENSUS_NET)).get("list");
                return ips;
            }
        } catch (Exception e) {
            chain.getLogger().error("", e);
        }
        return null;
    }


}
