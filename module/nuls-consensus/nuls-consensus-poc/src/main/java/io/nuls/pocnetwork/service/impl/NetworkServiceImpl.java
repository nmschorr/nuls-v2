package io.nuls.pocnetwork.service.impl;

import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.rpc.info.Constants;
import io.nuls.core.rpc.model.ModuleE;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.core.rpc.netty.processor.ResponseMessageProcessor;
import io.nuls.core.rpc.util.NulsDateUtils;
import io.nuls.pocnetwork.constant.NetworkCmdConstant;
import io.nuls.pocnetwork.model.ConsensusNet;
import io.nuls.pocnetwork.model.message.ConsensusIdentitiesMsg;
import io.nuls.pocnetwork.service.ConsensusNetService;
import io.nuls.pocnetwork.service.NetworkService;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.utils.manager.ChainManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lanjinsheng
 * @date 2019/10/17
 * @description
 */
@Component
public class NetworkServiceImpl implements NetworkService {
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private ConsensusNetService consensusNetService;

    @Override
    public boolean connectPeer(int chainId, String nodeId) {
        Chain chain = chainManager.getChainMap().get(chainId);
        try {
            Map<String, Object> params = new HashMap<>(5);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chainId);
            params.put("nodeId", nodeId);
            Response response = ResponseMessageProcessor.requestAndResponse(ModuleE.NW.abbr, "nw_addDirectConnect", params);
            if (response.isSuccess()) {
                Map data = (Map) ((Map) response.getResponseData()).get("nw_addDirectConnect");
                return Boolean.valueOf(data.get("value").toString());
            }
        } catch (Exception e) {
            chain.getLogger().error("", e);
            return false;
        }
        return false;
    }

    private String getSelfNodeId(int chainId) {
        Chain chain = chainManager.getChainMap().get(chainId);
        try {
            Map<String, Object> params = new HashMap<>(2);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chainId);
            Response response = ResponseMessageProcessor.requestAndResponse(ModuleE.NW.abbr, NetworkCmdConstant.NW_GET_EXTRANET_IP, params);
            if (response.isSuccess()) {
                Map data = (Map) ((Map) response.getResponseData()).get( NetworkCmdConstant.NW_GET_EXTRANET_IP);
                return data.get("nodeId").toString();
            }
        } catch (Exception e) {
            chain.getLogger().error("", e);
            return null;
        }
        return null;
    }

    @Override
    public boolean sendIdentityMessage(int chainId, String peerNodeId, byte[] pubKey) {
        Chain chain = chainManager.getChainMap().get(chainId);
        try {
            Map<String, Object> params = new HashMap<>(5);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chainId);
            params.put("nodes", peerNodeId);
            ConsensusNet consensusNet = new ConsensusNet();
            consensusNet.setPubKey(consensusNetService.getSelfConsensusKeys(chainId).getPubKey());
            String selfNodeId = getSelfNodeId(chainId);
            if (null == selfNodeId) {
                return false;
            }
            consensusNet.setNodeId(selfNodeId);
            ConsensusIdentitiesMsg consensusIdentitiesMsg = new ConsensusIdentitiesMsg(consensusNet);
            consensusIdentitiesMsg.addEncryptNodes(pubKey);
            consensusIdentitiesMsg.setMessageTime(NulsDateUtils.getCurrentTimeSeconds());
            params.put("messageBody", HexUtil.encode(consensusIdentitiesMsg.serialize()));
            params.put("command", NetworkCmdConstant.POC_IDENTITY_MESSAGE);
            boolean result = ResponseMessageProcessor.requestAndResponse(ModuleE.NW.abbr, NetworkCmdConstant.NW_SEND_PEER, params).isSuccess();
            chain.getLogger().debug("broadcast: " + NetworkCmdConstant.POC_IDENTITY_MESSAGE + ", success:" + result);
            return result;
        } catch (Exception e) {
            chain.getLogger().error("", e);
            return false;
        }
    }

    @Override
    public boolean broadCastIdentityMsg(int chainId, String command, String msgStr, String excludeNodeId) {
        Chain chain = chainManager.getChainMap().get(chainId);
        try {
            Map<String, Object> params = new HashMap<>(5);
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put(Constants.CHAIN_ID, chainId);
            params.put("excludeNodes", excludeNodeId);
            params.put("messageBody", msgStr);
            params.put("command", command);
            boolean success = ResponseMessageProcessor.requestAndResponse(ModuleE.NW.abbr, NetworkCmdConstant.NW_BROADCAST, params).isSuccess();
            chain.getLogger().debug("broadcast: " + command + ", success:" + success);
            return success;
        } catch (Exception e) {
            chain.getLogger().error("", e);
            return false;
        }
    }
}
