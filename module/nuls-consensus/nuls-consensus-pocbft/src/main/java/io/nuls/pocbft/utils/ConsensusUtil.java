package io.nuls.pocbft.utils;

import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.pocbft.constant.ConsensusErrorCode;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.rpc.call.CallMethodUtils;
import io.nuls.pocnetwork.service.ConsensusNetService;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Set;

import static io.nuls.pocbft.constant.ParameterConstant.PARAM_PRI_KEY;
import static io.nuls.pocbft.constant.ParameterConstant.PARAM_PUB_KEY;

/**
 * 交易工具类
 * Transaction Tool Class
 *
 * @author tag
 * 2019/7/25
 */
@Component
public class ConsensusUtil {
    @Autowired
    private static ConsensusNetService netService;
    /**
     * 从喂价系统获取对应的数量的NVT
     * @param amount            数量
     * @param assetChainId      资产链ID
     * @param assetId           资产ID
     * @param txTime            交易时间
     * @return                  对应数量的NVT
     * */
    public static BigInteger getRealAmount(BigInteger amount, int assetChainId, int assetId, long txTime){
        //todo
        return amount;
    }

    public static Result getSuccess() {
        return Result.getSuccess(ConsensusErrorCode.SUCCESS);
    }

    /**
     * 初始化共识网络
     * @param chain            链信息
     * @param packAddress      出块地址
     * @param packAddressList  共识节点出块地址列表
     * */
    public static void initConsensusNet(Chain chain, String packAddress, Set<String> packAddressList){
        try {
            HashMap callResult = CallMethodUtils.accountValid(chain.getChainId(), packAddress, chain.getConfig().getPassword());
            String priKey = (String) callResult.get(PARAM_PRI_KEY);
            String pubKey = (String) callResult.get(PARAM_PUB_KEY);
            netService.createConsensusNetwork(chain.getChainId(), HexUtil.decode(pubKey), HexUtil.decode(priKey), chain.getSeedNodePubKeyList(), packAddressList);
        }catch (Exception e){
            chain.getLogger().error(e);
        }
    }

    /**
     * 节点有共识节点变为非共识节点
     * @param chain 链信息
     * */
    public static void disConnectConsenusNet(Chain chain){
        netService.cleanConsensusNetwork(chain.getChainId());
    }
}
