package io.nuls.poc.rpc.cmd;

import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.ECKey;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.model.ArraysTool;
import io.nuls.core.rpc.cmd.BaseCmd;
import io.nuls.core.rpc.model.*;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.pocnetwork.service.ConsensusNetService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试接口
 *
 * @author lanjinsheng
 * 2019/11/7
 */
@Component
public class TestCmd extends BaseCmd {
    @Autowired
    private ConsensusNetService service;

    /**
     * 初始化网络
     */
    @CmdAnnotation(cmd = "cs_initNet", version = 1.0, description = "测试初始化共识网络")
    @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @Parameter(parameterName = "selfPub", parameterType = "String", parameterDes = "自身公钥")
    @Parameter(parameterName = "selfPriv", parameterType = "String", parameterDes = "自身私钥")
    @Parameter(parameterName = "consensusPubKeys", parameterType = "String", parameterDes = "逗号分割")
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = Boolean.class, description = "接口执行成功与否")
    }))
    public Response initNet(Map<String, Object> params) {
        int chainId = Integer.valueOf(params.get("chainId").toString());
        String selfPub = params.get("selfPub").toString();
        String selfPriv = params.get("selfPriv").toString();
        String consensusPubKeys = params.get("consensusPubKeys").toString();
        ECKey ecKey = ECKey.fromPrivate(new BigInteger(1, HexUtil.decode(selfPriv)));
        List<byte[]> pubByteKeys = new ArrayList<>();
        String[] pubKeys = consensusPubKeys.split(",");
        for (String pubKey : pubKeys) {
            pubByteKeys.add(HexUtil.decode(pubKey));
        }
        boolean result = service.initConsensusNetwork(chainId, ecKey.getPubKey(), ecKey.getPrivKeyBytes(), pubByteKeys);
        Map<String, Object> rtMap = new HashMap<>();
        rtMap.put("value", result);
        return success(rtMap);
    }

    /**
     * 清除网络
     */
    @CmdAnnotation(cmd = "cs_cleanNet", version = 1.0, description = "测试清除共识网络")
    @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = Boolean.class, description = "接口执行成功与否")
    }))
    public Response cs_cleanNet(Map<String, Object> params) {
        int chainId = Integer.valueOf(params.get("chainId").toString());
        service.cleanConsensusNetwork(chainId);
        Map<String, Object> rtMap = new HashMap<>();
        rtMap.put("value", true);
        return success(rtMap);
    }

    /**
     * 清除网络
     */
    @CmdAnnotation(cmd = "cs_updateNet", version = 1.0, description = "测试清除共识网络")
    @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @Parameter(parameterName = "consensusPubKey", requestType = @TypeDescriptor(value = String.class), parameterDes = "自身公钥")
    @Parameter(parameterName = "updateType", requestType = @TypeDescriptor(value = Integer.class), parameterDes = "更新类型")
    @ResponseData(name = "返回值", description = "返回一个Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = Boolean.class, description = "接口执行成功与否")
    }))
    public Response updateConsensusNet(Map<String, Object> params) {
        int chainId = Integer.valueOf(params.get("chainId").toString());
        byte[] pub = HexUtil.decode(params.get("consensusPubKey").toString());
        boolean result = service.updateConsensusList(chainId, pub, Short.valueOf(params.get("updateType").toString()));
        Map<String, Object> rtMap = new HashMap<>();
        rtMap.put("value", result);
        return success(rtMap);
    }

    public static void main(String[] args) {
        String privKey = "5600ce4a5cfb6ceb484a5e3a5c2e2285a1dba4d294be5f95a11ada767659ff8f";
        String pub = "02632a4768a9c4b5bd5dc4f3de0361eaf09aff127fa6eb2cf9dbac403776efd8e3";
        System.out.println(ArraysTool.arrayEquals(ECKey.fromPrivate(new BigInteger(1, HexUtil.decode(privKey))).getPubKey(), HexUtil.decode(privKey)));
    }
}
