package io.nuls.pocbft.utils;

import io.nuls.core.basic.Result;
import io.nuls.pocbft.constant.ConsensusErrorCode;

import java.math.BigInteger;

/**
 * 交易工具类
 * Transaction Tool Class
 *
 * @author tag
 * 2019/7/25
 */
public class TxUtil {
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
}
