package io.nuls.pocbft.utils.validator;

import io.nuls.base.data.CoinData;
import io.nuls.base.data.Transaction;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.pocbft.constant.ConsensusErrorCode;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.tx.txdata.Deposit;
import io.nuls.pocbft.utils.TxUtil;
import io.nuls.pocbft.utils.validator.base.BaseValidator;

import java.io.IOException;
import java.math.BigInteger;

import static io.nuls.pocbft.utils.TxUtil.getSuccess;

/**
 * 减少保证金交易验证器
 * @author  tag
 * */
@Component
public class DepositValidator extends BaseValidator {
    @Override
    public Result validate(Chain chain, Transaction tx) throws NulsException, IOException {
        if (null == tx || null == tx.getTxData() || tx.getCoinData() == null) {
            chain.getLogger().error("Deposit -- Transaction data error");
            return Result.getFailed(ConsensusErrorCode.TX_DATA_VALIDATION_ERROR);
        }
        Deposit deposit = new Deposit();
        deposit.parse(tx.getTxData(), 0);
        //验证委托金额是否是否小于最小委托金额
        BigInteger realAmount = TxUtil.getRealAmount(deposit.getDeposit(), deposit.getAssetChainId(), deposit.getAssetId(),tx.getTime());
        if(realAmount.compareTo(chain.getConfig().getEntrustMin()) < 0){
            chain.getLogger().error("Deposit -- Less than the minimum entrusted amount");
            return Result.getFailed(ConsensusErrorCode.DEPOSIT_NOT_ENOUGH);
        }

        CoinData coinData = new CoinData();
        coinData.parse(tx.getCoinData(), 0);
        Result rs = appendDepositCoinDataValid(chain, deposit.getDeposit(), coinData, deposit.getAddress(), deposit.getAssetChainId(), deposit.getAssetId());
        if(rs.isFailed()){
            return rs;
        }

        //验证手续费是否足够
        rs = validFee(chain, coinData, tx);
        if (rs.isFailed()) {
            return rs;
        }

        return getSuccess();
    }
}
